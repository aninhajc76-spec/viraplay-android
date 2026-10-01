const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": "*",
    "access-control-allow-headers": "authorization, content-type"
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

  if (!res.ok) {
    throw new Error(text || `Supabase HTTP ${res.status}`);
  }

  return text ? JSON.parse(text) : null;
}

function isAdmin(req, env) {
  return req.headers.get("authorization") === `Bearer ${env.ADMIN_TOKEN}`;
}

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") {
      return json({ ok: true });
    }

    const url = new URL(req.url);

    try {
      if (req.method === "GET" && url.pathname === "/") {
        return json({
          name: "ViraPlay API",
          ok: true
        });
      }

      if (req.method === "POST" && url.pathname === "/api/register") {
        const b = await req.json();

        if (!b.device_id || !b.device_secret || !b.pairing_code) {
          return json({ error: "missing_fields" }, 400);
        }

        const rows = await sb(
          env,
          "viraplay_devices?on_conflict=device_id",
          {
            method: "POST",
            prefer: "resolution=merge-duplicates,return=representation",
            body: JSON.stringify({
              device_id: b.device_id,
              device_secret: b.device_secret,
              pairing_code: b.pairing_code,
              platform: b.platform || "ANDROID",
              updated_at: new Date().toISOString()
            })
          }
        );

        return json({
          ok: true,
          device: rows?.[0] || null
        });
      }

      if (req.method === "GET" && url.pathname === "/api/config") {
        const deviceId = url.searchParams.get("device_id") || "";
        const secret = url.searchParams.get("secret") || "";

        const rows = await sb(
          env,
          `viraplay_devices?device_id=eq.${encodeURIComponent(deviceId)}&device_secret=eq.${encodeURIComponent(secret)}&select=enabled,playlist_url,playlist_name`
        );

        if (!rows?.length) {
          return json({
            enabled: false,
            playlist_url: null
          }, 404);
        }

        return json(rows[0]);
      }

      if (
        url.pathname.startsWith("/api/admin/") &&
        !isAdmin(req, env)
      ) {
        return json({ error: "unauthorized" }, 401);
      }

      if (
        req.method === "GET" &&
        url.pathname === "/api/admin/devices"
      ) {
        const rows = await sb(
          env,
          "viraplay_devices?select=device_id,pairing_code,label,platform,enabled,playlist_url&order=created_at.desc"
        );

        return json(rows || []);
      }

      if (
        req.method === "POST" &&
        url.pathname === "/api/admin/claim"
      ) {
        const b = await req.json();

        const rows = await sb(
          env,
          `viraplay_devices?pairing_code=eq.${encodeURIComponent(b.pairing_code || "")}`,
          {
            method: "PATCH",
            body: JSON.stringify({
              label: b.label || null,
              playlist_url: b.playlist_url || null,
              enabled: true,
              claimed_at: new Date().toISOString(),
              updated_at: new Date().toISOString()
            })
          }
        );

        return json({
          ok: true,
          device: rows?.[0] || null
        });
      }

      if (
        req.method === "POST" &&
        url.pathname === "/api/admin/update"
      ) {
        const b = await req.json();

        const patch = {
          enabled: b.enabled !== false,
          playlist_url: b.playlist_url || null,
          updated_at: new Date().toISOString()
        };

        if (b.label !== undefined) {
          patch.label = b.label;
        }

        const rows = await sb(
          env,
          `viraplay_devices?device_id=eq.${encodeURIComponent(b.device_id || "")}`,
          {
            method: "PATCH",
            body: JSON.stringify(patch)
          }
        );

        return json({
          ok: true,
          device: rows?.[0] || null
        });
      }

      return json({ error: "not_found" }, 404);
    } catch (e) {
      return json({
        error: "server_error",
        detail: String(e?.message || e)
      }, 500);
    }
  }
};
