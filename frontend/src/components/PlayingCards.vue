<script setup lang="ts">
import type { PokerCard } from '../lib/types'
withDefaults(defineProps<{ cards?: PokerCard[]; compact?: boolean; count?: number }>(), {
  count: 3,
})
const suits = { spades: '♠', hearts: '♥', clubs: '♣', diamonds: '♦' }
const ranks: Record<number, string> = { 11: 'J', 12: 'Q', 13: 'K', 14: 'A' }
</script>
<template>
  <div
    class="poker-cards"
    :class="{ compact }"
    :aria-label="cards ? `已亮出的 ${cards.length} 张牌` : `${count} 张暗牌`"
  >
    <template v-if="cards">
      <div
        v-for="(card, index) in cards"
        :key="index"
        class="poker-card face"
        :class="{ red: card.suit === 'hearts' || card.suit === 'diamonds' }"
        :aria-label="`${suits[card.suit]}${ranks[card.rank] ?? card.rank}`"
      >
        <span class="card-rank">
          {{ ranks[card.rank] ?? card.rank }}
          <small>{{ suits[card.suit] }}</small>
        </span>
        <b>{{ suits[card.suit] }}</b>
        <span class="card-rank bottom">
          {{ ranks[card.rank] ?? card.rank }}
          <small>{{ suits[card.suit] }}</small>
        </span>
      </div>
    </template>
    <template v-else>
      <div v-for="n in count" :key="n" class="poker-card card-back"><span>✦</span></div>
    </template>
  </div>
</template>
<style scoped>
.poker-cards {
  display: flex;
  justify-content: center;
  gap: 9px;
  padding: 12px 0;
}
.poker-card {
  position: relative;
  width: 78px;
  height: 111px;
  border-radius: 9px;
  box-shadow: 0 5px 12px #0003;
  display: grid;
  place-items: center;
}
.poker-card:first-child {
  transform: rotate(-6deg) translateY(3px);
}
.poker-card:last-child {
  transform: rotate(6deg) translateY(3px);
}
.poker-card:only-child {
  transform: none;
}
.face {
  background: #fff5de;
  color: #293e33;
  border: 1px solid #f5e4bb;
}
.face.red {
  color: #b24c44;
}
.face b {
  font-size: 37px;
}
.card-rank {
  position: absolute;
  top: 5px;
  left: 7px;
  font:
    700 17px/1 Georgia,
    serif;
  text-align: center;
}
.card-rank small {
  display: block;
  font-size: 13px;
  margin-top: 2px;
}
.card-rank.bottom {
  top: auto;
  left: auto;
  bottom: 5px;
  right: 7px;
  transform: rotate(180deg);
}
.card-back {
  border: 1px solid #9e905c;
  color: #ead394;
  background: repeating-linear-gradient(45deg, #354b3e 0 5px, #304437 5px 7px);
}
.card-back::before {
  content: '';
  position: absolute;
  inset: 5px;
  border: 1px solid #8f8856;
  border-radius: 4px;
}
.card-back span {
  font-size: 35px;
}
.compact {
  gap: 4px;
  padding: 7px 0;
}
.compact .poker-card {
  width: 34px;
  height: 50px;
  border-radius: 4px;
}
.compact .card-rank {
  font-size: 12px;
  left: 4px;
  top: 3px;
}
.compact .card-rank small,
.compact .bottom {
  display: none;
}
.compact .face b {
  font-size: 22px;
  margin-top: 7px;
}
.compact .card-back span {
  font-size: 22px;
}
</style>
