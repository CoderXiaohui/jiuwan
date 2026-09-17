<script setup lang="ts">
import { useId } from 'vue'
const id = `bird-${useId()}`
</script>

<template>
  <svg class="bird-art" viewBox="0 0 240 240" fill="none" aria-hidden="true">
    <defs>
      <radialGradient :id="`${id}-body`" cx=".38" cy=".3" r=".75">
        <stop stop-color="#fff576" />
        <stop offset=".65" stop-color="#ffe740" />
        <stop offset="1" stop-color="#ffd32e" />
      </radialGradient>
      <linearGradient
        :id="`${id}-beak`"
        x1="120"
        y1="133"
        x2="120"
        y2="181"
        gradientUnits="userSpaceOnUse"
      >
        <stop stop-color="#ffb62a" />
        <stop offset="1" stop-color="#ff8c16" />
      </linearGradient>
    </defs>
    <path d="M103 53C77 28 92 9 109 31C110-8 146-8 134 28C163 5 177 34 143 51" fill="#ffe745" />
    <g class="bird-wings" fill="#ffe443">
      <path
        d="M42 119C20 125 7 149 8 160C9 165 21 154 24 151C13 176 23 183 36 162C29 187 46 188 54 159Z"
      />
      <path
        d="M198 119C220 125 233 149 232 160C231 165 219 154 216 151C227 176 217 183 204 162C211 187 194 188 186 159Z"
      />
    </g>
    <path
      d="M29 134C29 78 69 42 120 42C171 42 211 78 211 134C211 191 172 224 120 224C68 224 29 191 29 134Z"
      :fill="`url(#${id}-body)`"
    />
    <ellipse cx="72" cy="124" rx="14" ry="18" fill="#482522" />
    <ellipse cx="168" cy="124" rx="14" ry="18" fill="#482522" />
    <ellipse cx="76" cy="119" rx="5" ry="6" fill="white" />
    <ellipse cx="172" cy="119" rx="5" ry="6" fill="white" />
    <ellipse cx="60" cy="157" rx="12" ry="10" fill="#ff9584" />
    <ellipse cx="180" cy="157" rx="12" ry="10" fill="#ff9584" />
    <path d="M95 143Q120 122 145 143Q138 169 120 181Q102 169 95 143Z" :fill="`url(#${id}-beak)`" />
    <path d="M101 145Q120 153 139 145Q134 165 120 169Q107 165 101 145Z" fill="#8d3c18" />
    <path d="M109 163Q120 154 131 163Q120 177 109 163Z" fill="#ef6d25" />
  </svg>
</template>

<style scoped>
.bird-art {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}
</style>
