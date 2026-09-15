<script setup lang="ts">
import { computed } from 'vue'
import { LockKeyhole, Check, Trophy } from 'lucide-vue-next'
import { useGameStore } from '../../stores/game'
import { useRoomStore } from '../../stores/room'
import { useAction } from '../../lib/useAction'
import PlayerChoices from '../../components/PlayerChoices.vue'
const games = useGameStore(),
  room = useRoomStore(),
  { busy, act } = useAction()
const results = computed(
  () =>
    room.room?.players
      .filter((p) => games.view?.participants.includes(p.playerId))
      .map((p) => ({ ...p, votes: games.view?.counts?.[p.playerId] ?? 0 }))
      .sort((a, b) => b.votes - a.votes) ?? [],
)
</script>
<template>
  <div v-if="games.view" class="vote-game">
    <div class="question-label">凭直觉，把这一票交给 TA</div>
    <h2 class="game-question">{{ games.view.question }}</h2>
    <template v-if="!games.view.complete">
      <p class="secret-note">
        <LockKeyhole :size="14" />
        揭晓前，只有你知道自己的选择
      </p>
      <PlayerChoices
        :disabled="busy || games.view.hasActed"
        :selected="games.view.myChoice"
        @choose="(id) => act('VOTE', { targetPlayerId: id })"
      />
      <p v-if="games.view.hasActed" class="submitted-note">
        <Check :size="18" />
        你的选择已锁定，等待大家投票
      </p>
    </template>
    <div v-else class="results-list">
      <h3>
        <Trophy :size="20" />
        大家的选择，揭晓！
      </h3>
      <div
        v-for="(player, index) in results"
        :key="player.playerId"
        class="result-row"
        :class="{ winner: games.view.selectedIds?.includes(player.playerId) }"
        :style="{ '--order': index }"
      >
        <span class="result-avatar">{{ player.avatar }}</span>
        <div class="result-person">
          <strong>{{ player.nickname }}</strong>
          <div class="vote-bar">
            <i
              :style="{
                width: `${(player.votes / Math.max(1, games.view.submittedCount)) * 100}%`,
              }"
            />
          </div>
        </div>
        <span class="result-value">
          {{ player.votes }}
          <small>票</small>
        </span>
      </div>
      <p v-if="games.view.timedOut" class="muted text-center">倒计时结束，已按收到的投票结算。</p>
      <div v-if="games.view.ballots" class="ballots">
        <p v-for="(target, voter) in games.view.ballots" :key="voter">
          {{ room.room?.players.find((p) => p.playerId === voter)?.nickname }} →
          {{ room.room?.players.find((p) => p.playerId === target)?.nickname }}
        </p>
      </div>
    </div>
  </div>
</template>
