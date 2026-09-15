<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowUpRight, X, WifiOff } from 'lucide-vue-next'
import { useUserStore } from './stores/user'
import { useWebSocketStore } from './stores/websocket'
const user = useUserStore(),
  ws = useWebSocketStore(),
  route = useRoute()
watch(
  () => user.credentials,
  (value) => {
    if (value) ws.connect()
    else ws.disconnect()
  },
  { immediate: true },
)
const statusLabel = computed(
  () =>
    ({ connecting: '连接中', connected: '已连接', reconnecting: '重连中', offline: '离线' })[
      ws.status
    ],
)
</script>
<template>
  <div class="site-shell">
    <header class="site-header">
      <RouterLink to="/" class="brand" aria-label="酒玩首页">
        <img src="/icon.svg" alt="" width="37" height="37" />
        <span>
          酒玩
          <span class="brand-dot">.</span>
        </span>
        <small>JIUWAN</small>
      </RouterLink>
      <nav v-if="route.path === '/'" class="desktop-nav" aria-label="主要导航">
        <a href="#games">发现玩法</a>
        <a href="#how">怎么玩</a>
        <span class="nav-divider"></span>
        <span class="nav-note">
          好朋友的快乐集合地
          <ArrowUpRight :size="14" />
        </span>
      </nav>
      <button
        v-if="user.credentials"
        class="connection-badge"
        :class="ws.status"
        @click="ws.status !== 'connected' && ws.reconnect()"
        :aria-label="`连接状态：${statusLabel}`"
      >
        <i />
        {{ statusLabel }}
      </button>
      <span v-else class="header-badge">
        <i />
        随时开玩
      </span>
    </header>
    <div
      v-if="user.credentials && ws.status !== 'connected'"
      class="connection-banner"
      role="status"
    >
      <WifiOff :size="16" />
      {{
        ws.status === 'offline' ? '网络暂时离线，恢复后自动回到房间' : '正在同步房间，快乐马上回来…'
      }}
    </div>
    <RouterView />
    <footer class="site-footer">
      <div class="footer-brand">
        酒玩
        <span>.</span>
        <small>让聚会，自带快乐。</small>
      </div>
      <p>尽兴玩，量力而行。每个挑战都可以说「跳过」。</p>
      <span>MADE FOR GOOD TIMES ↗</span>
    </footer>
    <div v-if="ws.error" class="toast" role="alert">
      <span>{{ ws.error }}</span>
      <button aria-label="关闭提示" @click="ws.error = ''"><X :size="18" /></button>
    </div>
  </div>
</template>
