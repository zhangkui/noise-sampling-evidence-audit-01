<template>
  <div class="page-container">
    <el-card shadow="never" class="block">
      <template #header>
        <div class="card-head">
          <span class="section-title">批量导入噪声采样记录</span>
          <div>
            <el-button size="small" @click="downloadTemplate">下载导入模板</el-button>
            <el-button size="small" @click="jsonVisible = true">JSON 批量粘贴</el-button>
            <el-button size="small" type="primary" @click="addRow">新增一行</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" border size="small" max-height="360">
        <el-table-column label="#" type="index" width="46" />
        <el-table-column label="传感器编号*" width="150">
          <template #default="{ row }">
            <el-select v-model="row.sensorCode" size="small" filterable placeholder="选择">
              <el-option v-for="s in sensors" :key="s.id" :label="s.sensorCode" :value="s.sensorCode" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="采样时间*" width="200">
          <template #default="{ row }">
            <el-date-picker v-model="row.sampleTime" type="datetime" size="small"
                            value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择时间" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="经度" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.longitude" :controls="false" size="small" :precision="6"
                             :min="-180" :max="180" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="纬度" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.latitude" :controls="false" size="small" :precision="6"
                             :min="-90" :max="90" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="分贝*" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.dbValue" :controls="false" size="small" :min="0" :max="200"
                             :precision="2" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="原始数据哈希(SHA-256)*" min-width="240">
          <template #default="{ row }">
            <el-input v-model="row.rawDataHash" size="small" placeholder="64位十六进制">
              <template #append>
                <el-button @click="genHash(row)">生成</el-button>
              </template>
            </el-input>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="rows.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无待导入数据，点击右上角新增或粘贴 JSON" :image-size="60" />
        </template>
      </el-table>

      <div class="submit-bar">
        <el-button type="primary" size="large" :loading="importing" :disabled="rows.length === 0"
                   @click="submitImport">
          提交导入（{{ rows.length }} 条）
        </el-button>
        <span class="muted">失败的行会逐条给出原因且不会写入数据库；重复数据仅首条入库。</span>
      </div>
    </el-card>

    <!-- 本次导入逐条结果 -->
    <el-card v-if="result" shadow="never" class="block">
      <template #header>
        <div class="card-head">
          <span class="section-title">
            导入结果 {{ result.importNo }}
            <StatusTag :status="result.status" kind="import" />
          </span>
          <span>
            共 {{ result.totalCount }} 条 ·
            <span class="ok">成功 {{ result.successCount }}</span> ·
            <span class="fail">失败 {{ result.failCount }}</span>
          </span>
        </div>
      </template>
      <el-table :data="result.items" border size="small">
        <el-table-column prop="rowIndex" label="行号" width="70" />
        <el-table-column prop="sensorCode" label="传感器" width="130" />
        <el-table-column prop="sampleTime" label="采样时间" width="180" />
        <el-table-column prop="dbValue" label="分贝" width="90" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="recordNo" label="生成记录号" width="200">
          <template #default="{ row }">{{ row.recordNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'reason': !row.success }">{{ row.failReason || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 历史导入任务 -->
    <el-card shadow="never" class="block">
      <template #header><span class="section-title">历史导入任务</span></template>
      <el-table v-loading="historyLoading" :data="tasks" border size="small"
                @row-click="(row: ImportTask) => router.push({ name: 'import-detail', params: { id: row.id } })"
                style="cursor: pointer">
        <el-table-column prop="importNo" label="任务编号" width="220" />
        <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
        <el-table-column prop="totalCount" label="总数" width="80" />
        <el-table-column prop="successCount" label="成功" width="80" />
        <el-table-column prop="failCount" label="失败" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" kind="import" /></template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="createdAt" label="时间" width="190" />
        <template #empty>
          <el-empty description="暂无导入任务" :image-size="60" />
        </template>
      </el-table>
      <div class="pagination-bar">
        <el-pagination v-model:current-page="taskPage" v-model:page-size="taskSize" :total="taskTotal"
                       layout="total, prev, pager, next" @current-change="loadTasks" />
      </div>
    </el-card>

    <!-- JSON 粘贴弹窗 -->
    <el-dialog v-model="jsonVisible" title="JSON 批量粘贴" width="640px">
      <el-alert type="info" :closable="false" class="block">
        <template #title>
          粘贴记录数组 JSON，字段：sensorCode、sampleTime(yyyy-MM-ddTHH:mm:ss)、dbValue、
          longitude、latitude、rawDataHash
        </template>
      </el-alert>
      <el-input v-model="jsonText" type="textarea" :rows="12"
                placeholder='[{"sensorCode":"S-NJ-001","sampleTime":"2026-09-10T10:00:00","dbValue":72.5,"rawDataHash":"...64位..."}]' />
      <template #footer>
        <el-button @click="jsonVisible = false">取消</el-button>
        <el-button type="primary" @click="parseJson">解析并追加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  importApi,
  sensorApi,
  type ImportResult,
  type ImportTask,
  type Sensor
} from '@/api'
import StatusTag from '@/components/StatusTag.vue'
import { sha256Hex } from '@/utils/hash'

