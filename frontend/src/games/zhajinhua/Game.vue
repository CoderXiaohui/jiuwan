<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Eye, EyeOff, Swords, Trophy, Wine, X, ArrowUp } from 'lucide-vue-next'
import { useGameStore } from '../../stores/game'
import { useRoomStore } from '../../stores/room'
import { useUserStore } from '../../stores/user'
import { useAction } from '../../lib/useAction'
import PlayingCards from './PlayingCards.vue'
import type { PokerView } from '../../lib/types'

const games = useGameStore(),
  room = useRoomStore(),
  user = useUserStore()
const { busy, act } = useAction()
const choosingOpponent = ref(false),
  hidden = ref(false)
const poker = computed(() => games.view?.poker)
const me = computed(() => poker.value?.seats.find((s) => s.playerId === user.credentials?.playerId))
const myTurn = computed(
  () =>
    !games.view?.complete &&
    poker.value?.currentPlayerId === user.credentials?.playerId &&
    me.value?.status === 'active',
)
const callCost = computed(() => (poker.value?.baseStake ?? 1) * (me.value?.seen ? 2 : 1))
const raiseCost = computed(() => ((poker.value?.baseStake ?? 1) + 1) * (me.value?.seen ? 2 : 1))
const opponents = computed(
  () =>
    poker.value?.seats.filter(
      (s) => s.status === 'active' && s.playerId !== user.credentials?.playerId,
    ) ?? [],
)
const statusLabels = { active: '在场', folded: '已弃牌', lost: '比牌出局', winner: '本局赢家' }
const player = (id?: string) => room.room?.players.find((p) => p.playerId === id)
const name = (id?: string) => player(id)?.nickname ?? '玩家'
watch(
  () => poker.value?.turnNumber,
  () => {
    choosingOpponent.value = false
  },
)

