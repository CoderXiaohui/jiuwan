<script setup lang="ts">
import { computed } from 'vue'
import { Heart, LockKeyhole, Check } from 'lucide-vue-next'
import { useGameStore } from '../../stores/game'
import { useRoomStore } from '../../stores/room'
import { useUserStore } from '../../stores/user'
import { useAction } from '../../lib/useAction'
const game = useGameStore(),
  room = useRoomStore(),
  user = useUserStore(),
  { busy, act } = useAction()
const pair = computed(
  () => room.room?.players.filter((p) => game.view?.selectedIds?.includes(p.playerId)) ?? [],
)
const isMe = computed(() => game.view?.selectedIds?.includes(user.credentials?.playerId ?? ''))
</script>
<template>
  <div v-if="game.view" class="compatibility-game">
    <div class="question-label">此刻，我们想到一起了吗？</div>
    <div class="pair-display">
      <template v-for="(player, index) in pair" :key="player.playerId">
        <Heart v-if="index" class="pair-heart" :size="26" />
        <div>
          <span>{{ player.avatar }}</span>
          <strong>{{ player.nickname }}</strong>
          <small>默契分 {{ player.score }}</small>
        </div>
      </template>
    </div>
    <h2 class="game-question">{{ game.view.question }}</h2>
    <template v-if="!game.view.complete">
      <div class="answer-options">
        <button
          v-for="option in game.view.options"
          :key="option"
          :class="{ chosen: game.view.myChoice === option }"
          :disabled="!isMe || busy || game.view.hasActed"
          @click="act('ANSWER', { answer: option })"
        >
          {{ option }}
          <Check v-if="game.view.myChoice === option" :size="20" />
        </button>
      </div>
      <p class="secret-note">
        <LockKeyhole :size="14" />
        {{
          game.view.hasActed
            ? '答案已锁定，等待另一位玩家'
            : isMe
              ? '悄悄选择，先不要告诉 TA'
              : '本轮是他们的主场，一起见证默契'
        }}
      </p>
    </template>
    <div v-else class="compatibility-result">
      <h3>
        {{
          game.view.cancelled
            ? '这次擦肩而过，下轮再来'
            : game.view.matched
              ? '心有灵犀，默契 +1！'
              : '各有主见，也很精彩！'
        }}
      </h3>
      <div v-if="!game.view.cancelled" class="answer-reveal">
        <div v-for="player in pair" :key="player.playerId">
          <span>{{ player.nickname }}</span>
          <strong>{{ game.view.answers?.[player.playerId] }}</strong>
        </div>
      </div>
    </div>
  </div>
</template>
