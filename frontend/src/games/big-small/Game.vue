<script setup lang="ts">
import { computed } from 'vue'
import { Eye } from 'lucide-vue-next'
import PlayingCards from '../../components/PlayingCards.vue'
import { useGameStore } from '../../stores/game'
import { useRoomStore } from '../../stores/room'
import { useUserStore } from '../../stores/user'
import { useAction } from '../../lib/useAction'

const games = useGameStore(),
  room = useRoomStore(),
  user = useUserStore()
const { busy, act } = useAction()
const participating = computed(() =>
  games.view?.participants.includes(user.credentials?.playerId ?? ''),
)
const player = (id: string) => room.room?.players.find((p) => p.playerId === id)
</script>

<template>
  <div v-if="games.view?.bigSmall" class="big-small-game">
    <div class="big-small-intro">
      <span class="question-label">一人一张 · 玩法由大家约定</span>
      <h2>{{ games.view.complete ? '全部亮牌，秘密揭晓。' : '别人的牌，都在你眼前。' }}</h2>
      <p v-if="games.view.complete">本轮已结束，所有人的牌都在这里。</p>
      <p v-else-if="participating">你的牌将在本轮结束后揭晓。</p>
      <p v-else>你未参与本轮发牌，结束后可查看全部牌；下一局在线即可加入。</p>
    </div>

    <div class="big-small-table">
      <div class="big-small-seats">
        <article
          v-for="seat in games.view.bigSmall.seats"
          :key="seat.playerId"
          class="big-small-seat"
          :class="{ self: seat.playerId === user.credentials?.playerId }"
          :data-player-id="seat.playerId"
        >
          <div class="big-small-seat-name">
            <span>{{ player(seat.playerId)?.avatar }}</span>
            <strong>{{ player(seat.playerId)?.nickname ?? '玩家' }}</strong>
            <small v-if="seat.playerId === user.credentials?.playerId">我</small>
          </div>
          <PlayingCards :cards="seat.card ? [seat.card] : undefined" :count="1" />
          <p>{{ seat.card ? '已亮牌' : '结束后揭晓' }}</p>
        </article>
      </div>
      <p class="big-small-table-note">52 张牌 · 无大小王 · 每人 1 张</p>
    </div>

    <div v-if="!games.view.complete" class="big-small-controls">
      <button
        v-if="room.isOwner"
        class="button primary full"
        :disabled="busy"
        @click="act('END_ROUND')"
      >
        <Eye :size="18" />
        结束本轮 · 全部亮牌
      </button>
      <p>
        {{
          room.isOwner
            ? '大家准备好后，由你结束本轮。'
            : participating
              ? '等待房主结束本轮，揭晓你的牌。'
              : '等待房主结束本轮，查看全部牌。'
        }}
      </p>
    </div>
    <p class="big-small-help">本轮不限时，系统只负责随机发牌和亮牌。</p>
  </div>
</template>

<style scoped>
.big-small-intro {
  text-align: center;
  margin-bottom: 24px;
}
.big-small-intro h2 {
  font-size: clamp(20px, 3vw, 28px);
  margin: 12px 0 9px;
}
.big-small-intro p,
.big-small-controls p,
.big-small-help {
  color: #babba5;
  font-size: 12px;
  line-height: 1.8;
}
.big-small-table {
  padding: 18px;
  border: 1px solid #7a7950;
  border-radius: 20px;
  background: radial-gradient(ellipse at top, #304d3e, #1b3027);
  box-shadow: inset 0 0 0 5px #23392e;
}
.big-small-seats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
}
.big-small-seat {
  min-width: 0;
  padding: 14px 8px 10px;
  border: 1px solid #475642;
  border-radius: 12px;
  background: #15271ed9;
  text-align: center;
}
.big-small-seat.self {
  border-color: #edd289;
  background: #394732;
}
.big-small-seat-name {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 12px;
}
.big-small-seat-name > span {
  font-size: 23px;
  flex-shrink: 0;
}
.big-small-seat-name > strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.big-small-seat-name > small {
  flex-shrink: 0;
  color: #efd38d;
}
.big-small-seat > p,
.big-small-table-note {
  color: #c6cdb6;
  font-size: 11px;
  margin: 7px 0 0;
}
.big-small-table-note {
  text-align: center;
  margin-top: 18px;
}
.big-small-controls {
  margin-top: 24px;
  text-align: center;
}
.big-small-controls .primary {
  color: #2d3323;
  background: #efd38d;
  border-color: #efd38d;
}
.big-small-help {
  text-align: center;
  margin: 16px 0 0;
}
@media (max-width: 540px) {
  .big-small-table {
    padding: 14px 10px;
  }
  .big-small-seats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }
  .big-small-seat {
    padding-inline: 5px;
  }
}
</style>
