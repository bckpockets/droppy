export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const headers = { "Access-Control-Allow-Origin": "*" };

    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          ...headers,
          "Access-Control-Allow-Methods": "GET, POST",
          "Access-Control-Allow-Headers": "Content-Type",
        },
      });
    }

    if (url.pathname !== "/dry") {
      return new Response("Not found", { status: 404, headers });
    }

    if (request.method === "POST") {
      const body = await request.json();
      const name = body.name?.toLowerCase()?.trim();
      if (!name || !body.response) {
        return new Response("Bad request", { status: 400, headers });
      }
      await env.DROPPY.put(name, body.response, { expirationTtl: 300 });
      return new Response("OK", { headers });
    }

    if (request.method === "GET") {
      const name = url.searchParams.get("name")?.toLowerCase()?.trim();
      if (!name) {
        return new Response("Bad request", { status: 400, headers });
      }
      const response = await env.DROPPY.get(name);
      if (!response) {
        return new Response("Not found", { status: 404, headers });
      }
      return new Response(response, { headers: { ...headers, "Content-Type": "text/plain" } });
    }

    return new Response("Method not allowed", { status: 405, headers });
  },
};
