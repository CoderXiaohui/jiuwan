<script setup lang="ts">
import { Heart, MessageCircle, Sparkles, Zap, ArrowUpRight, Check } from 'lucide-vue-next'
import BirdArt from './BirdArt.vue'
defineProps<{ kind: string }>()
</script>
<template>
  <div class="game-art" :class="`art-${kind}`" aria-hidden="true">
    <template v-if="kind === 'bird'">
      <div class="bird-card-art"><BirdArt /></div>
      <i class="art-spark">♡</i>
    </template>
    <template v-else-if="kind === 'poker'">
      <div class="mini-card poker-left">
        <span>A</span>
        ♠
        <span>A</span>
      </div>
      <div class="mini-card poker-middle">
        <span>A</span>
        ♥
        <span>A</span>
      </div>
      <div class="mini-card poker-right">
        <span>A</span>
        ♣
        <span>A</span>
      </div>
      <i class="art-spark">✦</i>
    </template>
    <template v-else-if="kind === 'vote'">
      <div class="vote-bubble">
        <MessageCircle :size="58" :stroke-width="1.3" />
        <span>?</span>
      </div>
      <div class="vote-chip">
        <Check :size="19" />
        +1
      </div>
      <i class="art-spark">✦</i>
    </template>
    <template v-else-if="kind === 'cards'">
      <div class="mini-card back"><Zap :size="38" fill="currentColor" /></div>
      <div class="mini-card front">
        <span>Q</span>
        <Heart :size="34" fill="currentColor" />
        <span>Q</span>
      </div>
      <i class="art-spark">✧</i>
    </template>
    <template v-else-if="kind === 'wheel'">
      <div class="mini-wheel"><span>✦</span></div>
      <div class="wheel-pointer-small"></div>
      <i class="art-spark">✷</i>
    </template>
    <template v-else-if="kind === 'dice'">
      <div class="art-die die-back">
        <i />
        <i />
        <i />
      </div>
      <div class="art-die die-front">
        <i />
        <i />
        <i />
        <i />
        <i />
      </div>
      <i class="art-spark">✧</i>
    </template>
    <template v-else>
      <div class="heart-bubble one"><Heart :size="45" fill="currentColor" /></div>
      <div class="heart-bubble two"><Sparkles :size="39" /></div>
      <i class="art-spark">♡</i>
    </template>
    <ArrowUpRight class="art-corner" :size="16" />
  </div>
</template>
