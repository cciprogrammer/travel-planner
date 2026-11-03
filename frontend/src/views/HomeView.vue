<template>
  <div class="page">
    <!-- Hero：先回答“能给我什么”，再让人填表 -->
    <section class="hero">
      <h1 class="hero-title">说出想去哪，剩下的交给 AI</h1>
      <p class="hero-sub">只需目的地、天数、预算三项，即可得到每日安排、住宿推荐与地图动线</p>
      <div class="hero-chips">
        <span class="hero-label">常用</span>
        <span v-for="c in hotCities" :key="c" class="pill"
          :class="{ 'is-active': form.destination === c }" @click="pickCity(c)">{{ c }}</span>
      </div>
    </section>

    <div class="home-grid">
      <div class="card form-card">
        <div class="row-between form-head">
          <span class="section-title">填写出行条件</span>
          <el-button link type="primary" size="small" @click="reset">清空</el-button>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
          <div class="grid-2">
            <el-form-item label="目的地" prop="destination">
              <el-input v-model="form.destination" placeholder="如：成都" size="large" />
            </el-form-item>
            <el-form-item label="出发地" prop="origin">
              <el-input v-model="form.origin" placeholder="如：东莞" size="large" />
            </el-form-item>
          </div>

          <div class="grid-2">
            <el-form-item label="出行天数">
              <div class="stepper">
                <button type="button" class="step-btn" :disabled="form.days <= 1"
                  @click="stepDays(-1)">−</button>
                <span class="step-val num">{{ form.days }} 天</span>
                <button type="button" class="step-btn" :disabled="form.days >= 15"
                  @click="stepDays(1)">+</button>
              </div>
            </el-form-item>
            <el-form-item label="总预算">
              <el-input-number v-model="form.budget" :min="0" :step="500" :controls="false"
                size="large" class="budget-main" />
              <div class="budget-quick">
                <span class="quick-label">常用</span>
                <span v-for="p in budgetPresets" :key="p" class="quick-item"
                  :class="{ 'is-active': form.budget === p }" @click="form.budget = p">{{ budgetLabel(p) }}</span>
              </div>
            </el-form-item>
          </div>

          <el-form-item label="兴趣偏好（多选，影响行程节奏与景点类型）">
            <div class="pill-wrap">
              <span v-for="h in hobbyOptions" :key="h" class="pill"
                :class="{ 'is-active': hobbyList.includes(h) }" @click="toggleHobby(h)">{{ h }}</span>
            </div>
          </el-form-item>

          <div class="optional-toggle" @click="showOptional = !showOptional">
            <span class="opt-label">可选设置：出发日期、同行人、其他偏好</span>
            <span class="opt-action">
              {{ showOptional ? '收起' : '展开' }}
              <el-icon :size="11"><ArrowUp v-if="showOptional" /><ArrowDown v-else /></el-icon>
            </span>
          </div>

          <el-collapse-transition>
            <div v-show="showOptional" class="optional-area">
              <div class="grid-2">
                <el-form-item label="出发日期">
                  <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD"
                    placeholder="可选" style="width: 100%" />
                </el-form-item>
                <el-form-item label="同行人">
                  <el-input v-model="form.companions" placeholder="如：情侣2人 / 亲子 / 独自" />
                </el-form-item>
              </div>
              <el-form-item label="其他偏好">
                <el-input v-model="form.preferences" type="textarea" :rows="3"
                  placeholder="如：高铁出行、住连锁酒店、节奏轻松、不吃辣、多安排博物馆" />
              </el-form-item>
            </div>
          </el-collapse-transition>

          <div class="submit-row">
            <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="submit">
              <el-icon class="btn-icon"><MagicStick /></el-icon>
              {{ loading ? '大模型正在规划中…' : '生成行程方案' }}
            </el-button>
            <p class="submit-hint">通常 10–40 秒，生成后可随时手动调整</p>
          </div>
        </el-form>
      </div>

      <aside class="side-col">
        <div class="card side-card">
          <div class="row-between" style="margin-bottom: 12px">
            <span class="section-title" style="margin: 0">最近生成</span>
            <el-button link type="primary" size="small" @click="$router.push('/plans')">全部</el-button>
          </div>

          <div v-if="!recent.length" class="empty-wrap" style="padding: 16px 0 8px">
            <p class="empty-title">还没有生成过方案</p>
            <p class="empty-desc">填写左侧条件，第一次生成约需 20 秒</p>
          </div>

          <div v-else class="recent-list">
            <div v-for="(p, i) in recent" :key="p.id" class="recent-item"
              @click="$router.push(`/plans/${p.id}`)">
              <span class="recent-bar" :class="{ 'is-first': i === 0 }"></span>
              <div class="recent-main">
                <div class="recent-title">{{ p.title }}</div>
                <div class="recent-meta">
                  {{ p.origin }} → {{ p.destination }} · {{ p.days }} 天 · ¥{{ p.budget }}
                </div>
              </div>
              <span class="recent-time">{{ formatShort(p.createTime) }}</span>
            </div>
          </div>
        </div>

        <div class="brand-note">
          <div class="note-title">生成后可以改什么</div>
          <div class="note-body">改住宿会自动重排当天动线；不选推荐酒店可自填每晚花销，预算合计实时重算。</div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generatePlan, fetchPlans } from '@/api/plan'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const recent = ref([])
