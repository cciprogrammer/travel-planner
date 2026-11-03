<template>
  <div>
    <header class="app-header no-print">
      <div class="inner">
        <div class="left" @click="$router.push('/home')">
          <span class="logo">AI</span>
          <span class="name">旅行规划助手</span>
        </div>
        <el-menu :default-active="activeMenu" mode="horizontal" :router="true" :ellipsis="false">
          <el-menu-item index="/home">生成行程</el-menu-item>
          <el-menu-item index="/plans">我的行程</el-menu-item>
          <el-menu-item index="/favorites">地点收藏</el-menu-item>
          <el-menu-item index="/profile">个人中心</el-menu-item>
          <el-menu-item v-if="store.isAdmin" index="/admin">后台管理</el-menu-item>
        </el-menu>
        <div class="right">
          <el-tag v-if="store.isAdmin" size="small" type="warning" effect="plain">管理员</el-tag>
          <el-dropdown trigger="click" @command="onUserCommand">
            <span class="user-trigger">
              <el-avatar :size="28" :src="store.userInfo?.avatar || defaultAvatar" :icon="UserFilled" />
              <span class="user-name">{{ store.nickname }}</span>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <main>
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { UserFilled, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const defaultAvatar =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120">' +
      '<rect width="120" height="120" rx="60" fill="#2f6fed"/>' +
      '<text x="60" y="78" font-size="52" font-family="Microsoft YaHei,PingFang SC,Arial" fill="#ffffff" text-anchor="middle">旅</text></svg>'
  )

const activeMenu = computed(() => {
  if (route.path.startsWith('/plans')) return '/plans'
  if (route.path.startsWith('/favorites')) return '/favorites'
  if (route.path.startsWith('/profile')) return '/profile'
  if (route.path.startsWith('/admin')) return '/admin'
  return '/home'
})

function onUserCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    handleLogout()
  }
}

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' }).then(() => {
    store.logout()
    ElMessage.success('已退出')
    router.push('/login')
  }).catch(() => {})
}
</script>

<style scoped>
.app-header {
  background: #fff;
  border-bottom: 0.5px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 100;
}

.inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  gap: 20px;
  height: 56px;
  display: flex;
  align-items: center;
}

.left {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #2f6fed;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.name {
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--radius-ctrl);
  transition: background 120ms ease;
}

.user-trigger:hover {
  background: var(--bg-subtle);
}

.user-name {
  font-size: var(--fs-note);
  color: var(--text-2);
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.caret {
  font-size: 11px;
  color: var(--text-3);
}

:deep(.el-menu) {
  border-bottom: none;
  flex: none;
}

:deep(.el-menu--horizontal > .el-menu-item) {
  font-size: var(--fs-label);
  color: var(--text-2);
  height: 56px;
  line-height: 56px;
  border-bottom-width: 2px;
  padding: 0 14px;
}

:deep(.el-menu--horizontal > .el-menu-item.is-active) {
  color: var(--brand-active);
  font-weight: 500;
  border-bottom-color: var(--brand);
  background: transparent;
}

:deep(.el-menu--horizontal > .el-menu-item:hover) {
  color: var(--brand-hover);
  background: transparent;
}
</style>
