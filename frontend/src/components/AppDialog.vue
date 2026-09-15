<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { X } from 'lucide-vue-next'
const props = defineProps<{ open: boolean; title: string }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement>()
const titleId = useId()
watch(
  () => props.open,
  async (open) => {
    await nextTick()
    if (open) dialog.value?.showModal()
    else dialog.value?.close()
  },
  { immediate: true },
)
onBeforeUnmount(() => dialog.value?.close())
</script>
<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="app-dialog"
      :aria-labelledby="titleId"
      @cancel.prevent="emit('close')"
      @click="$event.target === dialog && emit('close')"
    >
      <div class="dialog-inner">
        <div class="dialog-heading">
          <h2 :id="titleId">{{ title }}</h2>
          <button class="icon-button" aria-label="关闭弹窗" @click="emit('close')">
            <X :size="20" />
          </button>
        </div>
        <slot />
      </div>
    </dialog>
  </Teleport>
</template>
