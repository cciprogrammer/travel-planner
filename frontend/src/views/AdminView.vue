<template>
  <div class="page">
    <h2 class="page-title">后台管理</h2>
    <p class="page-desc">查看系统运行数据、检测用户使用情况，并维护酒店信息。</p>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <!-- ============================ 数据概览 ============================ -->
      <el-tab-pane label="数据概览" name="overview">
        <div v-loading="statsLoading">
          <div class="stat-grid">
            <div class="stat-card" v-for="c in statCards" :key="c.label">
              <div class="stat-label">{{ c.label }}</div>
              <div class="stat-value">{{ c.value ?? '—' }}</div>
              <div class="stat-sub">{{ c.sub }}</div>
            </div>
          </div>

          <div class="card">
            <h3 class="sec-title">近 7 天生成方案数</h3>
            <div class="chart" v-if="trend.length">
              <div class="bar-item" v-for="p in trend" :key="p.date">
                <span class="bar-val" :class="{ 'is-zero': !p.count }">{{ p.count }}</span>
                <div class="bar-wrap">
                  <div class="bar" :class="{ 'is-zero': !p.count }" :style="{ height: barHeight(p.count) }"
                    :title="p.date + '：生成 ' + p.count + ' 个方案'"></div>
                </div>
                <span class="bar-label">{{ p.date.slice(5) }}</span>
              </div>
            </div>
            <el-empty v-else description="暂无数据" :image-size="70" />
          </div>

          <div class="two-col">
            <div class="card">
              <h3 class="sec-title">酒店城市分布</h3>
              <el-table :data="stats.hotelByCity || []" size="small" empty-text="暂无酒店">
                <el-table-column prop="city" label="城市" />
                <el-table-column prop="count" label="数量" width="90" align="right" />
              </el-table>
            </div>
            <div class="card">
              <h3 class="sec-title">活跃用户 Top5（按方案数）</h3>
              <el-table :data="stats.topUsers || []" size="small" empty-text="暂无数据">
                <el-table-column prop="username" label="用户名" />
                <el-table-column prop="nickname" label="昵称" />
                <el-table-column prop="planCount" label="方案数" width="90" align="right" />
              </el-table>
            </div>
          </div>

          <div class="muted" style="margin-top:4px">
            数据统计口径：用户/方案/收藏均不含已删除记录；「今日」按服务器日期 0 点起算。
          </div>
        </div>
      </el-tab-pane>

      <!-- ============================ 用户数据 ============================ -->
      <el-tab-pane label="用户数据" name="users">
        <div class="toolbar">
          <el-input v-model="userQuery.keyword" placeholder="搜索用户名 / 昵称" clearable style="width: 220px"
            @keyup.enter="searchUsers" />
          <el-select v-model="userQuery.role" placeholder="全部角色" clearable style="width: 130px">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
          <el-select v-model="userQuery.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="正常" :value="1" />
            <el-option label="已禁用" :value="0" />
          </el-select>
          <el-button type="primary" @click="searchUsers">查询</el-button>
          <el-button @click="resetUsers">重置</el-button>
        </div>

        <el-table :data="users" v-loading="userLoading" size="default" stripe empty-text="没有符合条件的用户">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="用户名" min-width="104" show-overflow-tooltip />
          <el-table-column prop="nickname" label="昵称" min-width="96" show-overflow-tooltip />
          <el-table-column label="角色" width="84">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.role === 'ADMIN' ? 'warning' : 'info'">
                {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="84">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
                {{ row.status === 1 ? '正常' : '已禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="planCount" label="方案数" width="74" align="center" />
          <el-table-column prop="favoriteCount" label="收藏数" width="74" align="center" />
          <el-table-column label="注册时间" width="138">
            <template #default="{ row }">{{ fmt(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="最近生成" width="138">
            <template #default="{ row }">{{ fmt(row.lastPlanTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="138" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openUserPlans(row)">查看方案</el-button>
              <el-button link :type="row.status === 1 ? 'danger' : 'success'" :disabled="row.role === 'ADMIN'"
                @click="toggleUserStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination background layout="total, prev, pager, next" :total="userTotal"
            :page-size="userQuery.pageSize" :current-page="userQuery.pageNum" @current-change="onUserPage" />
        </div>
      </el-tab-pane>

      <!-- ============================ 酒店管理 ============================ -->
      <el-tab-pane label="酒店管理" name="hotels">
        <div class="toolbar">
          <el-input v-model="hotelQuery.keyword" placeholder="搜索酒店名称 / 区域 / 地址 / 标签" clearable
            style="width: 240px" @keyup.enter="searchHotels" />
          <el-select v-model="hotelQuery.city" placeholder="全部城市" clearable filterable style="width: 140px">
            <el-option v-for="c in cities" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="hotelQuery.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
          <el-button type="primary" @click="searchHotels">查询</el-button>
          <el-button @click="resetHotels">重置</el-button>
          <div class="spacer"></div>
          <el-button type="primary" :icon="Plus" @click="openHotelDialog()">新增酒店</el-button>
        </div>

        <el-table :data="hotels" v-loading="hotelLoading" size="default" stripe empty-text="还没有酒店，点右上角「新增酒店」添加">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="name" label="酒店名称" min-width="168" show-overflow-tooltip />
          <el-table-column prop="city" label="城市" width="74" />
          <el-table-column prop="area" label="区域" width="96" show-overflow-tooltip />
          <el-table-column prop="level" label="档次" width="82" />
          <el-table-column label="每晚参考价" width="96" align="right">
            <template #default="{ row }">¥{{ row.pricePerNight ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="评分" width="68" align="center">
            <template #default="{ row }">{{ row.rating ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="tags" label="标签" min-width="118" show-overflow-tooltip />
          <el-table-column label="上架" width="74" align="center">
            <template #default="{ row }">
              <el-switch :model-value="row.status === 1" @change="(v) => toggleHotelStatus(row, v)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="118" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openHotelDialog(row)">编辑</el-button>
              <el-button link type="danger" @click="removeHotel(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination background layout="total, prev, pager, next" :total="hotelTotal"
            :page-size="hotelQuery.pageSize" :current-page="hotelQuery.pageNum" @current-change="onHotelPage" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ============================ 查看某用户的方案 ============================ -->
    <el-dialog v-model="plansDialog" :title="plansDialogTitle" width="760px">
      <el-table :data="userPlans" v-loading="plansLoading" size="small" empty-text="该用户还没有生成过方案">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="destination" label="目的地" width="100" />
        <el-table-column prop="days" label="天数" width="70" align="center" />
        <el-table-column label="预算" width="100" align="right">
          <template #default="{ row }">{{ row.budget ? '¥' + row.budget : '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="152">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination small background layout="total, prev, pager, next" :total="userPlanTotal"
          :page-size="planQuery.pageSize" :current-page="planQuery.pageNum" @current-change="onUserPlanPage" />
      </div>
    </el-dialog>

    <!-- ============================ 新增 / 编辑酒店 ============================ -->
    <el-dialog v-model="hotelDialog" :title="hotelForm.id ? '编辑酒店' : '新增酒店'" width="620px">
      <el-form :model="hotelForm" :rules="hotelRules" ref="hotelFormRef" label-width="96px">
        <el-form-item label="酒店名称" prop="name">
          <el-input v-model="hotelForm.name" maxlength="100" placeholder="如：长沙五一广场亚朵酒店" />
        </el-form-item>
        <el-form-item label="所在城市" prop="city">
          <el-input v-model="hotelForm.city" maxlength="50" placeholder="如：长沙" />
        </el-form-item>
        <el-form-item label="区域 / 商圈" prop="area">
          <el-input v-model="hotelForm.area" maxlength="100" placeholder="如：五一广场" />
        </el-form-item>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="hotelForm.address" maxlength="255" placeholder="选填" />
        </el-form-item>
        <el-form-item label="档次" prop="level">
          <el-select v-model="hotelForm.level" placeholder="请选择" clearable style="width: 100%">
            <el-option label="经济型" value="经济型" />
            <el-option label="舒适型" value="舒适型" />
            <el-option label="高档型" value="高档型" />
          </el-select>
        </el-form-item>
        <el-form-item label="每晚参考价" prop="pricePerNight">
          <el-input-number v-model="hotelForm.pricePerNight" :min="0" :max="100000" :step="50"
            controls-position="right" style="width: 180px" />
          <span class="muted" style="margin-left: 8px">元 / 晚</span>
        </el-form-item>
        <el-form-item label="评分" prop="rating">
          <el-input-number v-model="hotelForm.rating" :min="0" :max="5" :step="0.1" :precision="1"
            controls-position="right" style="width: 160px" />
          <span class="muted" style="margin-left: 8px">0 - 5 分，选填</span>
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="hotelForm.phone" maxlength="50" placeholder="选填" />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model="hotelForm.tags" maxlength="255" placeholder="逗号分隔，如：近地铁,含早,免费停车" />
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input v-model="hotelForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="推荐理由 / 位置优势" />
        </el-form-item>
        <el-form-item label="上架状态">
          <el-switch v-model="hotelForm.status" :active-value="1" :inactive-value="0" />
          <span class="muted" style="margin-left: 8px">下架后仍保留在后台，仅不对外推荐</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hotelDialog = false">取消</el-button>
        <el-button type="primary" :loading="hotelSaving" @click="submitHotel">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  fetchStats,
  fetchUsers,
  updateUserStatus,
  fetchUserPlans,
  fetchHotels,
  fetchHotelCities,
  createHotel,
  updateHotel,
  updateHotelStatus,
  deleteHotel
} from '@/api/admin'

/* ------------------------------ 公共 ------------------------------ */

const tab = ref('overview')

function fmt(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}

/* ------------------------------ 概览 ------------------------------ */

const stats = ref({})
const statsLoading = ref(false)

const statCards = computed(() => {
  const s = stats.value || {}
  return [
    { label: '用户总数', value: s.totalUsers, sub: `正常 ${s.activeUsers ?? '—'} 人` },
    { label: '方案总数', value: s.totalPlans, sub: `今日新增 ${s.plansToday ?? 0} 个` },
    { label: '收藏总数', value: s.totalFavorites, sub: '全部用户累计' },
    { label: '酒店总数', value: s.totalHotels, sub: '含已下架' },
    { label: '今日新增用户', value: s.newUsersToday, sub: '按服务器日期 0 点起算' },
    { label: '近 7 天新增用户', value: s.newUsers7d, sub: '含今天' },
    { label: '近 7 天生成方案', value: s.plans7d, sub: '含今天' }
  ]
})

const trend = computed(() => (stats.value && stats.value.planTrend) || [])

const maxTrend = computed(() => {
  const list = trend.value.map((p) => Number(p.count) || 0)
  return list.length ? Math.max(1, ...list) : 1
})

function barHeight(count) {
  const c = Number(count) || 0
  if (c <= 0) return '3px'
  return Math.max(8, Math.round((c / maxTrend.value) * 100)) + '%'
}

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await fetchStats()
    stats.value = res.data || {}
  } finally {
    statsLoading.value = false
  }
}

