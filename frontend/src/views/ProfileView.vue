<template>
  <div class="page">
    <h2 class="page-title">个人中心</h2>
    <p class="page-desc">修改头像、昵称与登录密码。</p>

    <!-- 头像 -->
    <div class="card">
      <h3 class="sec-title">头像</h3>
      <div class="avatar-row">
        <el-avatar :size="84" :src="form.avatar || defaultAvatar" :icon="UserFilled" />

        <div class="avatar-ops">
          <el-upload :show-file-list="false" accept="image/*" :before-upload="onPickFile">
            <el-button type="primary" :icon="Upload" :loading="uploading">上传本地图片</el-button>
          </el-upload>
          <span class="muted">支持 jpg / png / webp / gif，不超过 2MB</span>

          <div class="presets">
            <span class="muted" style="margin-right:8px">或使用预设头像：</span>
            <el-avatar v-for="(item, i) in presets" :key="i" :size="40" :src="item" class="preset"
              :class="{ active: form.avatar === item }" @click="choosePreset(item)" />
          </div>
        </div>
      </div>
    </div>

    <!-- 基本资料 -->
    <div class="card">
      <h3 class="sec-title">基本资料</h3>
      <el-form :model="form" ref="profileRef" label-width="90px" style="max-width:480px">
        <el-form-item label="用户名">
          <el-input :model-value="userInfo?.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="展示在顶栏的昵称" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 修改密码 -->
    <div class="card">
      <h3 class="sec-title">修改密码</h3>
      <el-form :model="pwd" :rules="pwdRules" ref="pwdRef" label-width="90px" style="max-width:480px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwd.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwd.newPassword" type="password" show-password placeholder="6-32 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="pwd.confirm" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="warning" :loading="savingPwd" @click="savePassword">确认修改</el-button>
          <el-button @click="resetPwd">清空</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, UserFilled } from '@element-plus/icons-vue'
import { fetchProfile, updateProfile, updatePassword, uploadAvatar } from '@/api/user'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const userInfo = computed(() => store.userInfo)

const profileRef = ref()
const pwdRef = ref()
const uploading = ref(false)
const savingProfile = ref(false)
const savingPwd = ref(false)

const form = reactive({ nickname: '', avatar: '' })
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const PRESET_COLORS = ['#2f6fed', '#f2994a', '#27ae60', '#eb5757', '#9b51e0', '#00b8d9']

function svgAvatar(color) {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120">` +
    `<rect width="120" height="120" rx="60" fill="${color}"/>` +
    `<text x="60" y="78" font-size="52" font-family="Microsoft YaHei,PingFang SC,Arial" ` +
    `fill="#ffffff" text-anchor="middle">旅</text></svg>`
  return 'data:image/svg+xml,' + encodeURIComponent(svg)
}

const presets = PRESET_COLORS.map(svgAvatar)
const defaultAvatar = presets[0]

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度 6-32 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwd.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function choosePreset(url) {
  form.avatar = url
}

async function onPickFile(file) {
  const okType = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)
  if (!okType) {
    ElMessage.error('仅支持 jpg / png / webp / gif 格式')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  uploading.value = true
  try {
    const res = await uploadAvatar(file)
    form.avatar = res.data.url
    await updateProfile({ avatar: form.avatar, nickname: form.nickname })
    store.setUser({ avatar: form.avatar })
    ElMessage.success('头像已更新')
  } finally {
    uploading.value = false
  }
  return false
}

async function saveProfile() {
  savingProfile.value = true
  try {
    await updateProfile({ nickname: form.nickname, avatar: form.avatar })
    store.setUser({ nickname: form.nickname, avatar: form.avatar })
    ElMessage.success('资料已保存')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  await pwdRef.value.validate(async (valid) => {
    if (!valid) return
    savingPwd.value = true
    try {
      await updatePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
      ElMessage.success('密码修改成功，下次登录请使用新密码')
      resetPwd()
    } finally {
      savingPwd.value = false
    }
  })
}

function resetPwd() {
  pwd.oldPassword = ''
  pwd.newPassword = ''
  pwd.confirm = ''
  pwdRef.value && pwdRef.value.clearValidate()
}

onMounted(async () => {
  const res = await fetchProfile()
  const user = res.data
  form.nickname = user.nickname || ''
  form.avatar = user.avatar || ''
  store.setUser({ nickname: user.nickname, avatar: user.avatar, username: user.username })
})
</script>

<style scoped>
.sec-title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.avatar-ops {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.presets {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.preset {
  cursor: pointer;
  border: 2px solid transparent;
  transition: transform 0.15s;
}

.preset:hover {
  transform: scale(1.08);
}

.preset.active {
  border-color: #2f6fed;
}
</style>
