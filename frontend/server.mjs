import { createReadStream, statSync } from 'node:fs'
import { createServer, request as proxyRequest } from 'node:http'
import { extname, join, normalize } from 'node:path'

const root = '/web/dist'
const contentTypes = new Map([
  ['.html', 'text/html; charset=utf-8'], ['.js', 'text/javascript; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'], ['.json', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml'], ['.png', 'image/png'], ['.ico', 'image/x-icon'],
])

function proxy(req, res) {
  const upstream = proxyRequest({
    hostname: 'backend', port: 8080, method: req.method, path: req.url,
    headers: { ...req.headers, host: 'backend:8080' },
  }, (upstreamResponse) => {
    res.writeHead(upstreamResponse.statusCode ?? 502, upstreamResponse.headers)
    upstreamResponse.pipe(res)
  })
  upstream.on('error', () => { res.writeHead(502); res.end('Backend unavailable') })
  req.pipe(upstream)
}

function staticFile(req, res) {
  const requested = decodeURIComponent((req.url ?? '/').split('?')[0])
  const normalized = normalize(requested).replace(/^(\.\.(\/|\\|$))+/, '')
  let path = join(root, normalized)
  try {
    if (requested.endsWith('/') || !statSync(path).isFile()) path = join(root, 'index.html')
  } catch { path = join(root, 'index.html') }
  res.setHeader('Content-Type', contentTypes.get(extname(path)) ?? 'application/octet-stream')
  res.setHeader('X-Content-Type-Options', 'nosniff')
  createReadStream(path).on('error', () => { res.writeHead(404); res.end('Not found') }).pipe(res)
}

const port = Number(process.env.PORT ?? 8080)
createServer((req, res) => req.url?.startsWith('/api/') ? proxy(req, res) : staticFile(req, res))
  .listen(port, '0.0.0.0')
