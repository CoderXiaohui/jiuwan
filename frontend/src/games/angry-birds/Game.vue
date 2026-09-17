<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Bomb, Feather } from 'lucide-vue-next'
import BirdArt from '../../components/BirdArt.vue'
import { useGameStore } from '../../stores/game'
import { useRoomStore } from '../../stores/room'
import { useUserStore } from '../../stores/user'
import { useWebSocketStore } from '../../stores/websocket'
import { useAction } from '../../lib/useAction'

const games = useGameStore(),
  room = useRoomStore(),
  user = useUserStore(),
  ws = useWebSocketStore()
const { busy, act } = useAction()
const board = computed(() => games.view?.angryBirds)
const complete = computed(() => games.view?.complete ?? false)
const now = ref(Date.now())
const timer = setInterval(() => {
  now.value = Date.now()
}, 100)
const canPick = computed(
  () =>
    board.value?.currentPlayerId === user.credentials?.playerId &&
    !complete.value &&
    !busy.value &&
    ws.status === 'connected' &&
    now.value + ws.serverOffset >= (games.view?.startsAt ?? 0) &&
    now.value + ws.serverOffset < (games.view?.deadline ?? 0),
)
const flying = ref(new Set<number>())
const exploding = ref<number | null>(null)
const animationTimers = new Set<ReturnType<typeof setTimeout>>()
function later(callback: () => void) {
  const timer = setTimeout(() => {
    callback()
    animationTimers.delete(timer)
  }, 800)
  animationTimers.add(timer)
}
// Only animate fresh, confirmed moves while continuously connected. Initial/reconnect
// snapshots and duplicate ACKs display the board without replaying past effects.
watch(
  () => [board.value?.lastMove?.turnNumber, ws.status] as const,
  ([turn, status], [previousTurn, previousStatus]) => {
    const move = board.value?.lastMove
    if (
      !move ||
      !turn ||
      turn === previousTurn ||
      status !== 'connected' ||
      previousStatus !== 'connected' ||
      Date.now() + ws.serverOffset - move.at > 1800
    )
      return
    if (complete.value) {
      exploding.value = move.birdId
      later(() => {
        exploding.value = null
      })
    } else {
      flying.value.add(move.birdId)
      later(() => {
        flying.value.delete(move.birdId)
      })
    }
  },
)
onBeforeUnmount(() => {
  clearInterval(timer)
  animationTimers.forEach(clearTimeout)
})
function player(id?: string) {
  return room.room?.players.find((p) => p.playerId === id)
}
function name(id?: string) {
  return player(id)?.nickname ?? '玩家'
}
function pick(birdId: number) {
  if (canPick.value && board.value)
    void act('PICK_BIRD', { birdId, turnNumber: board.value.turnNumber })
}
function label(id: number, status: string) {
  return `小鸟 ${id + 1} · ${
    status === 'flown'
      ? '已飞走'
      : status === 'exploded'
        ? '已引爆'
        : status === 'bomb'
          ? '炸弹鸟'
          : '未点击'
  }`
}
</script>