async function move(action: string, targetPlayerId?: string) {
  const ok = await act(action, {
    turnNumber: poker.value?.turnNumber,
    ...(targetPlayerId ? { targetPlayerId } : {}),
  })
  if (ok) choosingOpponent.value = false
}
function describe(move: PokerView['moves'][number]) {
  const actor = name(move.playerId)
  switch (move.action) {
    case 'LOOK':
      return `${actor} 看了牌`
    case 'CALL':
      return `${actor} 跟注 ${move.points} 分`
    case 'RAISE':
      return `${actor} 加注 ${move.points} 分`
    case 'FOLD':
      return `${actor} 弃牌`
    case 'TIMEOUT':
      return `${actor} 超时，自动弃牌`
    case 'COMPARE':
      return `${actor} 与 ${name(move.targetId)} 比牌，${name(move.loserId)} 出局`
    default:
      return ''
  }
}
</script>
<template>
  <div v-if="poker && games.view" class="poker-game">
    <div class="poker-intro">
      <span class="question-label">
        {{ poker.drinkMode ? '酒局模式 · 输家 1 小口' : '聚会模式 · 输家接受轻松挑战' }}
      </span>
      <h2>
        {{
          games.view.complete ? `${name(poker.winnerId)}，这局你赢了！` : '牌可以小，气势不能少。'
        }}
      </h2>
      <p>
        {{ poker.special235 ? '235 吃豹子已开启' : '235 按普通散牌' }} ·
        {{ games.view.participants.length }} 人牌局
      </p>
    </div>

    <div class="poker-table">
      <div class="poker-table-top">
        <div>
          <span>本局积分池</span>
          <strong>
            {{ poker.pot }}
            <small>分</small>
          </strong>
        </div>
        <div class="poker-stake">
          <span>当前底分</span>
          <b>{{ poker.baseStake }} / {{ poker.maxStake }}</b>
        </div>
      </div>
      <div class="poker-seats">
        <article
          v-for="seat in poker.seats"
          :key="seat.playerId"
          class="poker-seat"
          :class="{
            current: !games.view.complete && seat.playerId === poker.currentPlayerId,
            out: seat.status === 'lost' || seat.status === 'folded',
            winner: seat.status === 'winner',
          }"
        >
          <div class="poker-seat-name">
            <span>{{ player(seat.playerId)?.avatar }}</span>
            <strong>{{ name(seat.playerId) }}</strong>
            <small v-if="seat.playerId === user.credentials?.playerId">我</small>
          </div>
          <PlayingCards :cards="seat.cards" compact />
          <p>
            <Trophy v-if="seat.status === 'winner'" :size="12" />
            {{
              seat.handType ??
              (seat.status === 'active'
                ? seat.seen
                  ? '已看牌'
                  : '闷牌中'
                : statusLabels[seat.status])
            }}
          </p>
          <small v-if="games.view.complete">
            {{ seat.netPoints! > 0 ? '+' : '' }}{{ seat.netPoints }} 分 ·
            {{ seat.status === 'winner' ? '赢啦' : poker.drinkMode ? '1 小口' : '轻松挑战' }}
          </small>
          <small v-else>
            {{
              !games.view.complete && seat.playerId === poker.currentPlayerId ? '正在行动 · ' : ''
            }}已投入 {{ seat.contribution }} 分
          </small>
        </article>
      </div>
      <div class="poker-turn" role="status">
        <template v-if="games.view.complete">
          <Trophy :size="17" />
          本局结束，碰个杯吧
        </template>
        <template v-else>
          <span class="live-dot" />
          {{ myTurn ? '轮到你了' : `等待 ${name(poker.currentPlayerId)} 行动` }}
          <small>
            {{ poker.compareOnly ? '决胜阶段 · 比牌或弃牌' : `第 ${poker.turnNumber} 次行动` }}
          </small>
        </template>
      </div>
    </div>

    <div v-if="me && (!games.view.complete || me.status === 'folded')" class="poker-hand">
      <div class="poker-hand-label">
        <span>
          {{ games.view.complete ? '你弃掉的底牌' : '你的底牌' }}
          <small>仅自己可见</small>
        </span>
        <button v-if="poker.myCards" class="text-button" @click="hidden = !hidden">
          <EyeOff v-if="!hidden" :size="14" />
          <Eye v-else :size="14" />
          {{ hidden ? '显示手牌' : '收起手牌' }}
        </button>
      </div>
      <PlayingCards :cards="hidden ? undefined : poker.myCards" />
      <p v-if="poker.myHandType && !hidden" class="poker-hand-type">{{ poker.myHandType }}</p>
      <button
        v-if="!me.seen && me.status === 'active'"
        class="button secondary poker-look"
        :disabled="busy"
        @click="move('LOOK')"
      >
        <Eye :size="17" />
        看牌
        <small>之后跟注按底分 ×2</small>
      </button>
      <p v-else class="poker-help">
        {{
          games.view.complete
            ? '这三张弃牌仍然只有你能看见。'
            : me.status === 'active'
              ? '看过的牌可以收起，跟注倍数仍为 ×2。'
              : '本局已出局，等大家决出赢家。'
        }}
      </p>
    </div>

    <div v-if="me?.status === 'active' && !games.view.complete" class="poker-controls">
      <p v-if="poker.compareOnly" class="poker-decision-note">
        已到行动上限，接下来只可比牌或弃牌。
      </p>
      <div class="poker-action-grid">
        <button
          class="button primary"
          :disabled="busy || !myTurn || poker.compareOnly"
          @click="move('CALL')"
        >
          {{ me.seen ? '跟注' : '闷牌跟注' }}
          <small>{{ callCost }} 分</small>
        </button>
        <button
          class="button secondary"
          :disabled="busy || !myTurn || poker.compareOnly || poker.baseStake >= poker.maxStake"
          @click="move('RAISE')"
        >
          <ArrowUp :size="16" />
          {{ poker.baseStake >= poker.maxStake ? '加注已封顶' : '加注' }}
          <small v-if="poker.baseStake < poker.maxStake">{{ raiseCost }} 分</small>
        </button>
        <button
          class="button secondary"
          :disabled="busy || !myTurn"
          @click="choosingOpponent = !choosingOpponent"
        >
          <Swords :size="16" />
          比牌
          <small>{{ callCost * 2 }} 分</small>
        </button>
        <button class="button ghost" :disabled="busy || !myTurn" @click="move('FOLD')">
          <X :size="16" />
          弃牌
        </button>
      </div>
      <div v-if="choosingOpponent && myTurn" class="poker-opponents">
        <strong>选一位对手 · 输的人出局</strong>
        <button
          v-for="opponent in opponents"
          :key="opponent.playerId"
          class="button secondary full"
          :disabled="busy"
          @click="move('COMPARE', opponent.playerId)"
        >
          {{ player(opponent.playerId)?.avatar }} 和 {{ name(opponent.playerId) }} 比牌
        </button>
        <small>平牌时，发起比牌的人输。</small>
      </div>
      <p class="poker-help">
        {{
          myTurn
            ? '每次行动 30 秒，超时自动弃牌。看牌不会延长时间。'
            : '还没轮到你，可以先看牌，也可以继续闷着。'
        }}
      </p>
    </div>
    <p v-else-if="!me && !games.view.complete" class="poker-help">
      你正在观战，下一局在线即可加入。
    </p>

    <div v-if="games.view.complete" class="poker-settlement">
      <Wine :size="23" />
      <div>
        <strong>{{ poker.drinkMode ? '每位输家，1 小口就好' : '每位输家，来个轻松挑战' }}</strong>
        <p>
          {{
            poker.drinkMode
              ? '弃牌、超时和比牌出局均算输。可换饮料或跳过。'
              : '按房间设置完成自选挑战，也可以跳过。'
          }}
        </p>
        <small>积分每局重置，不叠加饮酒量。</small>
      </div>
    </div>
    <div v-if="poker.moves.length" class="poker-history" aria-live="polite">
      <span>牌桌动态</span>
      <p v-for="(entry, index) in poker.moves.slice(-5).reverse()" :key="index">
        {{ describe(entry) }}
      </p>
    </div>
    <details class="poker-rules">
      <summary>第一次玩？看看牌型和本桌规则</summary>
      <div class="poker-rankings">
        <span>
          豹子
          <b>AAA</b>
        </span>
        <i>›</i>
        <span>
          顺金
          <b>♠QKA</b>
        </span>
        <i>›</i>
        <span>
          金花
          <b>♥258</b>
        </span>
        <i>›</i>
        <span>
          顺子
          <b>456</b>
        </span>
        <i>›</i>
        <span>
          对子
          <b>QQ8</b>
        </span>
        <i>›</i>
        <span>
          散牌
          <b>KA8</b>
        </span>
      </div>
      <ul>
        <li>一副 52 张牌，每人 3 张，支持 2–17 人。每局轮换先手，按玩家列表轮流行动。</li>
        <li>
          同牌型逐张比点数，对子先比对子再比单张；A 最大，花色不分高低。顺子 QKA 最大、A23 最小，KA2
          不算顺子。
        </li>
        <li>
          每人底注 1 分，底分从 1 开始，加注每次将底分加 1，最高
          5。未看牌跟注为底分，看过牌翻倍，比牌为本人跟注的 2 倍。积分每局重置，只用于牌局。
        </li>
        <li>
          随时可看牌；轮到自己可跟注、加注、选对手比牌或弃牌。比牌平手时发起者输，只公布输赢，直到剩一人获胜。
        </li>
        <li>
          每次行动限时 30 秒，超时弃牌；跟注、加注、比牌、弃牌合计达到开局人数 ×5
          次后，进入只能比牌或弃牌的决胜阶段。
        </li>
        <li>
          {{
            poker.special235
              ? '已开启：非同花 235 在一对一比牌中胜任意豹子，其他情况按普通散牌比较；同花 235 仍为金花。'
              : '本桌不开启 235 吃豹子，非同花 235 为普通散牌；房主可在大厅设置切换。'
          }}
        </li>
        <li>
          本局结束公开未弃牌者的手牌，弃牌者的牌继续保密。{{
            poker.drinkMode
              ? '除赢家外每人喝 1 小口，可换饮料或跳过。'
              : '输家按房间设置接受轻松挑战。'
          }}
        </li>
      </ul>
    </details>
  </div>
