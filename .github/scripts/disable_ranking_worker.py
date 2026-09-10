from pathlib import Path

p = Path('cloudflare/ranking-worker.js')
s = p.read_text()

if 'const RANKING_ENABLED = false;' not in s:
    s = 'const RANKING_ENABLED = false;\n\n' + s

s = s.replace(
    '      if (!env.DB) return json({ error: "DATABASE_NOT_CONFIGURED" }, 500);\n      if (!env.RANKING_SIGNING_SECRET) return json({ error: "SECRET_NOT_CONFIGURED" }, 500);\n',
    '      if (!env.DB) return json({ error: "DATABASE_NOT_CONFIGURED" }, 500);\n'
)

s = s.replace(
    '      if (request.method === "POST" && path === "/v1/ranking/submit") {\n        return submitRanking(request, env);\n      }\n',
    '      if (request.method === "POST" && path === "/v1/ranking/submit") {\n        if (!RANKING_ENABLED) return json({ error: "FEATURE_DISABLED" }, 404);\n        if (!env.RANKING_SIGNING_SECRET) return json({ error: "SECRET_NOT_CONFIGURED" }, 500);\n        return submitRanking(request, env);\n      }\n'
)

s = s.replace(
    '      if (request.method === "DELETE" && path === "/v1/ranking/player") {\n        return deletePlayerRecords(request, env);\n      }\n',
    '      if (request.method === "DELETE" && path === "/v1/ranking/player") {\n        if (!RANKING_ENABLED) return json({ error: "FEATURE_DISABLED" }, 404);\n        if (!env.RANKING_SIGNING_SECRET) return json({ error: "SECRET_NOT_CONFIGURED" }, 500);\n        return deletePlayerRecords(request, env);\n      }\n'
)

s = s.replace(
    '      if (request.method === "DELETE" && path === "/v1/ranking/player/games") {\n        return deleteSelectedPlayerRecords(request, env);\n      }\n',
    '      if (request.method === "DELETE" && path === "/v1/ranking/player/games") {\n        if (!RANKING_ENABLED) return json({ error: "FEATURE_DISABLED" }, 404);\n        if (!env.RANKING_SIGNING_SECRET) return json({ error: "SECRET_NOT_CONFIGURED" }, 500);\n        return deleteSelectedPlayerRecords(request, env);\n      }\n'
)

s = s.replace(
    '      if (request.method === "GET" && match) {\n        return getRanking(env, match[1], match[2], url.searchParams.get("playerId"));\n      }\n',
    '      if (request.method === "GET" && match) {\n        if (!RANKING_ENABLED) return json({ error: "FEATURE_DISABLED" }, 404);\n        if (!env.RANKING_SIGNING_SECRET) return json({ error: "SECRET_NOT_CONFIGURED" }, 500);\n        return getRanking(env, match[1], match[2], url.searchParams.get("playerId"));\n      }\n'
)

p.write_text(s)