<template>
  <div v-if="board && games.view" class="angry-birds-game">
    <div class="bird-turn-heading" aria-live="polite" aria-atomic="true">
      <template v-if="complete">
        <span class="bird-kicker">BOOM! · 本局结束</span>
        <h2>{{ name(board.loserId) }} 点中了炸弹！</h2>
        <p>
          {{ board.lastMove?.automatic ? '这次是超时自动点击' : '下次，运气一定会更好' }} ·
          全部炸弹已揭晓
        </p>
      </template>
      <template v-else>
        <span class="bird-kicker">PICK A LITTLE BIRD</span>
        <h2>
          {{
            board.currentPlayerId === user.credentials?.playerId
              ? '轮到你啦，选一只小鸟'
              : `轮到 ${name(board.currentPlayerId)} 啦`
          }}
        </h2>
        <p>{{ board.bombCount }} 只炸弹鸟藏在其中 · 10 秒未点击将自动随机选择</p>
      </template>
    </div>
    <ol class="bird-turn-order" aria-label="本局玩家顺序">
      <li
        v-for="(id, index) in games.view.participants"
        :key="id"
        :class="{ current: !complete && id === board.currentPlayerId, loser: id === board.loserId }"
        :aria-current="!complete && id === board.currentPlayerId ? 'step' : undefined"
      >
        <span class="bird-player-number">{{ index + 1 }}</span>
        <span>{{ player(id)?.avatar }}</span>
        <span class="bird-player-name">{{ name(id) }}</span>
        <small v-if="!player(id)?.connected">离线</small>
      </li>
    </ol>
    <div class="bird-sky" :class="{ 'board-explosion': exploding !== null }">
      <div class="bird-sky-decor" aria-hidden="true">
        <span v-for="i in 8" :key="i" :class="i % 2 ? 'sky-cloud' : 'sky-heart'">
          {{ i % 2 ? '' : '♥' }}
        </span>
      </div>
      <div class="bird-grid" role="group" aria-label="16 只小鸟棋盘">
        <button
          v-for="bird in board.birds"
          :key="bird.id"
          class="bird-cell"
          :class="[bird.status, { flying: flying.has(bird.id), bursting: exploding === bird.id }]"
          :data-bird-id="bird.id"
          :data-status="bird.status"
          :aria-label="label(bird.id, bird.status)"
          :disabled="!canPick || bird.status !== 'hidden'"
          @click="pick(bird.id)"
        >
          <BirdArt v-if="bird.status === 'hidden' || flying.has(bird.id)" />
          <span v-else-if="bird.status === 'flown'" class="bird-empty"><Feather :size="20" /></span>
          <span v-else class="bird-bomb">
            <Bomb :stroke-width="1.8" />
            <span>{{ bird.status === 'exploded' ? 'BOOM!' : '炸弹鸟' }}</span>
          </span>
          <span v-if="exploding === bird.id" class="bird-blast" aria-hidden="true">✹</span>
        </button>
      </div>
      <div class="bird-sky-caption">
        <Feather :size="13" />
        愿你的每一次选择，都平安飞走
      </div>
    </div>
    <p class="bird-last-move" aria-live="polite">
      <template v-if="board.lastMove">
        {{ name(board.lastMove.playerId)
        }}{{ board.lastMove.automatic ? ' 超时，系统自动点击了' : ' 点击了' }}
        {{ board.lastMove.birdId + 1 }} 号小鸟{{ complete ? '，引爆了炸弹。' : '，安全飞走！' }}
      </template>
      <template v-else>顺序已随机排好，小鸟们准备出发！</template>
    </p>
  </div>
</template>

