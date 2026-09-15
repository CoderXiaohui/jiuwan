import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Credentials } from '../lib/types'
export const avatars = ['😎', '🤡', '🐶', '🐱', '👻', '🔥', '🍺', '🥳', '🦊', '🐼', '👽', '🪩']
export const useUserStore = defineStore('user', () => {
  const credentials = ref<Credentials | null>(null)
  const nickname = ref('')
  const avatar = ref('😎')
  try {
    const saved = JSON.parse(localStorage.getItem('jiuwan.session') ?? 'null') as Credentials | null
    if (saved && /^[0-9]{6}$/.test(saved.roomCode) && saved.playerToken && saved.playerId)
      credentials.value = saved
    nickname.value = localStorage.getItem('jiuwan.nickname') ?? ''
    avatar.value = localStorage.getItem('jiuwan.avatar') ?? '😎'
  } catch {
    /* A corrupted local session can be replaced by joining again. */
  }
  function save(value: Credentials) {
    credentials.value = value
    localStorage.setItem('jiuwan.session', JSON.stringify(value))
  }
  function profile(name: string, emoji: string) {
    nickname.value = name
    avatar.value = emoji
    localStorage.setItem('jiuwan.nickname', name)
    localStorage.setItem('jiuwan.avatar', emoji)
  }
  function clear() {
    credentials.value = null
    localStorage.removeItem('jiuwan.session')
  }
  return { credentials, nickname, avatar, save, profile, clear }
})
