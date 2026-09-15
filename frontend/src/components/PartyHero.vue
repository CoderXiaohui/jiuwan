<script setup lang="ts">
import { Sparkles, ArrowUpRight, Smile, Zap } from 'lucide-vue-next'
</script>
<template>
  <div class="party-hero" aria-hidden="true">
    <div class="orbit orbit-one"></div>
    <div class="orbit orbit-two"></div>
    <div class="hero-sticker">
      <span>GOOD FRIENDS.</span>
      <span>GREAT NIGHTS.</span>
      <ArrowUpRight :size="19" />
    </div>
    <div class="hero-card card-purple">
      <span>LET'S</span>
      <strong>PLAY.</strong>
      <Smile :size="74" :stroke-width="1.2" />
      <small>聚在一起，就有好戏</small>
    </div>
    <div class="hero-die">
      <div class="die-dots">
        <i />
        <i />
        <i />
        <i />
        <i />
      </div>
      <span>LUCK IS ON YOUR SIDE</span>
    </div>
    <span class="hero-star star-one">✳</span>
    <span class="hero-star star-two">✦</span>
    <div class="hero-bubble">
      <Sparkles :size="19" />
      快乐已就位
    </div>
    <div class="hero-caption">
      <Zap :size="13" />
      NO DOWNLOAD. JUST PLAY.
    </div>
  </div>
</template>
