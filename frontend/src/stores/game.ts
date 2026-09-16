import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { GameView } from '../lib/types'
export const useGameStore = defineStore('game', () => {
  const view = ref<GameView | null>(null)
  function update(game?: GameView) {
    view.value = game ?? null
  }
  return { view, update }
})
