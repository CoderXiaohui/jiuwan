<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Check, Sparkles } from 'lucide-vue-next'
import { avatars, useUserStore } from '../stores/user'
import { useWebSocketStore } from '../stores/websocket'
import { api } from '../lib/api'
import type { Credentials } from '../lib/types'
const user = useUserStore(),
  ws = useWebSocketStore(),
  route = useRoute(),
  router = useRouter()
const name = ref(user.nickname),
  emoji = ref(user.avatar),
  busy = ref(false),
  error = ref('')
const code = computed(() => String(route.query.join ?? user.credentials?.roomCode ?? ''))
async function enter() {
  if (!name.value.trim()) return
  busy.value = true
  error.value = ''
  try {
    if (route.query.join) {
      const credentials = await api<Credentials>(`/rooms/${code.value}/join`, {
        nickname: name.value.trim(),
        avatar: emoji.value,
      })
      user.save(credentials)
    } else {
      await ws.send('UPDATE_PROFILE', { nickname: name.value.trim(), avatar: emoji.value })
    }
    user.profile(name.value.trim(), emoji.value)
    await router.replace(`/room/${code.value}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '暂时无法加入房间'
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <main class="form-page">
    <RouterLink to="/" class="back-link">
      <ArrowLeft :size="17" />
      返回首页
    </RouterLink>
    <section class="form-card profile-card">
      <span class="room-created">
        <Check :size="15" />
        {{ route.query.join ? '即将加入' : '房间已创建' }}
        <strong>{{ code }}</strong>
      </span>
      <div class="profile-preview">
        {{ emoji }}
        <span><Sparkles :size="15" /></span>
      </div>
      <h1>今天，你是谁？</h1>
      <p class="form-description">起个好记的名字，让朋友一眼认出你。</p>
      <form @submit.prevent="enter">
        <label for="nickname">
          你的昵称
          <small>{{ name.length }}/16</small>
        </label>
        <input
          id="nickname"
          v-model="name"
          maxlength="16"
          placeholder="比如：今晚的气氛组"
          autocomplete="nickname"
          autofocus
          required
        />
        <label class="avatar-label">选个头像，亮个相</label>
        <div class="avatar-picker">
          <button
            v-for="avatar in avatars"
            :key="avatar"
            type="button"
            :class="{ selected: emoji === avatar }"
            :aria-pressed="emoji === avatar"
            :aria-label="`选择头像 ${avatar}`"
            @click="emoji = avatar"
          >
            {{ avatar }}
            <Check v-if="emoji === avatar" :size="12" />
          </button>
        </div>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button
          class="button primary full"
          :disabled="busy || !name.trim() || (!route.query.join && ws.status !== 'connected')"
        >
          {{ busy ? '正在入场…' : '准备好了，入场' }}
          <ArrowRight :size="18" />
        </button>
      </form>
    </section>
  </main>
</template>
