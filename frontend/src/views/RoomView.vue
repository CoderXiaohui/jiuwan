<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  Copy,
  Check,
  Crown,
  Users,
  Settings2,
  Link,
  X,
  LogOut,
  ShieldCheck,
  Plus,
  Play,
  QrCode,
} from 'lucide-vue-next'
import { useUserStore } from '../stores/user'
import { useRoomStore } from '../stores/room'
import { useGameStore } from '../stores/game'
import { useWebSocketStore } from '../stores/websocket'
import { catalog } from '../games/catalog'
import type { RoomSettings } from '../lib/types'
import { useAction } from '../lib/useAction'
import GameArt from '../components/GameArt.vue'
import GameStage from '../components/GameStage.vue'
import AppDialog from '../components/AppDialog.vue'
const user = useUserStore(),
  rooms = useRoomStore(),
  games = useGameStore(),
  ws = useWebSocketStore(),
  route = useRoute(),
  router = useRouter()
const { busy, act } = useAction()
const settingsOpen = ref(false),
  inviteOpen = ref(false),
  confirmAction = ref<'leave' | 'close' | 'kick' | null>(null),
  kickId = ref(''),
  copied = ref('')
const draft = ref<RoomSettings>({
  punishmentMode: 'challenge',
  gameMode: 'normal',
  maxPlayers: 12,
  anonymousVote: true,
  safeMode: true,
  diceRule: 'lowest',
  zhaJinHua235: false,
  zhaJinHuaDrink: true,
})
const selected = computed(() => catalog.find((g) => g.id === games.selectedId) ?? catalog[0]!)
const inviteUrl = computed(
  () => `${location.origin}/join?code=${rooms.room?.roomCode ?? route.params.code}`,
)
watch(
  () => route.params.code,
  (code) => {
    if (!user.credentials || user.credentials.roomCode !== code)
      void router.replace({ path: '/join', query: { code: String(code) } })
  },
  { immediate: true },
)
function openSettings() {
  if (rooms.room) draft.value = { ...rooms.room.settings }
  settingsOpen.value = true
}
function requestKick(playerId: string) {
  kickId.value = playerId
  confirmAction.value = 'kick'
}
function requestClose() {
  settingsOpen.value = false
  confirmAction.value = 'close'
}
async function saveSettings() {
  if (await act('UPDATE_SETTINGS', { ...draft.value })) settingsOpen.value = false
}
async function copy(text: string, type: string) {
  try {
    if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(text)
    else {
      const area = document.createElement('textarea')
      area.value = text
      area.style.position = 'fixed'
      area.style.opacity = '0'
      document.body.append(area)
      area.select()
      const ok = document.execCommand('copy')
      area.remove()
      if (!ok) throw new Error()
    }
    copied.value = type
  } catch {
    ws.error = '自动复制不可用，请长按房间码或邀请链接复制'
  }
}
async function confirm() {
  if (confirmAction.value === 'kick') {
    await act('KICK_PLAYER', { targetPlayerId: kickId.value })
    confirmAction.value = null
    return
  }
  const success = await act(confirmAction.value === 'close' ? 'CLOSE_ROOM' : 'LEAVE_ROOM')
  if (success || !user.credentials) {
    ws.error = ''
    ws.disconnect()
    user.clear()
    rooms.clear()
    await router.replace('/')
  }
  confirmAction.value = null
}
</script>
<template>
  <main class="room-page">
    <div class="room-topline">
      <RouterLink to="/" class="back-link">
        <ArrowLeft :size="17" />
        首页
      </RouterLink>
      <div class="room-top-actions">
        <button class="text-button" @click="inviteOpen = true">
          <Link :size="16" />
          邀请朋友
        </button>
        <button class="icon-button" aria-label="退出房间" @click="confirmAction = 'leave'">
          <LogOut :size="18" />
        </button>
      </div>
    </div>
    <template v-if="rooms.room">
      <section class="room-banner">
        <div>
          <span class="eyebrow subtle">OUR LITTLE PARTY</span>
          <h1>
            人已就位，快乐开场
            <span>✦</span>
          </h1>
        </div>
        <button
          class="room-code"
          aria-label="复制房间码"
          @click="copy(rooms.room.roomCode, 'code')"
        >
          <small>房间码</small>
          <strong>{{ rooms.room.roomCode }}</strong>
          <Check v-if="copied === 'code'" :size="18" />
          <Copy v-else :size="18" />
        </button>
      </section>
      <div class="room-layout">
        <aside class="players-panel">
          <div class="panel-title">
            <h2>
              <Users :size="18" />
              派对成员
            </h2>
            <span>{{ rooms.online }} / {{ rooms.room.settings.maxPlayers }}</span>
          </div>
          <TransitionGroup name="players" tag="div" class="player-list">
            <div v-for="player in rooms.room.players" :key="player.playerId" class="lobby-player">
              <span class="player-avatar">
                {{ player.avatar }}
                <i :class="{ online: player.connected }" />
              </span>
              <div>
                <strong>
                  {{ player.nickname }}
                  <small v-if="player.playerId === user.credentials?.playerId">（我）</small>
                </strong>
                <span v-if="player.isOwner" class="owner-label">
                  <Crown :size="11" />
                  房主
                </span>
                <span v-else class="player-status">
                  {{ player.connected ? '准备好一起玩' : '暂时离线，等 TA 回来' }}
                </span>
              </div>
              <button
                v-if="rooms.isOwner && !player.isOwner"
                class="kick-button"
                :aria-label="`移出 ${player.nickname}`"
                @click="requestKick(player.playerId)"
              >
                <X :size="15" />
              </button>
            </div>
          </TransitionGroup>
          <button class="invite-slot" @click="inviteOpen = true">
            <Plus :size="21" />
            <span>留个位置给朋友</span>
          </button>
          <div class="room-setting-summary">
            <ShieldCheck :size="17" />
            <span>
              {{ rooms.room.settings.safeMode ? '安全模式已开启' : '自在参与，量力而行' }}
              <small>任务均可自愿跳过</small>
            </span>
          </div>
          <button
            v-if="rooms.isOwner"
            class="button ghost full settings-button"
            @click="openSettings"
          >
            <Settings2 :size="17" />
            房间设置
          </button>
        </aside>
        <GameStage v-if="rooms.room.status === 'PLAYING' && games.view" />
        <section v-else class="lobby-games">
          <div class="panel-title">
            <div>
              <span class="eyebrow subtle">WHAT'S THE PLAN?</span>
              <h2>{{ rooms.isOwner ? '今晚先玩哪个？' : '好朋友，集合中' }}</h2>
            </div>
            <span class="small-tag">{{ catalog.length }} 种玩法</span>
          </div>
          <div class="lobby-game-list">
            <button
              v-for="game in catalog"
              :key="game.id"
              class="lobby-game"
              :class="[game.color, { selected: games.selectedId === game.id }]"
              :disabled="!rooms.isOwner"
              @click="games.selectedId = game.id"
            >
              <div class="lobby-game-art"><GameArt :kind="game.icon" /></div>
              <div>
                <small>{{ game.english }}</small>
                <h3>{{ game.name }}</h3>
                <p>{{ game.description }}</p>
              </div>
              <div class="selection-circle">
                <Check v-if="games.selectedId === game.id" :size="16" />
              </div>
            </button>
          </div>
          <div class="selected-rule">
            <strong>{{ selected.name }} · 怎么玩</strong>
            <p>{{ selected.rule }}</p>
          </div>
          <div v-if="rooms.isOwner" class="lobby-start">
            <button
              class="button primary full"
              :disabled="
                rooms.online < 2 ||
                rooms.online > (selected.maxPlayers ?? 20) ||
                busy ||
                ws.status !== 'connected'
              "
              @click="act('START_GAME', {}, games.selectedId)"
            >
              <Play :size="19" fill="currentColor" />
              {{ busy ? '准备开场…' : `开始游戏 · ${selected.name}` }}
              <ArrowRight :size="19" />
            </button>
            <p>
              {{
                rooms.online < 2
                  ? '至少需要 2 位在线玩家，快邀请朋友加入吧'
                  : rooms.online > (selected.maxPlayers ?? 20)
                    ? `${selected.name}最多支持 ${selected.maxPlayers} 位在线玩家，请选择其他游戏`
                    : `${rooms.online} 位玩家已在线，随时开玩`
              }}
            </p>
          </div>
          <div v-else class="waiting-box">
            <span class="live-dot" />
            等待房主开始游戏
            <p>朋友到齐，快乐就绪。</p>
          </div>
        </section>
      </div>
    </template>
    <div v-else class="empty-room">
      <span class="loading-orbit">✦</span>
      <h1>{{ user.credentials ? '正在回到你的房间…' : '这场聚会已经结束' }}</h1>
      <p>{{ user.credentials ? '同步玩家与游戏状态' : '回到首页，再开启一场好时光。' }}</p>
      <button
        v-if="user.credentials && ws.status === 'offline'"
        class="button primary"
        @click="ws.reconnect()"
      >
        重新连接
      </button>
      <RouterLink v-if="!user.credentials" to="/" class="button primary">返回首页</RouterLink>
    </div>
    <AppDialog :open="settingsOpen" title="让聚会更合拍" @close="settingsOpen = false">
      <form class="settings-form" @submit.prevent="saveSettings">
        <label>
          真心话题库
          <select v-model="draft.gameMode">
            <option value="normal">普通模式 · 轻松破冰</option>
            <option value="friends">熟人模式 · 朋友专场</option>
            <option value="couple">情侣模式 · 温暖相处</option>
            <option value="mellow">微醺模式 · 放松聊天</option>
          </select>
        </label>
        <label>
          骰子规则
          <select v-model="draft.diceRule">
            <option value="lowest">最小点数接受挑战</option>
            <option value="highest">最大点数接受挑战</option>
          </select>
        </label>
        <label>
          挑战方式
          <select v-model="draft.punishmentMode">
            <option value="challenge">自选轻松挑战</option>
            <option value="truth">分享真心话</option>
            <option value="dare">完成自选小任务</option>
          </select>
        </label>
        <label class="toggle-row">
          <span>
            炸金花 · 酒局模式
            <small>每位输家 1 小口，可换饮料或跳过；关闭后使用上面的挑战方式</small>
          </span>
          <input v-model="draft.zhaJinHuaDrink" type="checkbox" role="switch" />
        </label>
        <label class="toggle-row">
          <span>
            炸金花 · 235 吃豹子
            <small>非同花 235 比牌时胜豹子，其他情况按散牌比较</small>
          </span>
          <input v-model="draft.zhaJinHua235" type="checkbox" role="switch" />
        </label>
        <label>
          房间人数上限
          <input v-model.number="draft.maxPlayers" type="number" min="2" max="20" required />
        </label>
        <label class="toggle-row">
          <span>
            匿名投票
            <small>关闭后，结果页会公开每个人的选择</small>
          </span>
          <input v-model="draft.anonymousVote" type="checkbox" role="switch" />
        </label>
        <label class="toggle-row">
          <span>
            安全模式
            <small>使用轻松、无压力的挑战文案</small>
          </span>
          <input v-model="draft.safeMode" type="checkbox" role="switch" />
        </label>
        <button class="button primary full" :disabled="busy">
          保存设置
          <Check :size="18" />
        </button>
        <button type="button" class="text-button danger full" @click="requestClose">
          关闭这个房间
        </button>
      </form>
    </AppDialog>
    <AppDialog :open="inviteOpen" title="好朋友，一个都不能少" @close="inviteOpen = false">
      <div class="invite-content">
        <div class="invite-code-label">把房间码发给 TA</div>
        <strong class="invite-code">{{ rooms.room?.roomCode }}</strong>
        <p>打开酒玩 → 加入房间 → 输入房间码</p>
        <button class="button primary full" @click="copy(inviteUrl, 'link')">
          <Check v-if="copied === 'link'" :size="18" />
          <Link v-else :size="18" />
          {{ copied === 'link' ? '邀请链接已复制' : '复制邀请链接' }}
        </button>
        <input class="invite-link-input" :value="inviteUrl" readonly aria-label="邀请链接" />
        <span class="qr-note">
          <QrCode :size="16" />
          二维码入口 · 本版使用链接邀请
        </span>
      </div>
    </AppDialog>
    <AppDialog
      :open="!!confirmAction"
      :title="
        confirmAction === 'kick'
          ? '请这位朋友暂时离场？'
          : confirmAction === 'close'
            ? '结束今晚的聚会？'
            : '现在离开房间？'
      "
      @close="confirmAction = null"
    >
      <p class="rule-text">
        {{
          confirmAction === 'close'
            ? '房间将关闭，所有玩家都会离场。随时可以重新创建房间。'
            : confirmAction === 'kick'
              ? '这位玩家将被移出。如果正在游戏，大家会一起返回大厅。'
              : '离开后需要重新加入；如果你是房主，会自动把房主交给下一位朋友。进行中的游戏会返回大厅。'
        }}
      </p>
      <div class="dialog-actions">
        <button class="button secondary" @click="confirmAction = null">再待一会</button>
        <button class="button primary" :disabled="busy" @click="confirm">
          {{ confirmAction === 'kick' ? '确认移出' : '确认离开' }}
        </button>
      </div>
    </AppDialog>
  </main>
</template>
