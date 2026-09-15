import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import JoinView from './views/JoinView.vue'
import ProfileView from './views/ProfileView.vue'
import RoomView from './views/RoomView.vue'
import './style.css'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/join', component: JoinView },
    { path: '/profile', component: ProfileView },
    { path: '/room/:code', component: RoomView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: (to, _from, savedPosition) =>
    savedPosition ?? (to.hash ? { el: to.hash, behavior: 'smooth' } : { top: 0 }),
})
createApp(App).use(createPinia()).use(router).mount('#app')
if (import.meta.env.PROD && 'serviceWorker' in navigator)
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {})
  })
