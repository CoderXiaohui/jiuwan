<script setup lang="ts">
import { computed } from 'vue'
import { Heart, Zap } from 'lucide-vue-next'
import { useGameStore } from '../../stores/game'
import { useRoomStore } from '../../stores/room'
import { useUserStore } from '../../stores/user'
import { useAction } from '../../lib/useAction'
const game = useGameStore(),
  room = useRoomStore(),
  user = useUserStore(),
  { busy, act } = useAction()
const selected = computed(() =>
  room.room?.players.find((p) => game.view?.selectedIds?.includes(p.playerId)),
)
const isMe = computed(() => selected.value?.playerId === user.credentials?.playerId)
</script>
<template>
  <div v-if="game.view" class="truth-game">
    <div class="question-label">命运选中了你</div>
    <div class="selected-avatar">{{ selected?.avatar }}</div>
    <h2 class="game-question">
      {{ selected?.nickname }}
      <span class="muted">，轮到你啦。</span>
    </h2>
    <template v-if="!game.view.complete">
      <p class="secret-note">
        {{ isMe ? '说点真心话，还是挑战一下自己？' : '看看 TA 会做出怎样的选择' }}
      </p>
      <div class="truth-options">
        <button
          class="truth-option"
          :disabled="!isMe || busy"
          @click="act('DRAW', { type: 'truth' })"
        >
          <Heart :size="35" />
          <strong>真心话</strong>
          <small>聊点不一样的</small>
        </button>
        <button
          class="truth-option dare"
          :disabled="!isMe || busy"
          @click="act('DRAW', { type: 'dare' })"
        >
          <Zap :size="35" />
          <strong>大冒险</strong>
          <small>给勇气一点舞台</small>
        </button>
      </div>
    </template>
    <div v-else class="revealed-card">
      <span>
        {{
          game.view.cardType === 'dare'
            ? 'DARE · 大冒险'
            : game.view.cardType
              ? 'TRUTH · 真心话'
              : '本轮结束'
        }}
      </span>
      <h3>{{ game.view.question ?? '时间到了，下次再挑战！' }}</h3>
      <small>不想回答或执行？可以轻松跳过。</small>
    </div>
  </div>
</template>