const router = useRouter()
const sensors = ref<Sensor[]>([])

interface ImportRow {
  sensorCode: string
  sampleTime: string
  longitude?: number
  latitude?: number
  dbValue?: number
  spectrumSummary?: string
  rawDataHash: string
}

const rows = ref<ImportRow[]>([])
const importing = ref(false)
const result = ref<ImportResult | null>(null)

function addRow() {
  rows.value.push({
    sensorCode: sensors.value[0]?.sensorCode || '',
    sampleTime: '',
    longitude: 118.7788,
    latitude: 32.0417,
    dbValue: 70,
    rawDataHash: ''
  })
}

async function genHash(row: ImportRow) {
  row.rawDataHash = await sha256Hex(
    `raw|${row.sensorCode}|${row.sampleTime}|${row.dbValue}|${Date.now()}`
  )
}

async function submitImport() {
  // 防重复提交
  if (importing.value) return
  if (rows.value.some((r) => !r.sensorCode || !r.sampleTime || r.dbValue === undefined || !r.rawDataHash)) {
    ElMessage.warning('存在未填写完整的行（传感器/时间/分贝/哈希必填）')
    return
  }
  importing.value = true
  try {
    const res = await importApi.doImport({
      fileName: `web-import-${new Date().toISOString().slice(0, 19)}.json`,
      records: rows.value.map((r) => ({
        sensorCode: r.sensorCode,
        sampleTime: r.sampleTime,
        longitude: r.longitude,
        latitude: r.latitude,
        dbValue: r.dbValue!,
        spectrumSummary: r.spectrumSummary,
        rawDataHash: r.rawDataHash
      }))
    })
    result.value = res
    if (res.failCount === 0) {
      ElMessage.success(`全部 ${res.successCount} 条导入成功`)
      rows.value = []
    } else {
      ElMessage.warning(`成功 ${res.successCount} 条，失败 ${res.failCount} 条，失败行未写入数据库`)
    }
    loadTasks()
  } catch {
    // 拦截器已提示（如整体参数错误）
  } finally {
    importing.value = false
  }
}

// JSON 粘贴
const jsonVisible = ref(false)
const jsonText = ref('')
function parseJson() {
  try {
    const arr = JSON.parse(jsonText.value)
    if (!Array.isArray(arr)) throw new Error('not array')
    const valid = arr
      .filter((r) => r && typeof r === 'object')
      .map((r) => ({
        sensorCode: r.sensorCode || '',
        sampleTime: r.sampleTime || '',
        longitude: r.longitude ?? 118.7788,
        latitude: r.latitude ?? 32.0417,
        dbValue: typeof r.dbValue === 'number' ? r.dbValue : undefined,
        spectrumSummary: r.spectrumSummary,
        rawDataHash: (r.rawDataHash || '').toLowerCase()
      }))
    rows.value.push(...valid)
    ElMessage.success(`已追加 ${valid.length} 行`)
    jsonVisible.value = false
    jsonText.value = ''
  } catch {
    ElMessage.error('JSON 解析失败，请确认是记录数组格式')
  }
}

function downloadTemplate() {
  const template = [
    {
      sensorCode: 'S-NJ-001',
      sampleTime: '2026-09-10T10:00:00',
      longitude: 118.7788,
      latitude: 32.0417,
      dbValue: 72.5,
      spectrumSummary: '{"bands":[{"hz":63,"db":62.5}]}',
      rawDataHash: '0000000000000000000000000000000000000000000000000000000000000000'
    }
  ]
  const blob = new Blob([JSON.stringify(template, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'noise-import-template.json'
  a.click()
  URL.revokeObjectURL(url)
}

// 历史任务
const tasks = ref<ImportTask[]>([])
const historyLoading = ref(false)
const taskPage = ref(1)
const taskSize = ref(10)
const taskTotal = ref(0)

async function loadTasks() {
  historyLoading.value = true
  try {
    const data = await importApi.page({ page: taskPage.value, size: taskSize.value })
    tasks.value = data.records
    taskTotal.value = Number(data.total)
  } catch {
    tasks.value = []
  } finally {
    historyLoading.value = false
  }
}

onMounted(async () => {
  sensors.value = await sensorApi.list().catch(() => [])
  addRow()
  addRow()
  loadTasks()
})
</script>

<style scoped>
.block {
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.submit-bar {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.muted {
  color: #909399;
  font-size: 12px;
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
