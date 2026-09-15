<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Hash } from 'lucide-vue-next'
import { api } from '../lib/api'
import { useUserStore } from '../stores/user'
const router = useRouter(),
  route = useRoute(),
  user = useUserStore()
const code = ref(
    String(route.query.code ?? '')
      .replace(/\D/g, '')
      .slice(0, 6),
  ),
  busy = ref(false),
  error = ref('')
async function join() {
  if (!/^[0-9]{6}$/.test(code.value)) {
    error.value = '请输入完整的 6 位房间码'
    return
  }
  if (user.credentials?.roomCode === code.value) {
    await router.push(`/room/${code.value}`)
    return
  }
  if (user.credentials) {
    error.value = '请先回到当前房间并退出，再加入新房间'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await api(`/rooms/${code.value}`)
    await router.push({ path: '/profile', query: { join: code.value } })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '暂时无法加入'
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
    <section class="form-card">
      <div class="form-icon purple"><Hash :size="30" /></div>
      <span class="eyebrow subtle">THE GANG IS WAITING</span>
      <h1>
        朋友都在，
        <br />
        就差你啦。
      </h1>
      <p class="form-description">向房主要个房间码，快乐马上接上。</p>
      <form @submit.prevent="join">
        <label for="room-code">6 位房间码</label>
        <input
          id="room-code"
          v-model="code"
          class="code-input"
          type="text"
          inputmode="numeric"
          pattern="[0-9]{6}"
          maxlength="6"
          placeholder="000000"
          autocomplete="off"
          autofocus
          @input="code = code.replace(/\D/g, '')"
          required
        />
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="button primary full" :disabled="busy || code.length !== 6">
          {{ busy ? '寻找房间中…' : '加入房间' }}
          <ArrowRight :size="18" />
        </button>
      </form>
      <RouterLink
        v-if="user.credentials"
        :to="`/room/${user.credentials.roomCode}`"
        class="text-link"
      >
        返回当前房间 {{ user.credentials.roomCode }}
      </RouterLink>
      <p class="form-hint">无需注册。起个昵称，就能一起玩。</p>
    </section>
  </main>
</template>