<style scoped>
.angry-birds-game {
  max-width: 680px;
  margin: 0 auto;
  padding: 10px 0 0;
}
.bird-turn-heading {
  text-align: center;
  padding: 0 10px;
}
.bird-kicker {
  color: #a4d9f1;
  font-size: 10px;
  letter-spacing: 2px;
  font-weight: 700;
}
.bird-turn-heading h2 {
  color: #f6f9e9;
  font-size: clamp(17px, 3vw, 25px);
  margin: 10px 0 8px;
  overflow-wrap: anywhere;
}
.bird-turn-heading p {
  color: #aec8cf;
  font-size: 11px;
  line-height: 1.8;
  margin: 0;
}
.bird-turn-order {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
  margin: 18px 0;
  padding: 0;
}
.bird-turn-order li {
  display: flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  border: 1px solid #556367;
  border-radius: 30px;
  padding: 6px 9px;
  font-size: 11px;
  color: #c1d0d0;
  background: #26383a;
}
.bird-turn-order li.current {
  background: #ffe86a;
  border-color: #ffe86a;
  color: #49381c;
  box-shadow: 0 0 0 3px #ffe86a15;
}
.bird-turn-order li.loser {
  color: #ffd3b5;
  border-color: #e88d63;
}
.bird-player-number {
  font-size: 9px;
  opacity: 0.7;
}
.bird-player-name {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bird-turn-order small {
  font-size: 9px;
}
.bird-sky {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  border-radius: 23px;
  background:
    radial-gradient(ellipse at 20% 5%, #b8edff80, transparent 65%),
    linear-gradient(140deg, #81d3f9, #74c5f4 60%, #a2e2fc);
  border: 2px solid #b7ecff;
  box-shadow:
    inset 0 0 40px #effbff25,
    0 12px 35px #0002;
}
.bird-sky-decor {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  color: #ffffff9c;
}
.bird-sky-decor span {
  position: absolute;
}
.sky-cloud {
  width: 12%;
  height: 4%;
  border-radius: 100px;
  background: #ffffff70;
}
.sky-cloud::before {
  content: '';
  position: absolute;
  width: 55%;
  height: 150%;
  bottom: 0;
  left: 18%;
  border-radius: 100%;
  background: inherit;
}
.sky-heart {
  font-size: clamp(18px, 4vw, 30px);
}
.bird-sky-decor span:nth-child(1) {
  top: 5%;
  left: -2%;
}
.bird-sky-decor span:nth-child(2) {
  top: 23%;
  left: 23%;
}
.bird-sky-decor span:nth-child(3) {
  top: 47%;
  right: -3%;
}
.bird-sky-decor span:nth-child(4) {
  top: 1%;
  right: 25%;
}
.bird-sky-decor span:nth-child(5) {
  top: 73%;
  left: 45%;
}
.bird-sky-decor span:nth-child(6) {
  top: 70%;
  left: 0;
}
.bird-sky-decor span:nth-child(7) {
  top: 24%;
  left: 47%;
}
.bird-sky-decor span:nth-child(8) {
  bottom: 3%;
  right: 3%;
}
.bird-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  aspect-ratio: 1;
  gap: clamp(6px, 2vw, 20px);
  padding: clamp(12px, 3vw, 25px);
}
.bird-cell {
  appearance: none;
  position: relative;
  display: grid;
  place-items: center;
  min-width: 0;
  padding: 0;
  background: none;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  transition: transform 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.bird-cell:disabled {
  opacity: 1;
  cursor: default;
}
.bird-cell:enabled:hover {
  transform: translateY(-4px) rotate(-4deg);
}
.bird-cell:enabled:active {
  transform: scale(0.91);
}
.bird-cell:focus-visible {
  outline: 3px solid #285880;
  outline-offset: 3px;
}
.bird-empty {
  display: grid;
  place-items: center;
  width: 45%;
  height: 45%;
  border: 1px dashed #ffffff60;
  border-radius: 50%;
  color: #ffffff88;
}
.bird-bomb {
  display: grid;
  justify-items: center;
  gap: 3px;
  color: #304557;
}
.bird-bomb svg {
  width: clamp(28px, 7vw, 58px);
  height: auto;
  fill: #304557;
  color: #e8f6ff;
}
.bird-bomb span {
  font-size: clamp(8px, 1.7vw, 12px);
  font-weight: 800;
  letter-spacing: 1px;
}
.exploded {
  background: #fff2a9a8;
  box-shadow: 0 0 0 3px #ffb45280;
}
.exploded .bird-bomb {
  color: #a93d20;
}
.exploded .bird-bomb svg {
  fill: #bd492b;
}
.bird-cell.flying {
  z-index: 3;
}
.flying :deep(.bird-art) {
  animation: bird-fly 0.8s ease-in forwards;
  pointer-events: none;
}
.bird-blast {
  position: absolute;
  inset: -30%;
  display: grid;
  place-items: center;
  color: #fff2a1;
  text-shadow: 0 0 20px #ff7b17;
  font-size: clamp(100px, 20vw, 190px);
  line-height: 1;
  pointer-events: none;
  animation: bird-boom 0.8s ease-out forwards;
}
.board-explosion {
  animation: bird-shake 0.4s;
}
.bird-sky-caption {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 5px;
  padding: 0 5px 12px;
  color: #246084;
  font-size: 9px;
  letter-spacing: 1px;
}
.bird-last-move {
  min-height: 32px;
  margin: 13px 8px 0;
  text-align: center;
  color: #aec8cf;
  font-size: 11px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
@keyframes bird-fly {
  20% {
    transform: translateY(6px) scale(0.92);
  }
  60% {
    opacity: 1;
  }
  100% {
    transform: translate(60%, -200%) rotate(25deg) scale(0.3);
    opacity: 0;
  }
}
@keyframes bird-boom {
  0% {
    transform: scale(0.2);
    opacity: 0;
  }
  25% {
    transform: scale(1.2) rotate(20deg);
    opacity: 1;
  }
  100% {
    transform: scale(1.5) rotate(-10deg);
    opacity: 0;
  }
}
@keyframes bird-shake {
  20%,
  60% {
    transform: translateX(-3px);
  }
  40%,
  80% {
    transform: translateX(3px);
  }
}
@media (prefers-reduced-motion: reduce) {
  .bird-cell,
  .bird-sky,
  .bird-blast,
  .flying :deep(.bird-art) {
    animation: none;
    transition: none;
  }
  .flying :deep(.bird-art),
  .bird-blast {
    opacity: 0;
  }
}
</style>
