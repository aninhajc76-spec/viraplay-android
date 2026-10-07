const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": "*",
    "access-control-allow-headers": "authorization, content-type, asaas-access-token"
  }
});

async function sb(env, path, options = {}) {
  const res = await fetch(`${env.SUPABASE_URL}/rest/v1/${path}`, {
    method: options.method || "GET",
    headers: {
      apikey: env.SUPABASE_SERVICE_ROLE,
      Authorization: `Bearer ${env.SUPABASE_SERVICE_ROLE}`,
      "Content-Type": "application/json",
      Prefer: options.prefer || "return=representation",
      ...(options.headers || {})
    },
    body: options.body
  });
  const text = await res.text();
  if (!res.ok) throw new Error(text || `Supabase HTTP ${res.status}`);
  return text ? JSON.parse(text) : null;
}

async function actor(req, env) {
  const raw = req.headers.get("authorization") || "";
  const token = raw.startsWith("Bearer ") ? raw.slice(7).trim() : "";
  if (!token) return null;
  if (token === env.ADMIN_TOKEN) return { role: "MASTER", id: null, name: "VPlayo MASTER" };

  const rows = await sb(
    env,
    `viraplay_partners?access_token=eq.${encodeURIComponent(token)}&status=eq.ACTIVE&select=id,name,login_code,status,dns_primary,dns_secondary,active_window_days&limit=1`
  );
  if (!rows?.length) return null;
  return { role: "PARTNER", ...rows[0] };
}

function safeCode(value) {
  return String(value || "").trim().toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 10);
}

function randomToken(prefix = "VP") {
  return `${prefix}_${crypto.randomUUID().replaceAll("-", "")}`;
}

function normalizeDns(value) {
  const raw = String(value || "").trim();
  if (!raw) return null;
  const withScheme = /^https?:\/\//i.test(raw) ? raw : `http://${raw}`;
  const parsed = new URL(withScheme);
  if (!["http:", "https:"].includes(parsed.protocol)) throw new Error("invalid_dns_protocol");
  return `${parsed.protocol}//${parsed.host}${parsed.pathname === "/" ? "" : parsed.pathname.replace(/\/$/, "")}`;
}

function providerCode() {
  return `VP${String(Math.floor(100000 + Math.random() * 900000))}`;
}

async function rpc(env, fn, body) {
  return sb(env, `rpc/${fn}`, { method: "POST", body: JSON.stringify(body) });
}

