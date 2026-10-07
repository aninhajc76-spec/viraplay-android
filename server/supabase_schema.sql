-- VPlayo 3.3.8 - base MASTER + PARCEIROS + CREDITOS
-- Script aditivo: preserva os clientes atuais. Execute no SQL Editor do Supabase.

create extension if not exists pgcrypto;

create table if not exists public.viraplay_partners (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  login_code text unique not null,
  access_token text unique not null,
  status text not null default 'ACTIVE',
  credits integer not null default 0 check (credits >= 0),
  asaas_customer_id text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.viraplay_credit_ledger (
  id uuid primary key default gen_random_uuid(),
  partner_id uuid not null references public.viraplay_partners(id) on delete cascade,
  amount integer not null,
  kind text not null,
  reference text,
  note text,
  created_at timestamptz not null default now()
);

create table if not exists public.viraplay_payments (
  id uuid primary key default gen_random_uuid(),
  partner_id uuid not null references public.viraplay_partners(id) on delete cascade,
  provider text not null default 'ASAAS',
  provider_payment_id text unique,
  amount_cents integer not null,
  credits integer not null,
  status text not null default 'PENDING',
  pix_payload text,
  pix_qr_image text,
  created_at timestamptz not null default now(),
  paid_at timestamptz
);

-- A tabela de aparelhos já existe nas instalações atuais.
alter table public.viraplay_devices
  add column if not exists partner_id uuid references public.viraplay_partners(id) on delete set null,
  add column if not exists license_activated_at timestamptz,
  add column if not exists license_expires_at timestamptz,
  add column if not exists license_credits_cost integer not null default 0;

create index if not exists viraplay_devices_partner_idx on public.viraplay_devices(partner_id);
create index if not exists viraplay_devices_license_idx on public.viraplay_devices(license_expires_at);
create index if not exists viraplay_partners_token_idx on public.viraplay_partners(access_token);
create index if not exists viraplay_credit_ledger_partner_idx on public.viraplay_credit_ledger(partner_id, created_at desc);

alter table public.viraplay_partners enable row level security;
alter table public.viraplay_credit_ledger enable row level security;
alter table public.viraplay_payments enable row level security;
-- Sem policy publica. O Worker usa SERVICE_ROLE.

create or replace function public.viraplay_add_credits(
  p_partner_id uuid,
  p_amount integer,
  p_kind text,
  p_note text default null,
  p_reference text default null
) returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_balance integer;
begin
  if p_amount = 0 then
    select credits into v_balance from public.viraplay_partners where id = p_partner_id;
    return coalesce(v_balance, 0);
  end if;

  update public.viraplay_partners
     set credits = credits + p_amount,
         updated_at = now()
   where id = p_partner_id
     and credits + p_amount >= 0
  returning credits into v_balance;

  if v_balance is null then
    raise exception 'insufficient_credits';
  end if;

  insert into public.viraplay_credit_ledger(partner_id, amount, kind, reference, note)
  values (p_partner_id, p_amount, p_kind, p_reference, p_note);

  return v_balance;
end;
$$;

create or replace function public.viraplay_partner_activate(
  p_partner_id uuid,
  p_device_id text,
  p_label text,
  p_playlist_url text,
  p_credit_cost integer,
  p_license_months integer default 12
) returns setof public.viraplay_devices
language plpgsql
security definer
set search_path = public
as $$
declare
  v_owner uuid;
  v_existing_expiry timestamptz;
begin
  select partner_id, license_expires_at
    into v_owner, v_existing_expiry
    from public.viraplay_devices
   where device_id = p_device_id
   for update;

  if not found then
    raise exception 'device_not_found';
  end if;

  if v_owner is not null then
    raise exception 'device_already_claimed';
  end if;

  perform public.viraplay_add_credits(
    p_partner_id,
    -greatest(p_credit_cost, 0),
    'ACTIVATION',
    'Licenca anual VPlayo',
    p_device_id
  );

  update public.viraplay_devices
     set partner_id = p_partner_id,
         label = p_label,
         playlist_url = p_playlist_url,
         enabled = true,
         claimed_at = now(),
         license_activated_at = now(),
         license_expires_at = now() + make_interval(months => greatest(p_license_months, 1)),
         license_credits_cost = greatest(p_credit_cost, 0),
         updated_at = now()
   where device_id = p_device_id;

  return query
  select * from public.viraplay_devices where device_id = p_device_id;
end;
$$;


-- VPlayo 3.3.15 - PROVEDORES / DNS / LOGIN DIRETO
alter table public.viraplay_partners
  add column if not exists dns_primary text,
  add column if not exists dns_secondary text;

create table if not exists public.viraplay_provider_sessions (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.viraplay_partners(id) on delete cascade,
  device_id text not null,
  platform text,
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  unique(provider_id, device_id)
);

create index if not exists viraplay_provider_sessions_provider_idx
  on public.viraplay_provider_sessions(provider_id, last_seen_at desc);

alter table public.viraplay_provider_sessions enable row level security;
-- Sem policy publica. O Worker usa SERVICE_ROLE.


-- VPlayo 3.3.16 - MODELO POR APARELHO ATIVO
-- Execute UMA VEZ no SQL Editor do Supabase.
-- Migração aditiva: não apaga clientes, provedores nem configurações existentes.

alter table public.viraplay_partners
  add column if not exists active_window_days integer not null default 10;

alter table public.viraplay_provider_sessions
  add column if not exists app_version text;

-- Mantém a regra de atividade entre 1 e 60 dias.
do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'viraplay_partners_active_window_days_check'
  ) then
    alter table public.viraplay_partners
      add constraint viraplay_partners_active_window_days_check
      check (active_window_days between 1 and 60);
  end if;
end $$;

create index if not exists viraplay_provider_sessions_last_seen_idx
  on public.viraplay_provider_sessions(last_seen_at desc);

-- As tabelas/colunas antigas de créditos são preservadas somente por compatibilidade
-- com versões anteriores. A versão 3.3.16 não usa créditos no modelo comercial.
