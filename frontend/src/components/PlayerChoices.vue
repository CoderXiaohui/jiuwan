<script setup lang="ts">
import { computed } from 'vue'
import { Check } from 'lucide-vue-next'
import { useRoomStore } from '../stores/room'
import { useGameStore } from '../stores/game'
import { useUserStore } from '../stores/user'
const props = defineProps<{ disabled?: boolean; selected?: string; ids?: string[] }>()
const emit = defineEmits<{ choose: [id: string] }>()
const room = useRoomStore(),
  game = useGameStore(),
  user = useUserStore()
const players = computed(
  () =>
    room.room?.players.filter((p) =>
      (props.ids ?? game.view?.participants ?? []).includes(p.playerId),
    ) ?? [],
)
</script>
<template>
  <div class="player-choices">
    <button
      v-for="player in players"
      :key="player.playerId"
      class="player-choice"
      :class="{ chosen: selected === player.playerId }"
      :disabled="disabled"
      @click="emit('choose', player.playerId)"
    >
      <span class="choice-avatar">{{ player.avatar }}</span>
      <span>
        {{ player.nickname }}
        <small v-if="player.playerId === user.credentials?.playerId">（我）</small>
      </span>
      <Check v-if="selected === player.playerId" :size="17" />
      <span v-else class="choice-radio"></span>
    </button>
  </div>
</template>
