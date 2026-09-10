<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="记录ID">
          <el-input-number v-model="recordId" :controls="false" :min="1" placeholder="如 12"
                           style="width: 120px" />
        </el-form-item>
        <el-form-item label="实体类型">
          <el-select v-model="entityType" placeholder="全部" clearable style="width: 140px">
            <el-option label="噪声记录" value="RECORD" />
            <el-option label="异常事件" value="ANOMALY" />
            <el-option label="证据" value="EVIDENCE" />
            <el-option label="采样批次" value="BATCH" />
            <el-option label="传感器" value="SENSOR" />
            <el-option label="批量导入" value="IMPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="action" placeholder="全部" clearable style="width: 140px">
            <el-option label="创建" value="CREATE" />
            <el-option label="状态变更" value="STATUS_CHANGE" />
            <el-option label="证据关联" value="EVIDENCE_LINK" />
            <el-option label="批次创建" value="BATCH_CREATE" />
            <el-option label="导入" value="IMPORT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="logs" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="链序号" width="80">
          <template #default="{ row }">
            <el-tag size="small" type="info">#{{ row.chainSeq }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recordId" label="记录ID" width="90">
          <template #default="{ row }">{{ row.recordId || '—' }}</template>
        </el-table-column>
        <el-table-column prop="entityType" label="实体类型" width="100" />
        <el-table-column prop="action" label="动作" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="actionType(row.action)">{{ actionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="entityId" label="实体标识" width="190" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="createdAt" label="时间" width="190" />
        <el-table-column label="条目哈希" min-width="180">
          <template #default="{ row }"><span class="hash-text">{{ row.entryHash }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.recordId" link type="primary" @click="openHistory(row)">回放链</el-button>
            <el-button link type="info" @click="showDetail(row)">快照</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="loadError ? '加载失败' : '暂无审计日志'">
            <el-button v-if="loadError" type="primary" @click="load">重试</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
                       :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
                       @size-change="onSizeChange" @current-change="load" :disabled="loading" />
      </div>
    </div>

    <!-- 快照详情 -->
    <el-dialog v-model="detailVisible" title="审计条目快照" width="720px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="链序号 / 前驱">
          #{{ current.chainSeq }} ｜ prev: <span class="hash-text">{{ current.prevHash }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="本条目哈希">
          <span class="hash-text">{{ current.entryHash }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="操作人 / IP / 时间">
          {{ current.operator }} / {{ current.operatorIp || '—' }} / {{ current.createdAt }}
        </el-descriptions-item>
        <el-descriptions-item label="变更前">
          <pre class="json-box">{{ prettyJson(current.beforeJson) || '—' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="变更后">
          <pre class="json-box">{{ prettyJson(current.afterJson) || '—' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { auditApi, type AuditLog } from '@/api'
import { prettyJson } from '@/utils/hash'

const router = useRouter()
const loading = ref(false)
const loadError = ref(false)
const logs = ref<AuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const recordId = ref<number | undefined>()
const entityType = ref('')
const action = ref('')

const detailVisible = ref(false)
const current = ref<AuditLog | null>(null)

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const data = await auditApi.logs({
      page: page.value,
      size: size.value,
      recordId: recordId.value,
      entityType: entityType.value || undefined,
      action: action.value || undefined
    })
    logs.value = data.records
    total.value = Number(data.total)
  } catch {
    logs.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  recordId.value = undefined
  entityType.value = ''
  action.value = ''
  page.value = 1
  load()
}

function onSizeChange() {
  page.value = 1
  load()
}

function openHistory(row: AuditLog) {
  router.push({ name: 'record-detail', params: { id: row.recordId } })
}

function showDetail(row: AuditLog) {
  current.value = row
  detailVisible.value = true
}

function actionLabel(a: string) {
  return {
    CREATE: '创建',
    STATUS_CHANGE: '状态变更',
    HANDLE: '处理',
    EVIDENCE_LINK: '证据关联',
    BATCH_CREATE: '批次创建',
    SENSOR_CREATE: '传感器创建',
    IMPORT: '导入'
  }[a] || a
}

function actionType(a: string) {
  return ({
    CREATE: 'success',
    STATUS_CHANGE: 'warning',
    HANDLE: 'warning',
    EVIDENCE_LINK: 'primary',
    IMPORT: 'info'
  } as Record<string, string>)[a] || 'info' as any
}

onMounted(load)
</script>

<style scoped>
.json-box {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f7f8fa;
  border-radius: 4px;
  padding: 8px;
  font-size: 12px;
  max-height: 260px;
  overflow: auto;
}
</style>