const showOptional = ref(false)

const hotCities = ['长沙', '成都', '西安', '桂林']
const budgetPresets = [2000, 3000, 5000, 8000]
const hobbyOptions = ['美食', '自然风光', '历史人文', '城市打卡', '购物', '亲子', '摄影', '户外徒步', '夜生活', '温泉休闲']
const hobbyList = ref(['美食', '自然风光'])

const form = reactive({
  origin: '',
  destination: '',
  days: 3,
  budget: 3000,
  startDate: '',
  companions: '',
  preferences: '',
  hobbies: '美食、自然风光'
})

const rules = {
  origin: [{ required: true, message: '请输入出发地', trigger: 'blur' }],
  destination: [{ required: true, message: '请输入目的地', trigger: 'blur' }]
}

function pickCity(c) {
  form.destination = c
}

function stepDays(delta) {
  const next = form.days + delta
  if (next >= 1 && next <= 15) form.days = next
}

function toggleHobby(h) {
  const i = hobbyList.value.indexOf(h)
  if (i >= 0) {
    if (hobbyList.value.length === 1) {
      ElMessage.info('至少保留一个兴趣偏好')
      return
    }
    hobbyList.value.splice(i, 1)
  } else {
    hobbyList.value.push(h)
  }
  form.hobbies = hobbyList.value.join('、')
}

function budgetLabel(v) {
  return v >= 10000 ? v / 10000 + ' 万' : '¥' + v
}

function reset() {
  Object.assign(form, {
    origin: '', destination: '', days: 3, budget: 3000,
    startDate: '', companions: '', preferences: ''
  })
  hobbyList.value = ['美食', '自然风光']
  form.hobbies = '美食、自然风光'
}

async function submit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const payload = { ...form, hobbies: hobbyList.value.join('、') }
      const res = await generatePlan(payload)
      ElMessage.success('行程生成成功')
      router.push(`/plans/${res.data.planId}`)
    } finally {
      loading.value = false
    }
  })
}

function formatShort(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}

async function loadRecent() {
  const res = await fetchPlans({ pageNum: 1, pageSize: 5 })
  recent.value = res.data.records || []
}

onMounted(loadRecent)
</script>