/* ------------------------------ 用户数据 ------------------------------ */

const users = ref([])
const userTotal = ref(0)
const userLoading = ref(false)
const userQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', role: '', status: null })

async function loadUsers() {
  userLoading.value = true
  try {
    const res = await fetchUsers({
      pageNum: userQuery.pageNum,
      pageSize: userQuery.pageSize,
      keyword: userQuery.keyword || undefined,
      role: userQuery.role || undefined,
      status: userQuery.status === null || userQuery.status === '' ? undefined : userQuery.status
    })
    users.value = res.data.records || []
    userTotal.value = Number(res.data.total) || 0
  } finally {
    userLoading.value = false
  }
}

function searchUsers() {
  userQuery.pageNum = 1
  loadUsers()
}

function resetUsers() {
  userQuery.keyword = ''
  userQuery.role = ''
  userQuery.status = null
  searchUsers()
}

function onUserPage(p) {
  userQuery.pageNum = p
  loadUsers()
}

async function toggleUserStatus(row) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${action}用户「${row.username}」吗？${next === 0 ? '禁用后该用户将无法登录。' : ''}`,
      `${action}确认`,
      { type: 'warning' }
    )
  } catch {
    return
  }
  await updateUserStatus(row.id, next)
  ElMessage.success(`已${action}`)
  loadUsers()
}

/* ---------- 查看某用户的方案 ---------- */

const plansDialog = ref(false)
const plansLoading = ref(false)
const userPlans = ref([])
const userPlanTotal = ref(0)
const currentUser = ref(null)
const planQuery = reactive({ pageNum: 1, pageSize: 10 })

const plansDialogTitle = computed(() => {
  const u = currentUser.value
  if (!u) return '用户方案'
  return `${u.nickname || u.username} 的方案（${userPlanTotal.value} 个）`
})

async function loadUserPlans() {
  if (!currentUser.value) return
  plansLoading.value = true
  try {
    const res = await fetchUserPlans(currentUser.value.id, {
      pageNum: planQuery.pageNum,
      pageSize: planQuery.pageSize
    })
    userPlans.value = res.data.records || []
    userPlanTotal.value = Number(res.data.total) || 0
  } finally {
    plansLoading.value = false
  }
}

function openUserPlans(row) {
  currentUser.value = row
  planQuery.pageNum = 1
  plansDialog.value = true
  loadUserPlans()
}

function onUserPlanPage(p) {
  planQuery.pageNum = p
  loadUserPlans()
}

/* ------------------------------ 酒店管理 ------------------------------ */

const hotels = ref([])
const hotelTotal = ref(0)
const hotelLoading = ref(false)
const cities = ref([])
const hotelQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', city: '', status: null })

async function loadHotels() {
  hotelLoading.value = true
  try {
    const res = await fetchHotels({
      pageNum: hotelQuery.pageNum,
      pageSize: hotelQuery.pageSize,
      keyword: hotelQuery.keyword || undefined,
      city: hotelQuery.city || undefined,
      status: hotelQuery.status === null || hotelQuery.status === '' ? undefined : hotelQuery.status
    })
    hotels.value = res.data.records || []
    hotelTotal.value = Number(res.data.total) || 0
  } finally {
    hotelLoading.value = false
  }
}

async function loadCities() {
  const res = await fetchHotelCities()
  cities.value = res.data || []
}

function searchHotels() {
  hotelQuery.pageNum = 1
  loadHotels()
}

function resetHotels() {
  hotelQuery.keyword = ''
  hotelQuery.city = ''
  hotelQuery.status = null
  searchHotels()
}

function onHotelPage(p) {
  hotelQuery.pageNum = p
  loadHotels()
}

async function toggleHotelStatus(row, checked) {
  const next = checked ? 1 : 0
  await updateHotelStatus(row.id, next)
  row.status = next
  ElMessage.success(next === 1 ? '已上架' : '已下架')
}

/* ---------- 新增 / 编辑 ---------- */

const hotelDialog = ref(false)
const hotelSaving = ref(false)
const hotelFormRef = ref()

const emptyHotel = () => ({
  id: null,
  name: '',
  city: '',
  area: '',
  address: '',
  level: '',
  pricePerNight: 300,
  rating: null,
  phone: '',
  tags: '',
  description: '',
  status: 1
})

const hotelForm = reactive(emptyHotel())

const hotelRules = {
  name: [{ required: true, message: '请输入酒店名称', trigger: 'blur' }],
  city: [{ required: true, message: '请输入所在城市', trigger: 'blur' }],
  pricePerNight: [{ required: true, message: '请输入每晚参考价', trigger: 'blur' }]
}

function openHotelDialog(row) {
  Object.assign(hotelForm, emptyHotel())
  if (row) {
    Object.assign(hotelForm, {
      id: row.id,
      name: row.name,
      city: row.city,
      area: row.area || '',
      address: row.address || '',
      level: row.level || '',
      pricePerNight: row.pricePerNight ?? 0,
      rating: row.rating === null || row.rating === undefined ? null : Number(row.rating),
      phone: row.phone || '',
      tags: row.tags || '',
      description: row.description || '',
      status: row.status === 0 ? 0 : 1
    })
  }
  hotelDialog.value = true
  if (hotelFormRef.value) hotelFormRef.value.clearValidate()
}

async function submitHotel() {
  await hotelFormRef.value.validate(async (valid) => {
    if (!valid) return
    hotelSaving.value = true
    const payload = {
      name: hotelForm.name,
      city: hotelForm.city,
      area: hotelForm.area,
      address: hotelForm.address,
      level: hotelForm.level,
      pricePerNight: hotelForm.pricePerNight,
      rating: hotelForm.rating,
      phone: hotelForm.phone,
      tags: hotelForm.tags,
      description: hotelForm.description,
      status: hotelForm.status
    }
    try {
      if (hotelForm.id) {
        await updateHotel(hotelForm.id, payload)
        ElMessage.success('酒店已更新')
      } else {
        await createHotel(payload)
        ElMessage.success('酒店已添加')
      }
      hotelDialog.value = false
      loadHotels()
      loadCities()
      loadStats()
    } finally {
      hotelSaving.value = false
    }
  })
}

async function removeHotel(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除酒店「${row.name}」吗？删除后不再出现在后台列表中。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteHotel(row.id)
  ElMessage.success('已删除')
  if (hotels.value.length === 1 && hotelQuery.pageNum > 1) {
    hotelQuery.pageNum -= 1
  }
  loadHotels()
  loadCities()
  loadStats()
}

/* ------------------------------ 初始化 ------------------------------ */

async function onTabChange(name) {
  if (name === 'overview') loadStats()
  if (name === 'users') loadUsers()
  if (name === 'hotels') {
    loadHotels()
    loadCities()
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(154px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.stat-card {
  background: #fff;
  border: 0.5px solid var(--border);
  border-radius: var(--radius-card);
  padding: 14px 16px;
}

.stat-label {
  font-size: var(--fs-mini);
  color: var(--text-3);
  margin-bottom: 6px;
}

.stat-value {
  font-size: 24px;
  font-weight: 500;
  line-height: 1.2;
  letter-spacing: -0.4px;
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.stat-sub {
  margin-top: 6px;
  font-size: var(--fs-mini);
  color: var(--text-3);
}

.sec-title {
  margin: 0 0 16px;
  font-size: var(--fs-section);
  font-weight: 600;
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  height: 190px;
  padding: 0 4px 4px;
}

.bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.bar-val {
  font-size: var(--fs-mini);
  color: var(--text-2);
  margin-bottom: 4px;
  font-variant-numeric: tabular-nums;
}

.bar-val.is-zero {
  color: var(--text-3);
}

.bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.bar {
  width: 60%;
  max-width: 28px;
  background: var(--brand);
  border-radius: 6px 6px 0 0;
  transition: height 0.25s ease;
}

.bar.is-zero {
  background: #e6eaf2;
  border-radius: 4px;
  min-height: 5px;
  height: 5px !important;
}

.bar-label {
  margin-top: 8px;
  font-size: 12px;
  color: #8a909c;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.spacer {
  flex: 1;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 820px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
