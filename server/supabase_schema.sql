-- ViraPlay MVP v0.1
-- Execute no SQL Editor do Supabase.

create extension if not exists pgcrypto;

create table if not exists public.viraplay_devices (
  id uuid primary key default gen_random_uuid(),
  device_id text unique not null,
  device_secret text not null,
  pairing_code text unique not null,
  platform text,
  label text,
  playlist_url text,
  playlist_name text,
  enabled boolean not null default true,
  claimed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.viraplay_devices enable row level security;

-- Sem policy pública: somente o Worker com SERVICE_ROLE acessa esta tabela.
create index if not exists viraplay_devices_pairing_code_idx on public.viraplay_devices(pairing_code);
create index if not exists viraplay_devices_device_id_idx on public.viraplay_devices(device_id);
