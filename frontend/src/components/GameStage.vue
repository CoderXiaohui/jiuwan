<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowRight, CheckCheck, Clock, EyeOff, LogOut, ShieldCheck } from 'lucide-vue-next'
import { useGameStore } from '../stores/game'
import { useRoomStore } from '../stores/room'
import { useWebSocketStore } from '../stores/websocket'
import { catalog } from '../games/catalog'
import { gameComponents } from '../games/registry'
import { useAction } from '../lib/useAction'
const games = useGameStore(),
  room = useRoomStore(),
  ws = useWebSocketStore(),
  { busy, act } = useAction()
const now = ref(Date.now())
watch(
  () => games.view?.instanceId,
  () => {
    now.value = Date.now()
  },
)
const timer = setInterval(() => {
  now.value = Date.now()
}, 200)
onBeforeUnmount(() => clearInterval(timer))
const countdown = computed(() =>
  Math.max(0, Math.ceil(((games.view?.startsAt ?? 0) - now.value - ws.serverOffset) / 1000)),
)
const remaining = computed(() =>
  Math.max(0, Math.ceil(((games.view?.deadline ?? 0) - now.value - ws.serverOffset) / 1000)),
)
const meta = computed(() => catalog.find((g) => g.id === games.view?.gameId))
</script>
<template>
  <section v-if="games.view" class="game-stage" :class="meta?.color">
    <div class="stage-header">
      <div>
        <span class="eyebrow subtle">{{ meta?.english }}</span>
        <h1>{{ meta?.name }}</h1>
      </div>
      <span class="round-pill">ROUND {{ String(games.view.round).padStart(2, '0') }}</span>
    </div>
    <div class="stage-status">
      <span v-if="games.view.complete">
        <CheckCheck :size="16" />
        本轮已揭晓
      </span>
      <span v-else-if="!countdown && games.view.deadline === 0">
        <EyeOff :size="16" />
        等待房主结束本轮
      </span>
      <span v-else>
        <Clock :size="16" />
        {{
          countdown
            ? '准备开始'
            : `${games.view.poker || games.view.angryBirds ? '本次行动' : '剩余'} ${remaining} 秒`
        }}
      </span>
      <span v-if="games.view.angryBirds">
        <template v-if="games.view.complete">
          {{ games.view.angryBirds.bombCount }} 只炸弹已揭晓
        </template>
        <template v-else>
          剩余
          {{ games.view.angryBirds.birds.filter((bird) => bird.status === 'hidden').length }} 只小鸟
        </template>
      </span>
      <span v-else-if="games.view.bigSmall">{{ games.view.participants.length }} 人参与</span>
      <span v-else-if="games.view.poker">
        {{
          games.view.poker.seats.filter((s) => s.status === 'active' || s.status === 'winner')
            .length
        }}
        / {{ games.view.participants.length }} 人在场
      </span>
      <span v-else>{{ games.view.submittedCount }} 人已完成</span>
    </div>
    <div class="play-surface">
      <div v-if="countdown" class="countdown-overlay" role="status">
        <span>准备好了吗？</span>
        <strong :key="countdown">{{ countdown }}</strong>
        <small>快乐即将开始</small>
      </div>
      <fieldset :disabled="countdown > 0 || ws.status !== 'connected'">
        <component :is="gameComponents[games.view.gameId]" :key="games.view.instanceId" />
      </fieldset>
    </div>
    <div v-if="games.view.complete && room.room?.events.length" class="event-list">
      <p v-for="(event, index) in room.room.events" :key="index">✦ {{ event.message }}</p>
      <small>自愿参与，不想做可以跳过。</small>
    </div>
    <div class="stage-footer">
      <template v-if="room.isOwner">
        <button
          v-if="games.view.complete"
          class="button primary"
          :disabled="busy || ws.status !== 'connected'"
          @click="act('NEXT_ROUND')"
        >
          下一局
          <ArrowRight :size="18" />
        </button>
        <button
          class="button ghost"
          :disabled="busy || ws.status !== 'connected'"
          @click="act('END_GAME')"
        >
          <LogOut :size="17" />
          返回大厅 · 换个游戏
        </button>
      </template>
      <p v-else class="waiting-note">
        {{ games.view.complete ? '等待房主开始下一局，或换个游戏' : '一起玩，才有意思。' }}
      </p>
    </div>
    <p class="safe-note">
      <ShieldCheck :size="14" />
      舒服最重要，每个挑战都可以跳过。
    </p>
  </section>
</template>
