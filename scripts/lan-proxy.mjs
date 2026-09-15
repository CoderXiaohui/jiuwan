// Optional, project-only LAN entry for container runtimes that publish to loopback.
// Usage: LAN_HOST=192.168.1.23 node scripts/lan-proxy.mjs
import net from 'node:net'
import os from 'node:os'
const lanHost = process.env.LAN_HOST ?? os.networkInterfaces().en0?.find(address => address.family === 'IPv4' && !address.internal)?.address
const port = Number(process.env.FRONTEND_PORT ?? 8088)
if (!lanHost || !net.isIPv4(lanHost) || !Number.isInteger(port) || port < 1024 || port > 65535) {
  console.error('Set LAN_HOST to this computer’s Wi-Fi IPv4 address, and FRONTEND_PORT to a valid port (default 8088).')
  process.exit(1)
}
const connections = new Set()
const server = net.createServer(client => {
  if (connections.size >= 128) { client.destroy(); return }
  const upstream = net.connect({ host: '127.0.0.1', port })
  connections.add(client)
  client.setTimeout(120000, () => client.destroy())
  upstream.setTimeout(120000, () => upstream.destroy())
  client.on('error', () => upstream.destroy())
  upstream.on('error', () => client.destroy())
  client.on('close', () => { connections.delete(client); upstream.destroy() })
  upstream.on('close', () => client.destroy())
  client.pipe(upstream).pipe(client)
})
server.on('error', error => { console.error(`LAN entry failed: ${error.message}`); process.exitCode = 1 })
server.listen(port, lanHost, () => console.log(`Jiuwan LAN entry: http://${lanHost}:${port} → http://127.0.0.1:${port}`))
function shutdown() { for (const client of connections) client.destroy(); server.close() }
process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
