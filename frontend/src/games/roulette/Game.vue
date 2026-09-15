<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RotateCw } from 'lucide-vue-next'
import { useGameStore } from '../../stores/game'
import { useUserStore } from '../../stores/user'
import { useAction } from '../../lib/useAction'
import PlayerChoices from '../../components/PlayerChoices.vue'
const game = useGameStore(),
  user = useUserStore(),
  { busy, act } = useAction()
const isSpinner = computed(() => game.view?.spinnerId === user.credentials?.playerId)
const finalAngle = (index: number) => 360 - ((index + 0.5) * 360) / 7
const rotation = ref(game.view?.resultIndex === undefined ? 0 : finalAngle(game.view.resultIndex))
const spinning = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
watch(
  () => game.view?.resultIndex,
  (index, old) => {
    if (index === undefined || index === old) return
    spinning.value = true
    rotation.value = 1800 + finalAngle(index)
    timer = setTimeout(() => {
      spinning.value = false
    }, 3300)
  },
)
onBeforeUnmount(() => clearTimeout(timer))
</script>
<template>
  <div v-if="game.view" class="roulette-game">
    <div class="question-label">转动一点运气，制造一点惊喜</div>
    <h2 class="game-question">今天的幸运儿是…</h2>
    <div class="roulette-wrap">
      <div class="roulette-pointer"></div>
      <div class="roulette-wheel" :style="{ transform: `rotate(${rotation}deg)` }">
        <span
          v-for="(option, index) in game.view.options"
          :key="option"
          :style="{ transform: `rotate(${((index + 0.5) * 360) / 7}deg)` }"
        >
          <b>{{ option }}</b>
        </span>
      </div>
      <div class="roulette-center">✦</div>
    </div>
    <h3 v-if="game.view.result && !spinning" class="roulette-result">{{ game.view.result }}</h3>
    <button
      v-if="game.view.resultIndex === undefined && !game.view.complete"
      class="button primary"
      :disabled="!isSpinner || busy"
      @click="act('SPIN')"
    >
      <RotateCw :size="19" />
      {{ isSpinner ? '转动轮盘' : '等待房主转动轮盘' }}
    </button>
    <p v-if="spinning" class="secret-note">好运正在赶来…</p>
    <template v-if="game.view.needsSelection && !spinning">
      <p class="secret-note">{{ isSpinner ? '选择一位玩家接受挑战' : '等待转盘玩家指定一人' }}</p>
      <PlayerChoices
        :disabled="!isSpinner || busy"
        @choose="(id) => act('SELECT_PLAYER', { targetPlayerId: id })"
      />
    </template>
    <p v-if="game.view.timedOut" class="muted text-center">时间到了，准备下一轮吧。</p>
  </div>
</template>
