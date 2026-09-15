<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Dices, Trophy } from 'lucide-vue-next'
import { useGameStore } from '../../stores/game'
import { useRoomStore } from '../../stores/room'
import { useAction } from '../../lib/useAction'
const games = useGameStore(),
  room = useRoomStore(),
  { busy, act } = useAction()
const rolling = ref(false)
let animation: ReturnType<typeof setTimeout> | undefined
const value = computed(() => Number(games.view?.myChoice ?? 5))
const patterns: Record<number, number[]> = {
  1: [5],
  2: [1, 9],
  3: [1, 5, 9],
  4: [1, 3, 7, 9],
  5: [1, 3, 5, 7, 9],
  6: [1, 3, 4, 6, 7, 9],
}
const results = computed(() =>
  Object.entries(games.view?.rolls ?? {})
    .map(([id, roll]) => ({ player: room.room?.players.find((p) => p.playerId === id), id, roll }))
    .sort((a, b) => b.roll - a.roll),
)
async function roll() {
  rolling.value = true
  await act('SHAKE_DICE')
  animation = setTimeout(() => {
    rolling.value = false
  }, 650)
}
onBeforeUnmount(() => clearTimeout(animation))
</script>
<template>
  <div v-if="games.view" class="dice-game">
    <div class="question-label">
      {{ games.view.rule === 'highest' ? '最大点数接受挑战' : '最小点数接受挑战' }}
    </div>
    <h2 class="game-question">
      手气这件事，
      <br />
      摇一下就知道。
    </h2>
    <template v-if="!games.view.complete">
      <div
        class="play-die"
        :class="{ rolling }"
        :aria-label="games.view.hasActed ? `你的点数 ${value}` : '等待摇骰子'"
      >
        <i v-for="n in 9" :key="n" :class="{ visible: patterns[value]?.includes(n) }" />
      </div>
      <button
        class="button primary roll-button"
        :disabled="busy || games.view.hasActed || rolling"
        @click="roll"
      >
        <Dices :size="21" />
        {{ games.view.hasActed ? `摇到了 ${value} 点` : '摇骰子' }}
      </button>
      <p class="secret-note">
        {{ games.view.hasActed ? '你的点数已锁定，等待其他玩家' : '每人一次机会，命运交给骰子。' }}
      </p>
    </template>
    <div v-else class="results-list">
      <h3>
        <Trophy :size="20" />
        手气排行榜
      </h3>
      <div
        v-for="(result, index) in results"
        :key="result.id"
        class="result-row"
        :style="{ '--order': index }"
      >
        <span class="ranking">{{ index + 1 }}</span>
        <span class="result-avatar">{{ result.player?.avatar }}</span>
        <strong class="flex-1">{{ result.player?.nickname }}</strong>
        <span v-if="games.view.loserIds?.includes(result.id)" class="challenge-tag">接受挑战</span>
        <span class="result-value">
          {{ result.roll }}
          <small>点</small>
        </span>
      </div>
      <p v-if="games.view.timedOut" class="muted text-center">未摇骰子的玩家本轮不计入排名。</p>
    </div>
  </div>
</template>
