<template>
  <div class="anchor-bar no-print">
    <div class="anchor-inner">
      <button v-for="it in items" :key="it.id" type="button" class="anchor-item"
        :class="{ 'is-active': active === it.id }" @click="go(it.id)">{{ it.label }}</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] }
})

const active = ref('')
let ticking = false

/** 滚动时高亮「最后一个已越过顶部阈值」的区块 */
function onScroll() {
  if (ticking) return
  ticking = true
  requestAnimationFrame(() => {
    ticking = false
    const offset = 120
    let current = ''
    props.items.forEach((it) => {
      const el = document.getElementById(it.id)
      if (!el) return
      if (el.getBoundingClientRect().top - offset <= 0) current = it.id
    })
    if (!current && props.items.length) current = props.items[0].id
    // 滚到底部时最后一段可能永远越不过阈值，直接高亮最后一项
    if (props.items.length && window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4) {
      current = props.items[props.items.length - 1].id
    }
    active.value = current
  })
}

function go(id) {
  const el = document.getElementById(id)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  active.value = id
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  nextTick(onScroll)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
.anchor-bar {
  position: sticky;
  top: 56px;
  z-index: 30;
  background: var(--bg-page);
  margin: -4px 0 16px;
  padding: 6px 0 0;
  border-bottom: 0.5px solid var(--border);
}

.anchor-inner {
  display: flex;
  align-items: center;
  gap: 20px;
  overflow-x: auto;
  padding: 0 2px;
  scrollbar-width: none;
}

.anchor-inner::-webkit-scrollbar {
  display: none;
}

.anchor-item {
  border: none;
  background: transparent;
  padding: 8px 0;
  font-size: var(--fs-label);
  color: var(--text-2);
  cursor: pointer;
  white-space: nowrap;
  border-bottom: 2px solid transparent;
  transition: color 120ms ease, border-color 120ms ease;
}

.anchor-item:hover {
  color: var(--brand-hover);
}

.anchor-item.is-active {
  color: var(--brand-active);
  font-weight: 500;
  border-bottom-color: var(--brand);
}
</style>
