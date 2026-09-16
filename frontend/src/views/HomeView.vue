<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ArrowUpRight,
  Plus,
  Hash,
  Users,
  Smartphone,
  ShieldCheck,
  Sparkles,
  ChevronRight,
} from 'lucide-vue-next'
import PartyHero from '../components/PartyHero.vue'
import GameArt from '../components/GameArt.vue'
import AppDialog from '../components/AppDialog.vue'
import { catalog } from '../games/catalog'
import { api } from '../lib/api'
import type { Credentials, GameMeta } from '../lib/types'
import { useUserStore } from '../stores/user'
import { useWebSocketStore } from '../stores/websocket'
import { useRoomStore } from '../stores/room'
const user = useUserStore(),
  ws = useWebSocketStore(),
  rooms = useRoomStore(),
  router = useRouter()
const busy = ref(false),
  selected = ref<GameMeta | null>(null)
async function createRoom(selectedGameId?: string) {
  if (busy.value) return
  busy.value = true
  try {
    if (user.credentials) {
      if (selectedGameId && rooms.isOwner && rooms.room?.status !== 'PLAYING')
        await ws.send('SELECT_GAME', {}, selectedGameId)
      await router.push(`/room/${user.credentials.roomCode}`)
      return
    }
    const session = await api<Credentials>('/rooms', {
      nickname: user.nickname || '派对发起人',
      avatar: user.avatar,
      selectedGameId,
    })
    user.save(session)
    await router.push('/profile')
  } catch (e) {
    ws.error = e instanceof Error ? e.message : '操作失败，请重试'
  } finally {
    busy.value = false
  }
}
async function playSelected() {
  const gameId = selected.value?.id
  selected.value = null
  await createRoom(gameId)
}
</script>
<template>
  <main class="home-main">
    <section class="hero-section">
      <div class="hero-copy">
        <div class="eyebrow">
          <span class="live-dot"></span>
          YOUR NEXT GREAT NIGHT STARTS HERE
        </div>
        <h1>
          今晚，
          <br />
          玩点
          <span class="highlight">
            不一样的
            <svg viewBox="0 0 370 15" preserveAspectRatio="none">
              <path d="M3 10Q164 -2 365 6M24 13Q195 4 341 10" />
            </svg>
          </span>
          <span class="title-period">。</span>
        </h1>
        <p class="hero-description">
          放下手机里的各自精彩，打开属于我们的好时光。
          <br class="desktop-only" />
          一个房间，一群朋友，无限种快乐。
        </p>
        <div class="hero-actions">
          <button class="button primary" :disabled="busy" @click="createRoom()">
            <Plus :size="21" />
            {{ busy ? '正在创建…' : user.credentials ? '回到我的房间' : '创建房间' }}
            <ArrowUpRight :size="19" />
          </button>
          <RouterLink to="/join" class="button secondary">
            <Hash :size="21" />
            加入房间
            <ArrowRight :size="18" />
          </RouterLink>
        </div>
        <div class="hero-benefits">
          <span>
            <Smartphone :size="15" />
            无需下载
          </span>
          <i />
          <span>
            <Users :size="16" />
            2–20 人同玩
          </span>
          <i />
          <span>
            <ShieldCheck :size="16" />
            免费开局
          </span>
        </div>
      </div>
      <PartyHero />
    </section>
    <div class="scene-strip">
      <span class="scene-caption">好玩的，不挑场合</span>
      <div>
        <span>朋友聚会</span>
        <i>✦</i>
        <span>KTV 嗨玩</span>
        <i>✦</i>
        <span>饭后时光</span>
        <i>✦</i>
        <span>团建破冰</span>
        <i>✦</i>
        <span>宿舍夜聊</span>
      </div>
      <Sparkles :size="19" />
    </div>
    <section id="games" class="games-section">
      <div class="section-heading">
        <div>
          <div class="eyebrow subtle">PICK YOUR FUN</div>
          <h2>
            快乐，不止一种玩法
            <span>✳</span>
          </h2>
        </div>
        <span class="section-aside">
          选个喜欢的，气氛交给我们
          <ArrowUpRight :size="16" />
        </span>
      </div>
      <div class="game-card-grid">
        <button
          v-for="(game, index) in catalog"
          :key="game.id"
          class="game-card"
          :class="game.color"
          @click="selected = game"
        >
          <div class="game-card-top">
            <span>{{ game.tag }}</span>
            <small>0{{ index + 1 }}</small>
          </div>
          <GameArt :kind="game.icon" />
          <div class="game-card-copy">
            <small>{{ game.english }}</small>
            <h3>{{ game.name }}</h3>
            <p>{{ game.description }}</p>
          </div>
          <div class="game-card-bottom">
            <span>
              <Users :size="13" />
              2–{{ game.maxPlayers ?? 20 }} 人
              <span class="dot-separator">·</span>
              {{ game.duration }}
            </span>
            <span class="card-arrow"><ArrowUpRight :size="17" /></span>
          </div>
        </button>
      </div>
    </section>
    <section id="how" class="how-section">
      <div class="how-intro">
        <span class="eyebrow subtle">LESS SETUP, MORE PLAY</span>
        <h2>
          三步到位，
          <br />
          快乐开局。
        </h2>
        <p>
          不注册，不下载。
          <br />
          把时间留给身边的朋友。
        </p>
      </div>
      <div class="how-steps">
        <article>
          <span class="step-number">01</span>
          <div class="step-icon"><Plus :size="22" /></div>
          <h3>开个房间</h3>
          <p>一键创建，拿到专属房间码。</p>
        </article>
        <ChevronRight class="step-chevron" :size="20" />
        <article>
          <span class="step-number">02</span>
          <div class="step-icon"><Users :size="22" /></div>
          <h3>喊上朋友</h3>
          <p>输入 6 位房间码，好友就位。</p>
        </article>
        <ChevronRight class="step-chevron" :size="20" />
        <article>
          <span class="step-number">03</span>
          <div class="step-icon"><Sparkles :size="22" /></div>
          <h3>尽情开玩</h3>
          <p>选个游戏，一起制造快乐。</p>
        </article>
      </div>
    </section>
    <section class="good-vibes">
      <div class="vibes-icon"><ShieldCheck :size="25" /></div>
      <div>
        <h3>快乐有很多种，舒服最重要。</h3>
        <p>不劝酒、不勉强。随时跳过挑战，每个人都能自在参与。</p>
      </div>
      <span>
        GOOD VIBES ONLY
        <Sparkles :size="17" />
      </span>
    </section>
    <AppDialog :open="!!selected" :title="selected?.name ?? '玩法介绍'" @close="selected = null">
      <template v-if="selected">
        <div class="dialog-art" :class="selected.color"><GameArt :kind="selected.icon" /></div>
        <p class="rule-text">{{ selected.rule }}</p>
        <div class="rule-meta">
          <span>2–20 人同玩</span>
          <span>{{ selected.duration }} / 轮</span>
        </div>
        <button class="button primary full" :disabled="busy" @click="playSelected">
          就玩这个
          <ArrowRight :size="18" />
        </button>
      </template>
    </AppDialog>
  </main>
</template>