</template>
<style scoped>
.poker-game {
  text-align: left;
}
.poker-intro {
  text-align: center;
  margin-bottom: 24px;
}
.poker-intro h2 {
  font-size: clamp(20px, 3vw, 28px);
  margin: 12px 0 9px;
  overflow-wrap: anywhere;
}
.poker-intro > p {
  color: #aaa88c;
  font-size: 12px;
}
.poker-table {
  background: radial-gradient(ellipse at top, #304d3e, #1b3027);
  border: 1px solid #7a7950;
  border-radius: 20px;
  padding: 18px;
  box-shadow: inset 0 0 0 5px #23392e;
}
.poker-table-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 5px 18px;
}
.poker-table-top span {
  display: block;
  color: #c0c6ab;
  font-size: 11px;
}
.poker-table-top strong {
  display: block;
  font-size: 32px;
  color: #f5db91;
  margin-top: 3px;
  font-variant-numeric: tabular-nums;
}
.poker-table-top strong small {
  margin-left: 7px;
  font-size: 12px;
  font-weight: 400;
}
.poker-stake {
  text-align: right;
}
.poker-stake b {
  display: block;
  margin-top: 8px;
  font-size: 17px;
  color: #e6d9b5;
}
.poker-seats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(145px, 1fr));
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
  padding: 2px;
  scrollbar-width: thin;
  scrollbar-color: #7a7950 transparent;
}
.poker-seat {
  min-width: 0;
  padding: 12px 6px;
  text-align: center;
  background: #15271ed9;
  border: 1px solid #475642;
  border-radius: 12px;
}
.poker-seat.current,
.poker-seat.winner {
  border-color: #edd289;
  background: #394732;
  box-shadow: 0 0 0 1px #edd28940;
}
.poker-seat.out {
  background: #17291f;
}
.poker-seat-name {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 12px;
  min-width: 0;
}
.poker-seat-name > span {
  font-size: 21px;
  flex-shrink: 0;
}
.poker-seat-name > strong {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.poker-seat-name > small {
  color: #e9cb7c;
  font-size: 10px;
  flex-shrink: 0;
}
.poker-seat > p {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #e9d79d;
  font-size: 12px;
  margin: 6px 0;
}
.poker-seat > small {
  color: #b7bfaa;
  font-size: 10px;
}
.poker-turn {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
  color: #f0dcac;
  padding: 18px 0 2px;
  text-align: center;
  overflow-wrap: anywhere;
}
.poker-turn small {
  color: #bec5b0;
  font-size: 11px;
}
.poker-hand {
  text-align: center;
  padding: 22px 0 8px;
}
.poker-hand-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 7px;
}
.poker-hand-label small {
  color: #a9ac96;
  font-size: 10px;
  margin-left: 6px;
}
.poker-hand-label button {
  color: #d6c690;
  font-size: 11px;
}
.poker-look {
  margin: 8px auto 2px;
  min-height: 44px;
}
.poker-look small,
.poker-action-grid small {
  font-size: 11px;
  font-weight: 400;
}
.poker-hand-type {
  color: #f0d28e;
  margin: 8px 0;
  font-weight: 650;
}
.poker-help {
  text-align: center;
  color: #aeb09f;
  font-size: 11px;
  line-height: 1.8;
  margin: 10px 0;
}
.poker-action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
  margin-top: 13px;
}
.poker-action-grid .button {
  padding: 12px 8px;
  min-height: 48px;
  gap: 6px;
  font-size: 13px;
}
.poker-action-grid .primary {
  background: #efd38d;
  border-color: #efd38d;
  color: #2d3323;
}
.poker-action-grid .secondary {
  border-color: #7d7754;
}
.poker-decision-note {
  padding: 10px;
  text-align: center;
  color: #f0d28e;
  font-size: 12px;
}
.poker-opponents {
  display: grid;
  gap: 9px;
  border: 1px solid #85764a;
  border-radius: 12px;
  background: #2b3022;
  margin-top: 14px;
  padding: 14px;
}
.poker-opponents strong {
  font-size: 13px;
}
.poker-opponents small {
  color: #bab7a1;
  font-size: 11px;
}
.poker-opponents button {
  white-space: normal;
  overflow-wrap: anywhere;
}
.poker-history {
  border-top: 1px solid #454832;
  margin-top: 22px;
  padding-top: 15px;
  overflow-wrap: anywhere;
}
.poker-history > span {
  font-size: 10px;
  color: #9ea58a;
}
.poker-history p {
  font-size: 12px;
  color: #babfac;
  margin: 9px 0;
  line-height: 1.6;
}
.poker-history p:first-of-type {
  color: #ebd69f;
}
.poker-settlement {
  display: flex;
  gap: 12px;
  background: #3b3927;
  border: 1px solid #8e7a4c;
  border-radius: 13px;
  padding: 18px 14px;
  margin-top: 20px;
  color: #efd795;
}
.poker-settlement > svg {
  flex-shrink: 0;
}
.poker-settlement strong {
  font-size: 15px;
}
.poker-settlement p {
  font-size: 12px;
  line-height: 1.7;
  color: #d1c7a7;
  margin: 7px 0;
}
.poker-settlement small {
  font-size: 11px;
  color: #b8b398;
}
.poker-rules {
  border-top: 1px solid #454832;
  margin-top: 18px;
  padding-top: 17px;
  font-size: 12px;
}
.poker-rules summary {
  cursor: pointer;
  color: #d5c89e;
  line-height: 1.8;
}
.poker-rules ul {
  padding-left: 18px;
  color: #b5baa7;
  line-height: 1.9;
}
.poker-rules li {
  margin-top: 8px;
}
.poker-rankings {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
  font-size: 11px;
}
.poker-rankings span {
  text-align: center;
  color: #e4d09a;
}
.poker-rankings b {
  display: block;
  font-size: 10px;
  color: #b5baa7;
  margin-top: 5px;
}
.poker-rankings i {
  color: #8b9476;
}
@media (max-width: 540px) {
  .poker-table {
    padding: 13px 10px;
  }
  .poker-seats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 7px;
  }
  .poker-seat {
    padding: 10px 3px;
  }
  .poker-intro {
    margin-bottom: 18px;
  }
}
</style>
