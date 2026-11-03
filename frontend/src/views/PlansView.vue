<template>
  <div class="page">
    <h2 class="page-title">我的历史方案</h2>
    <p class="page-desc">支持按标题 / 目的地检索，点击进入详情查看、编辑或导出。</p>

    <div class="card">
      <div style="display:flex;gap:12px;margin-bottom:16px" class="no-print">
        <el-input v-model="keyword" placeholder="搜索标题或目的地" clearable style="max-width:280px"
          @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load">搜索</el-button>
      </div>

      <el-empty v-if="!list.length" description="暂无行程方案，去首页生成一份吧" />
      <div v-else>
        <el-table :data="list" border stripe>
          <el-table-column prop="title" label="方案标题" min-width="200" show-overflow-tooltip />
          <el-table-column label="路线" min-width="180">
            <template #default="{ row }">{{ row.origin }} → {{ row.destination }}</template>
          </el-table-column>
          <el-table-column prop="days" label="天数" width="80" align="center" />
          <el-table-column prop="budget" label="预算(元)" width="100" align="center" />
          <el-table-column label="生成时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" align="center" class="no-print">
            <template #default="{ row }">
              <el-button link type="primary" @click="$router.push(`/plans/${row.id}`)">查看</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination style="margin-top:16px;justify-content:flex-end" background layout="total, prev, pager, next"
          :total="total" :current-page="pageNum" :page-size="pageSize" @current-change="handlePage" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchPlans, deletePlan } from '@/api/plan'

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  const res = await fetchPlans({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value })
  list.value = res.data.records || []
  total.value = Number(res.data.total || 0)
}

function handlePage(p) {
  pageNum.value = p
  load()
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除方案「${row.title}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      await deletePlan(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>
