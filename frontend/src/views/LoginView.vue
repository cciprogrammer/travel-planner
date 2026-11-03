<template>
  <div class="login-page">
    <div class="login-box">
      <div class="brand">
        <div class="logo">AI</div>
        <div>
          <h1>旅行规划助手</h1>
          <p class="muted">基于大模型的智能行程生成系统</p>
        </div>
      </div>

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" :rules="rules" ref="loginRef" label-position="top" @keyup.enter="doLogin">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码"
                :prefix-icon="Lock" @keyup.enter="doLogin" />
            </el-form-item>
            <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" :rules="regRules" ref="regRef" label-position="top">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="regForm.username" placeholder="3-20 位" :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="regForm.password" type="password" show-password placeholder="6-32 位"
                :prefix-icon="Lock" />
            </el-form-item>
            <el-form-item label="昵称（可选）" prop="nickname">
              <el-input v-model="regForm.nickname" placeholder="展示用的昵称" />
            </el-form-item>
            <el-button type="success" size="large" style="width:100%" :loading="loading" @click="doRegister">
              注 册 并 登 录
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <p class="muted tip">提示：首次使用请先注册账号，系统会为每人独立保存行程与收藏。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()

const activeTab = ref('login')
const loading = ref(false)
const loginRef = ref()
const regRef = ref()

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', nickname: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const regRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度 3-20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度 6-32 位', trigger: 'blur' }
  ]
}

async function doLogin() {
  await loginRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await login(loginForm)
      store.setLogin(res.data.token, res.data.user)
      ElMessage.success('登录成功')
      router.push('/home')
    } finally {
      loading.value = false
    }
  })
}

async function doRegister() {
  await regRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await register(regForm)
      store.setLogin(res.data.token, res.data.user)
      ElMessage.success('注册成功')
      router.push('/home')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eaf1ff 0%, #f7f9ff 60%, #eef6ff 100%);
}

.login-box {
  width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 32px 32px 24px;
  box-shadow: 0 10px 40px rgba(47, 111, 237, 0.12);
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}

.logo {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #2f6fed;
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.brand h1 {
  font-size: 20px;
  margin: 0;
}

.brand p {
  margin: 2px 0 0;
}

.tip {
  margin-top: 16px;
  text-align: center;
}
</style>
