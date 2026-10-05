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
  if (token === env.ADMIN_TOKEN) return { role: "MASTER", id: null, name: "VPlayo MASTER", credits: 0 };

  const rows = await sb(
    env,
    `viraplay_partners?access_token=eq.${encodeURIComponent(token)}&status=eq.ACTIVE&select=id,name,credits,login_code,status&limit=1`
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

async function rpc(env, fn, body) {
  return sb(env, `rpc/${fn}`, { method: "POST", body: JSON.stringify(body) });
}

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") return json({ ok: true });
    const url = new URL(req.url);

    try {
      if (req.method === "GET" && url.pathname === "/") {
        return json({ name: "VPlayo API", ok: true, version: "3.3.6" });
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

      // Asaas: endpoint reservado. So passa a operar quando as credenciais forem configuradas.
      if (req.method === "POST" && url.pathname === "/api/payments/asaas/webhook") {
        if (!env.ASAAS_WEBHOOK_TOKEN) return json({ error: "asaas_not_configured" }, 503);
        const sent = req.headers.get("asaas-access-token") || "";
        if (sent !== env.ASAAS_WEBHOOK_TOKEN) return json({ error: "unauthorized" }, 401);
        const event = await req.json();
        // A confirmacao automatica de creditos sera ligada quando a conta Asaas
        // estiver criada e o customer_id de cada parceiro puder ser registrado.
        return json({ ok: true, received: event?.event || null });
      }

      if (!url.pathname.startsWith("/api/admin/")) return json({ error: "not_found" }, 404);
      const who = await actor(req, env);
      if (!who) return json({ error: "unauthorized" }, 401);
      const annualCost = Math.max(1, Number(env.ANNUAL_LICENSE_CREDITS || 15));

      if (req.method === "GET" && url.pathname === "/api/admin/profile") {
        return json({
          role: who.role,
          name: who.name,
          credits: Number(who.credits || 0),
          annual_license_credits: annualCost
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

        if (d.partner_id) return json({ error: "device_already_claimed" }, 409);
        try {
          const rows = await rpc(env, "viraplay_partner_activate", {
            p_partner_id: who.id,
            p_device_id: d.device_id,
            p_label: b.label || "Cliente",
            p_playlist_url: b.playlist_url,
            p_credit_cost: annualCost,
            p_license_months: Math.max(1, Number(b.license_months || 12))
          });
          return json({ ok: true, device: rows?.[0] || null });
        } catch (e) {
          const msg = String(e?.message || e);
          if (msg.includes("insufficient_credits")) return json({ error: "insufficient_credits" }, 402);
          if (msg.includes("already_claimed")) return json({ error: "device_already_claimed" }, 409);
          throw e;
        }
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
        const partners = await sb(env, "viraplay_partners?select=id,name,login_code,access_token,status,credits&order=created_at.desc") || [];
        const devices = await sb(env, "viraplay_devices?partner_id=not.is.null&select=partner_id") || [];
        const counts = {};
        for (const d of devices) counts[d.partner_id] = (counts[d.partner_id] || 0) + 1;
        return json(partners.map(p => ({ ...p, clients: counts[p.id] || 0 })));
      }

      if (req.method === "POST" && url.pathname === "/api/admin/partners/create") {
        if (who.role !== "MASTER") return json({ error: "forbidden" }, 403);
        const b = await req.json();
        const name = String(b.name || "").trim();
        if (!name) return json({ error: "missing_name" }, 400);
        const loginCode = safeCode(`P${Math.random().toString(36).slice(2, 8)}`);
        const accessToken = randomToken("VPP");
        const rows = await sb(env, "viraplay_partners", {
          method: "POST",
          body: JSON.stringify({ name, login_code: loginCode, access_token: accessToken, status: "ACTIVE", credits: 0 })
        });
        return json({ ok: true, partner: { ...(rows?.[0] || {}), clients: 0 } });
      }

      if (req.method === "POST" && url.pathname === "/api/admin/partners/credits") {
        if (who.role !== "MASTER") return json({ error: "forbidden" }, 403);
        const b = await req.json();
        const amount = Number(b.amount || 0);
        if (!b.partner_id || !Number.isInteger(amount) || amount === 0) return json({ error: "invalid_amount" }, 400);
        const balance = await rpc(env, "viraplay_add_credits", {
          p_partner_id: b.partner_id,
          p_amount: amount,
          p_kind: "MASTER_ADJUSTMENT",
          p_note: b.note || "Ajuste manual MASTER",
          p_reference: null
        });
        return json({ ok: true, balance });
      }

      if (req.method === "GET" && url.pathname === "/api/admin/credits/history") {
        if (who.role === "MASTER") return json([]);
        const rows = await sb(env,
          `viraplay_credit_ledger?partner_id=eq.${encodeURIComponent(who.id)}&select=amount,kind,note,created_at&order=created_at.desc&limit=100`
        );
        return json(rows || []);
      }

      if (req.method === "POST" && url.pathname === "/api/admin/credits/order") {
        if (who.role !== "PARTNER") return json({ error: "forbidden" }, 403);
        if (!env.ASAAS_API_KEY) return json({ error: "asaas_not_configured", ready: false }, 503);
        return json({ error: "asaas_customer_setup_required", ready: false }, 503);
      }

      return json({ error: "not_found" }, 404);
    } catch (e) {
      return json({ error: "server_error", detail: String(e?.message || e) }, 500);
    }
  }
};
