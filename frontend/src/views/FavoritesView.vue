<template>
  <div class="page">
    <h2 class="page-title">我的地点收藏</h2>
    <p class="page-desc">在行程详情页点击「收藏」即可把感兴趣的地点存到这里，方便后续规划时复用。</p>

    <div class="card">
      <el-empty v-if="!list.length" description="还没有收藏任何地点" />
      <el-table v-else :data="list" border stripe>
        <el-table-column prop="name" label="地点" min-width="160" show-overflow-tooltip />
        <el-table-column prop="city" label="城市" width="120" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="rating" label="评分" width="100" />
        <el-table-column label="收藏时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchFavorites, deleteFavorite } from '@/api/favorite'

const list = ref([])

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  const res = await fetchFavorites()
  list.value = res.data || []
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定取消收藏「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteFavorite(row.id)
      ElMessage.success('已取消收藏')
      load()
    })
    .catch(() => {})
}

onMounted(load)
</script>
