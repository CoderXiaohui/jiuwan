export async function api<T>(path: string, body?: unknown, token?: string): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(12000),
  })
  const result = await response.json()
  if (!result.success) throw new Error(result.error?.message ?? '服务暂时不可用，请稍后重试')
  return result.data as T
}
export function requestId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (v) => v.toString(16).padStart(2, '0')).join('')
}
