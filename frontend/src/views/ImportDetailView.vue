<template>
  <div class="page-container">
    <el-page-header @back="router.back()" content="导入结果明细" />

    <el-card v-loading="loading" shadow="never" class="block">
      <template #header>
        <div class="card-head">
          <span class="section-title">
            {{ task?.importNo }}
            <StatusTag v-if="task" :status="task.status" kind="import" />
          </span>
          <span v-if="task">
            {{ task.fileName }} · 共 {{ task.totalCount }} 条，
            <span class="ok">成功 {{ task.successCount }}</span> /
            <span class="fail">失败 {{ task.failCount }}</span> · 操作人 {{ task.operator }}
          </span>
        </div>
      </template>

      <el-table :data="items" border size="small">
        <el-table-column prop="rowIndex" label="行号" width="70" />
        <el-table-column prop="sensorCode" label="传感器" width="130" />
        <el-table-column prop="sampleTime" label="采样时间" width="190" />
        <el-table-column prop="dbValue" label="分贝" width="90" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recordNo" label="记录编号" width="200">
          <template #default="{ row }">{{ row.recordNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ reason: !row.success }">{{ row.failReason || '—' }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loadError" description="该任务没有明细记录" />
          <el-empty v-else description="明细加载失败">
            <el-button type="primary" @click="load">重试</el-button>
          </el-empty>
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { importApi, type ImportItem, type ImportTask } from '@/api'
import StatusTag from '@/components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)

const loading = ref(false)
const loadError = ref(false)
const task = ref<ImportTask | null>(null)
const items = ref<ImportItem[]>([])

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const [t, its] = await Promise.all([importApi.detail(taskId), importApi.items(taskId)])
    task.value = t
    items.value = its
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.block {
  margin-top: 12px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ok {
  color: #67c23a;
  font-weight: 600;
}
.fail {
  color: #f56c6c;
  font-weight: 600;
}
.reason {
  color: #f56c6c;
}
</style>
