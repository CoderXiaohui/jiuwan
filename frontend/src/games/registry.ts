import { defineAsyncComponent, type Component } from 'vue'
const modules = import.meta.glob<{ default: Component }>('./*/Game.vue')
export const gameComponents = Object.fromEntries(
  Object.entries(modules).map(([path, loader]) => [
    path.split('/')[1],
    defineAsyncComponent(loader),
  ]),
)