function activeStats(rows, windowDays = 10) {
  const now = Date.now();
  const oneDay = 24 * 60 * 60 * 1000;
  const unique = new Map();

  for (const row of rows || []) {
    const key = String(row.device_id || "");
    if (!key) continue;
    const current = unique.get(key);
    const seen = Date.parse(row.last_seen_at || row.created_at || 0) || 0;
    if (!current || seen > current.seen) unique.set(key, { ...row, seen });
  }

  const values = [...unique.values()];
  const countSince = days => values.filter(x => x.seen >= now - days * oneDay).length;

  return {
    total_devices: values.length,
    active_today: countSince(1),
    active_7d: countSince(7),
    active_window: countSince(Math.max(1, Number(windowDays || 10)))
  };
}

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") return json({ ok: true });
    const url = new URL(req.url);

    try {
      if (req.method === "GET" && url.pathname === "/") {
        return json({ name: "VPlayo API", ok: true, version: "3.3.16" });
      }

      if (req.method === "POST" && url.pathname === "/api/register") {
        const b = await req.json();
        if (!b.device_id || !b.device_secret || !b.pairing_code) return json({ error: "missing_fields" }, 400);

        const rows = await sb(env, "viraplay_devices?on_conflict=device_id", {
          method: "POST",
          prefer: "resolution=merge-duplicates,return=representation",
          body: JSON.stringify({
            device_id: b.device_id,
            device_secret: b.device_secret,
            pairing_code: safeCode(b.pairing_code),
            platform: b.platform || "ANDROID",
            updated_at: new Date().toISOString()
          })
        });
        return json({ ok: true, device: rows?.[0] || null });
      }

      if (req.method === "GET" && url.pathname === "/api/config") {
        const deviceId = url.searchParams.get("device_id") || "";
        const secret = url.searchParams.get("secret") || "";
        const rows = await sb(env,
          `viraplay_devices?device_id=eq.${encodeURIComponent(deviceId)}&device_secret=eq.${encodeURIComponent(secret)}&select=enabled,playlist_url,playlist_name,partner_id,license_expires_at`
        );
        if (!rows?.length) return json({ enabled: false, playlist_url: null }, 404);

        const d = rows[0];
        const partnerLicensed = !d.partner_id || !d.license_expires_at || new Date(d.license_expires_at).getTime() > Date.now();
        return json({
          enabled: Boolean(d.enabled && partnerLicensed),
          playlist_url: d.enabled && partnerLicensed ? d.playlist_url : null,
          playlist_name: d.playlist_name || null
        });
      }


if (req.method === "GET" && url.pathname === "/api/provider/resolve") {
  const code = safeCode(url.searchParams.get("code"));
  if (!code) return json({ error: "missing_provider_code" }, 400);
  const rows = await sb(
    env,
    `viraplay_partners?login_code=eq.${encodeURIComponent(code)}&status=eq.ACTIVE&select=id,name,login_code,dns_primary,dns_secondary&limit=1`
  );
  if (!rows?.length) return json({ error: "provider_not_found" }, 404);
  const provider = rows[0];
  if (!provider.dns_primary) return json({ error: "provider_not_configured" }, 409);
  return json({
    provider_code: provider.login_code,
    name: provider.name,
    dns_primary: provider.dns_primary,
    dns_secondary: provider.dns_secondary || null
  });
}

if (req.method === "POST" && url.pathname === "/api/provider/session") {
  const b = await req.json();
  const code = safeCode(b.provider_code);
  const deviceId = String(b.device_id || "").trim();
  if (!code || !deviceId) return json({ error: "missing_fields" }, 400);
  const rows = await sb(
    env,
    `viraplay_partners?login_code=eq.${encodeURIComponent(code)}&status=eq.ACTIVE&select=id&limit=1`
  );
  if (!rows?.length) return json({ error: "provider_not_found" }, 404);
  await sb(env, "viraplay_provider_sessions?on_conflict=provider_id,device_id", {
    method: "POST",
    prefer: "resolution=merge-duplicates,return=minimal",
    body: JSON.stringify({
      provider_id: rows[0].id,
      device_id: deviceId,
      platform: String(b.platform || "ANDROID").slice(0, 40),
      app_version: String(b.app_version || "").slice(0, 30) || null,
      last_seen_at: new Date().toISOString()
    })
  });
  return json({ ok: true });
}

      if (!url.pathname.startsWith("/api/admin/")) return json({ error: "not_found" }, 404);
      const who = await actor(req, env);
      if (!who) return json({ error: "unauthorized" }, 401);

      if (req.method === "GET" && url.pathname === "/api/admin/profile") {
        if (who.role === "MASTER") {
          return json({
            role: who.role,
            name: who.name,
            provider_code: null,
            dns_primary: null,
            dns_secondary: null,
            total_devices: 0,
            active_today: 0,
            active_7d: 0,
            active_window: 0,
            active_window_days: 10
          });
        }

        const windowDays = Math.min(60, Math.max(1, Number(who.active_window_days || 10)));
        const sessions = await sb(
          env,
          `viraplay_provider_sessions?provider_id=eq.${encodeURIComponent(who.id)}&select=device_id,platform,app_version,created_at,last_seen_at`
        ) || [];
        const stats = activeStats(sessions, windowDays);

        return json({
          role: who.role,
          name: who.name,
          provider_code: who.login_code || null,
          dns_primary: who.dns_primary || null,
          dns_secondary: who.dns_secondary || null,
          active_window_days: windowDays,
          ...stats
        });
      }

      if (req.method === "GET" && url.pathname === "/api/admin/devices") {
        const select = "device_id,pairing_code,label,platform,enabled,playlist_url,partner_id,license_expires_at";
        const filter = who.role === "MASTER"
          ? `partner_id=is.null&select=${select}&order=created_at.desc`
          : `partner_id=eq.${encodeURIComponent(who.id)}&select=${select}&order=created_at.desc`;
        const rows = await sb(env, `viraplay_devices?${filter}`);
        return json(rows || []);
      }

      if (req.method === "GET" && url.pathname === "/api/admin/lookup") {
        const code = safeCode(url.searchParams.get("pairing_code"));
        if (!code) return json({ error: "missing_code" }, 400);
        const rows = await sb(env,
          `viraplay_devices?pairing_code=eq.${encodeURIComponent(code)}&select=device_id,pairing_code,label,platform,enabled,playlist_url,partner_id&limit=1`
        );
        if (!rows?.length) return json({ error: "not_found" }, 404);
        const d = rows[0];
        if (who.role === "PARTNER" && d.partner_id && d.partner_id !== who.id) return json({ error: "owned_by_other_partner" }, 409);
        if (who.role === "PARTNER" && d.partner_id === who.id) return json({ error: "already_owned" }, 409);
        return json({ ...d, playlist_url: d.partner_id ? d.playlist_url : null });
      }

      if (req.method === "POST" && url.pathname === "/api/admin/claim") {
        const b = await req.json();
        const code = safeCode(b.pairing_code);
        if (!code || !b.playlist_url) return json({ error: "missing_fields" }, 400);
        const found = await sb(env,
          `viraplay_devices?pairing_code=eq.${encodeURIComponent(code)}&select=device_id,partner_id&limit=1`
        );
        if (!found?.length) return json({ error: "not_found" }, 404);
        const d = found[0];

        if (who.role === "MASTER") {
          if (d.partner_id) return json({ error: "owned_by_partner" }, 409);
          const rows = await sb(env, `viraplay_devices?device_id=eq.${encodeURIComponent(d.device_id)}`, {
            method: "PATCH",
            body: JSON.stringify({
              partner_id: null,
              label: b.label || null,
              playlist_url: b.playlist_url,
              enabled: true,
              claimed_at: new Date().toISOString(),
              updated_at: new Date().toISOString()
            })
          });
          return json({ ok: true, device: rows?.[0] || null });
        }

        // No modelo atual, provedores usam código do provedor + usuário/senha.
        // A ativação individual por código permanece exclusiva do MASTER.
        return json({ error: "provider_direct_login_only" }, 403);
      }

      if (req.method === "POST" && url.pathname === "/api/admin/update") {
        const b = await req.json();
        const deviceId = String(b.device_id || "");
        const owned = who.role === "MASTER"
          ? `device_id=eq.${encodeURIComponent(deviceId)}&partner_id=is.null`
          : `device_id=eq.${encodeURIComponent(deviceId)}&partner_id=eq.${encodeURIComponent(who.id)}`;
        const patch = {
          enabled: b.enabled !== false,
          playlist_url: b.playlist_url || null,
          updated_at: new Date().toISOString()
        };
        if (b.label !== undefined) patch.label = b.label;
        const rows = await sb(env, `viraplay_devices?${owned}`, { method: "PATCH", body: JSON.stringify(patch) });
        if (!rows?.length) return json({ error: "not_found_or_forbidden" }, 404);
        return json({ ok: true, device: rows[0] });
      }

      if (req.method === "GET" && url.pathname === "/api/admin/partners") {
        if (who.role !== "MASTER") return json({ error: "forbidden" }, 403);

        const partners = await sb(
          env,
          "viraplay_partners?select=id,name,login_code,access_token,status,dns_primary,dns_secondary,active_window_days&order=created_at.desc"
        ) || [];
        const devices = await sb(
          env,
          "viraplay_devices?partner_id=not.is.null&select=partner_id,device_id"
        ) || [];
        const sessions = await sb(
          env,
          "viraplay_provider_sessions?select=provider_id,device_id,platform,app_version,created_at,last_seen_at"
        ) || [];

        const legacySets = {};
        const sessionRows = {};

        for (const d of devices) {
          if (!legacySets[d.partner_id]) legacySets[d.partner_id] = new Set();
          legacySets[d.partner_id].add(d.device_id);
        }
        for (const s of sessions) {
          if (!sessionRows[s.provider_id]) sessionRows[s.provider_id] = [];
          sessionRows[s.provider_id].push(s);
        }

        return json(partners.map(p => {
          const windowDays = Math.min(60, Math.max(1, Number(p.active_window_days || 10)));
          const stats = activeStats(sessionRows[p.id] || [], windowDays);
          const all = new Set([
            ...(legacySets[p.id] || []),
            ...((sessionRows[p.id] || []).map(x => x.device_id))
          ]);

          return {
            ...p,
            active_window_days: windowDays,
            clients: all.size,
            direct_clients: stats.total_devices,
            ...stats
          };
        }));
      }

      if (req.method === "POST" && url.pathname === "/api/admin/partners/create") {
        if (who.role !== "MASTER") return json({ error: "forbidden" }, 403);
        const b = await req.json();
        const name = String(b.name || "").trim();
        if (!name) return json({ error: "missing_name" }, 400);
        const loginCode = providerCode();
        const accessToken = randomToken("VPP");
        const rows = await sb(env, "viraplay_partners", {
          method: "POST",
          body: JSON.stringify({ name, login_code: loginCode, access_token: accessToken, status: "ACTIVE", active_window_days: 10 })
        });
        return json({ ok: true, partner: { ...(rows?.[0] || {}), clients: 0 } });
      }

      if (req.method === "POST" && url.pathname === "/api/admin/partners/update") {
        if (who.role !== "MASTER") return json({ error: "forbidden" }, 403);
        const b = await req.json();
        const partnerId = String(b.partner_id || "").trim();
        const name = String(b.name || "").trim();
        const status = String(b.status || "ACTIVE").toUpperCase();
        const activeWindowDays = Math.min(60, Math.max(1, Number(b.active_window_days || 10)));
        if (!partnerId || !name) return json({ error: "missing_fields" }, 400);
        if (!["ACTIVE", "BLOCKED"].includes(status)) return json({ error: "invalid_status" }, 400);

        const rows = await sb(
          env,
          `viraplay_partners?id=eq.${encodeURIComponent(partnerId)}`,
          {
            method: "PATCH",
            body: JSON.stringify({
              name,
              status,
              active_window_days: activeWindowDays,
              updated_at: new Date().toISOString()
            })
          }
        );
        if (!rows?.length) return json({ error: "partner_not_found" }, 404);
        return json({ ok: true, partner: rows[0] });
      }

      if (req.method === "POST" && url.pathname === "/api/admin/partners/delete") {
        if (who.role !== "MASTER") return json({ error: "forbidden" }, 403);
        const b = await req.json();
        const partnerId = String(b.partner_id || "").trim();
        if (!partnerId) return json({ error: "missing_partner_id" }, 400);

        // Encerra primeiro os clientes do parceiro e devolve-os ao MASTER bloqueados.
        await sb(
          env,
          `viraplay_devices?partner_id=eq.${encodeURIComponent(partnerId)}`,
          {
            method: "PATCH",
            body: JSON.stringify({
              partner_id: null,
              enabled: false,
              playlist_url: null,
              license_activated_at: null,
              license_expires_at: null,
              license_credits_cost: 0,
              updated_at: new Date().toISOString()
            })
          }
        );

        const removed = await sb(
          env,
          `viraplay_partners?id=eq.${encodeURIComponent(partnerId)}`,
          { method: "DELETE" }
        );
        if (!removed?.length) return json({ error: "partner_not_found" }, 404);
        return json({ ok: true, deleted: true });
      }


if (req.method === "GET" && url.pathname === "/api/admin/provider/sessions") {
  let targetId = who.id;
  let windowDays = Number(who.active_window_days || 10);

  if (who.role === "MASTER") {
    targetId = String(url.searchParams.get("partner_id") || "").trim();
    if (!targetId) return json({ error: "missing_partner_id" }, 400);

    const providerRows = await sb(
      env,
      `viraplay_partners?id=eq.${encodeURIComponent(targetId)}&select=active_window_days&limit=1`
    ) || [];
    if (!providerRows.length) return json({ error: "provider_not_found" }, 404);
    windowDays = Number(providerRows[0].active_window_days || 10);
  }

  const rows = await sb(
    env,
    `viraplay_provider_sessions?provider_id=eq.${encodeURIComponent(targetId)}&select=device_id,platform,app_version,created_at,last_seen_at&order=last_seen_at.desc&limit=1000`
  ) || [];

  return json({
    active_window_days: Math.min(60, Math.max(1, windowDays)),
    stats: activeStats(rows, windowDays),
    sessions: rows
  });
}

if (req.method === "POST" && url.pathname === "/api/admin/provider/config") {
  const b = await req.json();
  let targetId = who.id;
  if (who.role === "MASTER") {
    targetId = String(b.partner_id || "").trim();
    if (!targetId) return json({ error: "missing_partner_id" }, 400);
  }

  let primary;
  let secondary;
  try {
    primary = normalizeDns(b.dns_primary);
    secondary = normalizeDns(b.dns_secondary);
  } catch (_) {
    return json({ error: "invalid_dns" }, 400);
  }
  if (!primary) return json({ error: "missing_dns_primary" }, 400);

  const rows = await sb(
    env,
    `viraplay_partners?id=eq.${encodeURIComponent(targetId)}`,
    {
      method: "PATCH",
      body: JSON.stringify({
        dns_primary: primary,
        dns_secondary: secondary,
        updated_at: new Date().toISOString()
      })
    }
  );
  if (!rows?.length) return json({ error: "provider_not_found" }, 404);
  return json({ ok: true, provider: rows[0] });
}

      return json({ error: "not_found" }, 404);
    } catch (e) {
      return json({ error: "server_error", detail: String(e?.message || e) }, 500);
    }
  }
};