<style scoped>
.hero {
  background: linear-gradient(180deg, #f2f6ff 0%, #f7f9ff 100%);
  border: 0.5px solid var(--border);
  border-radius: var(--radius-card);
  padding: 26px 24px 22px;
  margin-bottom: 20px;
}

.hero-title {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.4px;
  margin: 0 0 6px;
  color: var(--text-1);
}

.hero-sub {
  font-size: var(--fs-label);
  color: var(--text-2);
  margin: 0 0 14px;
  line-height: 1.7;
}

.hero-chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.hero-label {
  font-size: var(--fs-mini);
  color: var(--text-3);
  margin-right: 2px;
}

.home-grid {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: 20px;
  align-items: start;
}

.form-card {
  margin-bottom: 0;
}

.form-head {
  margin-bottom: 14px;
}

.form-head .section-title {
  margin: 0;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.stepper {
  display: flex;
  align-items: center;
  width: 100%;
  border: 0.5px solid var(--border-strong);
  border-radius: var(--radius-ctrl);
  overflow: hidden;
  height: 40px;
  background: #fff;
}

.step-btn {
  width: 38px;
  height: 100%;
  border: none;
  background: #fff;
  color: var(--text-2);
  font-size: 16px;
  cursor: pointer;
  transition: background 120ms ease;
}

.step-btn:hover:not(:disabled) {
  background: var(--brand-light);
  color: var(--brand-hover);
}

.step-btn:disabled {
  color: #c8cfda;
  cursor: not-allowed;
}

.step-val {
  flex: 1;
  text-align: center;
  font-size: var(--fs-label);
  font-weight: 500;
  border-left: 0.5px solid var(--border);
  border-right: 0.5px solid var(--border);
  line-height: 38px;
}

.budget-main {
  width: 100%;
}

.budget-quick {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.quick-label {
  font-size: var(--fs-mini);
  color: var(--text-3);
}

.quick-item {
  font-size: var(--fs-mini);
  color: var(--text-2);
  padding: 1px 7px;
  border-radius: 4px;
  background: var(--bg-subtle);
  cursor: pointer;
  transition: all 120ms ease;
}

.quick-item:hover {
  color: var(--brand-hover);
}

.quick-item.is-active {
  background: var(--brand-light);
  color: var(--brand-active);
  font-weight: 500;
}

.pill-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.optional-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0 12px;
  cursor: pointer;
  border-top: 0.5px dashed var(--border);
  margin-top: 2px;
}

.opt-label {
  font-size: var(--fs-note);
  color: var(--text-3);
}

.opt-action {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: var(--fs-note);
  color: var(--brand);
}

.optional-area {
  padding-bottom: 4px;
}

.submit-row {
  margin: 4px 0 0;
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: var(--fs-body);
  font-weight: 500;
  border-radius: var(--radius-ctrl);
  margin-bottom: 8px;
}

.btn-icon {
  margin-right: 6px;
}

.submit-hint {
  text-align: center;
  font-size: var(--fs-mini);
  color: var(--text-3);
  margin: 0;
}

.side-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-card {
  margin-bottom: 0;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 8px 0;
  border-radius: var(--radius-ctrl);
  cursor: pointer;
  transition: background 120ms ease;
}

.recent-item:hover {
  background: var(--bg-subtle);
}

.recent-bar {
  width: 3px;
  height: 30px;
  border-radius: 2px;
  background: var(--border);
  flex: none;
}

.recent-bar.is-first {
  background: var(--brand);
}

.recent-main {
  min-width: 0;
  flex: 1;
}

.recent-title {
  font-size: var(--fs-note);
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-meta {
  font-size: var(--fs-mini);
  color: var(--text-3);
  margin-top: 2px;
}

.recent-time {
  font-size: var(--fs-mini);
  color: var(--text-3);
  flex: none;
}

.note-title {
  font-size: var(--fs-note);
  font-weight: 600;
  color: #1747a8;
  margin-bottom: 4px;
}

.note-body {
  font-size: var(--fs-mini);
  color: var(--text-2);
  line-height: 1.7;
}

@media (max-width: 1100px) {
  .home-grid {
    grid-template-columns: 1fr;
  }
  .grid-2 {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .hero {
    padding: 20px 16px 16px;
  }
  .hero-title {
    font-size: 20px;
  }
}
</style>
