import { ref } from 'vue'
import { useWebSocketStore } from '../stores/websocket'
export function useAction() {
  const busy = ref(false)
  const ws = useWebSocketStore()
  async function act(action: string, data: Record<string, unknown> = {}, gameId?: string) {
    if (busy.value) return false
    busy.value = true
    try {
      await ws.send(action, data, gameId)
      return true
    } catch (e) {
      ws.error = e instanceof Error ? e.message : '操作失败'
      return false
    } finally {
      busy.value = false
    }
  }
  return { busy, act }
}
