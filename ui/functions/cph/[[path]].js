export async function onRequest(context) {
  const url = new URL(context.request.url);
  const targetUrl = `http://213.199.38.16${url.pathname}${url.search}`;

  const requestHeaders = new Headers(context.request.headers);
  requestHeaders.set('X-Forwarded-For', context.request.headers.get('CF-Connecting-IP') ?? '');

  const response = await fetch(targetUrl, {
    method: context.request.method,
    headers: requestHeaders,
    body: ['GET', 'HEAD'].includes(context.request.method) ? undefined : context.request.body,
  });

  const responseHeaders = new Headers(response.headers);
  responseHeaders.set('Access-Control-Allow-Origin', url.origin);
  responseHeaders.set('Access-Control-Allow-Credentials', 'true');

  return new Response(response.body, {
    status: response.status,
    headers: responseHeaders,
  });
}
