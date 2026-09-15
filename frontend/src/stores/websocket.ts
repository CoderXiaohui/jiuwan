import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Envelope } from '../lib/types'
import { requestId } from '../lib/api'
import { useUserStore } from './user'
import { useRoomStore } from './room'
import { useGameStore } from './game'
export const useWebSocketStore = defineStore('websocket', () => {
  const status = ref<'connecting' | 'connected' | 'reconnecting' | 'offline'>('offline')
  const error = ref('')
  const serverOffset = ref(0)
  let socket: WebSocket | null = null
  let retry: ReturnType<typeof setTimeout> | undefined
  let heartbeatTimer: ReturnType<typeof setInterval> | undefined
  let attempt = 0,
    stopped = true,
    lastHeartbeat = 0
  const pending = new Map<
    string,
    { resolve: () => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout> }
  >()
  function rejectPending(message: string) {
    for (const p of pending.values()) {
      clearTimeout(p.timer)
      p.reject(new Error(message))
    }
    pending.clear()
  }
  function stopTimers() {
    clearTimeout(retry)
    clearInterval(heartbeatTimer)
  }
  function disconnect() {
    stopped = true
    stopTimers()
    if (socket) {
      socket.onclose = null
      socket.close()
      socket = null
    }
    status.value = 'offline'
    rejectPending('连接已断开，请重试')
  }
  function reconnect() {
    disconnect()
    connect()
  }
  function sendRaw(value: unknown) {
    if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify(value))
  }
  function heartbeat() {
    if (Date.now() - lastHeartbeat > 55000) {
      socket?.close()
      return
    }
    const credentials = useUserStore().credentials
    if (credentials)
      sendRaw({ type: 'HEARTBEAT', roomCode: credentials.roomCode, playerId: credentials.playerId })
  }
  function connect() {
    if (
      !useUserStore().credentials ||
      socket?.readyState === WebSocket.OPEN ||
      socket?.readyState === WebSocket.CONNECTING
    )
      return
    stopped = false
    status.value = attempt ? 'reconnecting' : 'connecting'
    const ws = new WebSocket(
      `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
    )
    socket = ws
    const handshakeTimeout = setTimeout(() => {
      if (socket === ws && status.value !== 'connected') ws.close()
    }, 12000)
    ws.onopen = () => {
      if (socket !== ws) {
        ws.close()
        return
      }
      ws.send(
        JSON.stringify({
          type: 'RECONNECT',
          ...useUserStore().credentials,
          requestId: requestId(),
        }),
      )
    }
    ws.onmessage = (message: MessageEvent<string>) => {
      if (socket !== ws) return
      try {
        dispatch(JSON.parse(message.data) as Envelope)
      } catch {
        error.value = '状态同步失败，请重新连接'
      }
    }
    ws.onclose = () => {
      clearTimeout(handshakeTimeout)
      if (socket !== ws) return
      clearInterval(heartbeatTimer)
      socket = null
      rejectPending('连接中断，请恢复连接后确认当前状态')
      if (!stopped) {
        status.value = navigator.onLine ? 'reconnecting' : 'offline'
        retry = setTimeout(connect, Math.min(1000 * 2 ** attempt++, 10000))
      }
    }
    ws.onerror = () => ws.close()
  }
  function dispatch(message: Envelope) {
    if (message.serverTime) serverOffset.value = message.serverTime - Date.now()
    if (message.type === 'ERROR') {
      const text = message.error?.message ?? '操作失败，请重试'
      error.value = text
      if (message.requestId) {
        const p = pending.get(message.requestId)
        if (p) {
          clearTimeout(p.timer)
          p.reject(new Error(text))
          pending.delete(message.requestId)
        }
      }
      if (
        ['ROOM_NOT_FOUND', 'ROOM_CLOSED', 'INVALID_PLAYER', 'SESSION_REPLACED'].includes(
          message.error?.code ?? '',
        )
      ) {
        disconnect()
        useRoomStore().clear()
        if (message.error?.code !== 'SESSION_REPLACED') useUserStore().clear()
      }
      return
    }
    lastHeartbeat = Date.now()
    if (message.data) useRoomStore().update(message.data)
    if (message.type === 'PLAYER_RECONNECTED' && status.value !== 'connected') {
      status.value = 'connected'
      attempt = 0
      error.value = ''
      clearInterval(heartbeatTimer)
      heartbeatTimer = setInterval(heartbeat, 20000)
    }
    if (message.requestId) {
      const p = pending.get(message.requestId)
      if (p) {
        clearTimeout(p.timer)
        p.resolve()
        pending.delete(message.requestId)
      }
    }
  }
  async function send(action: string, data: Record<string, unknown> = {}, gameId?: string) {
    const user = useUserStore(),
      game = useGameStore()
    if (status.value !== 'connected' || !user.credentials) throw new Error('请等待连接恢复')
    const id = requestId()
    return new Promise<void>((resolve, reject) => {
      pending.set(id, {
        resolve,
        reject,
        timer: setTimeout(() => {
          pending.delete(id)
          reject(new Error('确认超时，请检查最新游戏状态再重试'))
        }, 12000),
      })
      sendRaw({
        type: 'GAME_ACTION',
        requestId: id,
        roomCode: user.credentials?.roomCode,
        playerId: user.credentials?.playerId,
        gameId: gameId ?? game.view?.gameId,
        instanceId: game.view?.instanceId,
        action,
        data,
      })
    })
  }
  window.addEventListener('online', () => {
    if (!stopped) {
      clearTimeout(retry)
      attempt = 0
      connect()
    }
  })
  window.addEventListener('offline', () => {
    if (!stopped) {
      status.value = 'offline'
      socket?.close()
    }
  })
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && !stopped) {
      if (socket?.readyState === WebSocket.OPEN) heartbeat()
      else {
        clearTimeout(retry)
        connect()
      }
    }
  })
  return { status, error, serverOffset, connect, disconnect, reconnect, send, heartbeat }
})
