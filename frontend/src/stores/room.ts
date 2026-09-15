import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { Room } from '../lib/types'
import { useUserStore } from './user'
import { useGameStore } from './game'
export const useRoomStore = defineStore('room', () => {
  const room = ref<Room | null>(null)
  const isOwner = computed(() => room.value?.ownerId === useUserStore().credentials?.playerId)
  const online = computed(
    () => room.value?.players.filter((player) => player.connected).length ?? 0,
  )
  function update(value: Room) {
    if (room.value?.roomCode === value.roomCode && value.version < room.value.version) return
    room.value = value
    useGameStore().update(value.game)
  }
  function clear() {
    room.value = null
    useGameStore().update()
  }
  return { room, isOwner, online, update, clear }
})
