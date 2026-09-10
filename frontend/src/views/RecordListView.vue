<template>
  <div class="page-container" v-loading="loading">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="传感器">
          <el-select v-model="query.sensorCode" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="s in sensors" :key="s.id" :label="`${s.sensorCode} ${s.name}`" :value="s.sensorCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="采样时间">
          <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
                          start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DDTHH:mm:ss"
                          :default-time="[new Date(2026, 8, 1, 0, 0), new Date(2026, 8, 30, 23, 59)]"
                          style="width: 360px" />
        </el-form-item>
        <el-form-item label="异常状态">
          <el-select v-model="query.anomalyStatus" placeholder="全部" clearable style="width: 130px">
            <el-option label="正常" value="NONE" />
            <el-option label="有异常" value="ANY" />
            <el-option label="待处理" value="OPEN" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已解决" value="RESOLVED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
        </el-form-item>
        <el-form-item label="分贝区间">
          <el-input-number v-model="query.dbMin" :min="0" :max="200" :controls="false" placeholder="最低"
                           style="width: 90px" />
          <span style="margin: 0 6px">~</span>
          <el-input-number v-model="query.dbMax" :min="0" :max="200" :controls="false" placeholder="最高"
                           style="width: 90px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="openCreate">手工录入</el-button>
        <el-button type="warning" :icon="Promotion" :loading="concurrentLoading"
                   @click="simulateConcurrent">并发重复提交演示</el-button>
        <el-button :icon="Upload" @click="router.push({ name: 'imports' })">批量导入</el-button>
      </div>

      <el-table :data="records" stripe @row-click="openDetail" style="width: 100%"
                :empty-text="loading ? '加载中...' : '暂无采样记录'">
        <el-table-column prop="recordNo" label="记录编号" width="190" show-overflow-tooltip />
        <el-table-column prop="sensorCode" label="传感器" width="120" />
        <el-table-column prop="batchNo" label="采样批次" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.batchNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="sampleTime" label="采样时间" width="180" />
        <el-table-column label="分贝 dB(A)" width="120" sortable :sort-by="'dbValue'">
          <template #default="{ row }">
            <span :class="{ 'db-high': row.dbValue > 85 }">{{ row.dbValue }}</span>
          </template>
        </el-table-column>
        <el-table-column label="异常状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.anomalyStatus" /></template>
        </el-table-column>
        <el-table-column prop="createdBy" label="录入人" width="110" />
        <el-table-column prop="createdAt" label="入库时间" width="190" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">证据链/详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="loadError ? '数据加载失败，请稍后重试' : '暂无符合条件的采样记录'">
            <el-button v-if="loadError" type="primary" @click="loadData">重新加载</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size"
                       :total="total" :page-sizes="[10, 20, 50, 100]"
                       layout="total, sizes, prev, pager, next, jumper"
                       @size-change="onSizeChange" @current-change="loadData"
                       :disabled="loading" />
      </div>
    </div>

    <!-- 手工录入弹窗 -->
    <el-dialog v-model="createVisible" title="录入噪声采样记录" width="640px" @closed="onDialogClosed">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="传感器" prop="sensorCode">
          <el-select v-model="form.sensorCode" placeholder="选择传感器" filterable style="width: 100%">
            <el-option v-for="s in sensors" :key="s.id" :label="`${s.sensorCode} ${s.name}`" :value="s.sensorCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="采样批次">
          <el-select v-model="form.batchNo" placeholder="不关联批次" clearable filterable style="width: 100%">
            <el-option v-for="b in formBatches" :key="b.id" :label="`${b.batchNo} (${b.startTime}~${b.endTime})`"
                       :value="b.batchNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="采样时间" prop="sampleTime">
          <el-date-picker v-model="form.sampleTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                          placeholder="选择采样时间" style="width: 100%" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="经度" prop="longitude">
              <el-input-number v-model="form.longitude" :precision="7" :step="0.0001" :controls="false"
                               style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" prop="latitude">
              <el-input-number v-model="form.latitude" :precision="7" :step="0.0001" :controls="false"
                               style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="分贝值" prop="dbValue">
          <el-input-number v-model="form.dbValue" :min="0" :max="200" :precision="2" :controls="false"
                           style="width: 200px" />
          <span class="hint">超过 85 dB(A) 将自动生成异常事件</span>
        </el-form-item>
        <el-form-item label="频谱摘要">
          <el-input v-model="form.spectrumSummary" type="textarea" :rows="2"
                    placeholder='如 {"bands":[{"hz":63,"db":55.2}]}' />
        </el-form-item>
        <el-form-item label="原始数据哈希" prop="rawDataHash">
          <el-input v-model="form.rawDataHash" placeholder="64 位 SHA-256" clearable />
          <el-button link type="primary" @click="fillDemoHash">用示例内容生成哈希</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus, Upload, Promotion } from '@element-plus/icons-vue'
import { recordApi, sensorApi, batchApi, type RecordItem, type Sensor, type SamplingBatch } from '@/api'
import { ApiBusinessError } from '@/api/http'
import StatusTag from '@/components/StatusTag.vue'
import { sha256Hex } from '@/utils/hash'

const router = useRouter()
const loading = ref(false)
const loadError = ref(false)
const records = ref<RecordItem[]>([])
const total = ref(0)
const sensors = ref<Sensor[]>([])
const timeRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1,
  size: 10,
  sensorCode: '',
  anomalyStatus: '',
  dbMin: undefined as number | undefined,
  dbMax: undefined as number | undefined
})

async function loadData() {
  loading.value = true
  loadError.value = false
  try {
    const data = await recordApi.page({
      page: query.page,
      size: query.size,
      sensorCode: query.sensorCode || undefined,
      anomalyStatus: query.anomalyStatus || undefined,
      dbMin: query.dbMin,
      dbMax: query.dbMax,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1]
    })
    records.value = data.records
    total.value = Number(data.total)
  } catch {
    records.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadData()
}

function onReset() {
  query.page = 1
  query.sensorCode = ''
  query.anomalyStatus = ''
  query.dbMin = undefined
  query.dbMax = undefined
  timeRange.value = null
  loadData()
}

function onSizeChange() {
  query.page = 1
  loadData()
}

function openDetail(row: RecordItem) {
  router.push({ name: 'record-detail', params: { id: row.id } })
}

// ---------------- 手工录入 ----------------
const createVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const formBatches = ref<SamplingBatch[]>([])

const emptyForm = () => ({
  sensorCode: '',
  batchNo: '',
  sampleTime: '',
  longitude: 118.7788 as number | undefined,
  latitude: 32.0417 as number | undefined,
  dbValue: 70,
  spectrumSummary: '',
  rawDataHash: ''
})
const form = reactive(emptyForm())

const rules = {
  sensorCode: [{ required: true, message: '请选择传感器', trigger: 'change' }],
  sampleTime: [{ required: true, message: '请选择采样时间', trigger: 'change' }],
  dbValue: [{ required: true, message: '请输入分贝值', trigger: 'blur' }],
  rawDataHash: [
    { required: true, message: '请输入原始数据 SHA-256', trigger: 'blur' },
    { pattern: /^[a-fA-F0-9]{64}$/, message: '必须为 64 位十六进制', trigger: 'blur' }
  ]
}

async function openCreate() {
  createVisible.value = true
  if (sensors.value.length === 0) {
    sensors.value = await sensorApi.list().catch(() => [])
  }
}

watch(() => form.sensorCode, async (code) => {
  form.batchNo = ''
  if (code) {
    formBatches.value = await batchApi.list(code).catch(() => [])
  } else {
    formBatches.value = []
  }
})

async function fillDemoHash() {
  const content = `raw|${form.sensorCode || 'S-DEMO'}|${form.sampleTime || Date.now()}|${form.dbValue}|${Math.floor(Date.now() / 1000)}`
  form.rawDataHash = await sha256Hex(content)
}

async function submitForm() {
  // 防重复提交：请求进行中直接忽略后续点击
  if (submitting.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await recordApi.create({
      sensorCode: form.sensorCode,
      batchNo: form.batchNo || undefined,
      sampleTime: form.sampleTime,
      longitude: form.longitude,
      latitude: form.latitude,
      dbValue: form.dbValue,
      spectrumSummary: form.spectrumSummary || undefined,
      rawDataHash: form.rawDataHash
    })
    ElMessage.success('录入成功')
    createVisible.value = false
    onSearch()
  } catch (e) {
    if (e instanceof ApiBusinessError) {
      // 重复提交/并发冲突给出明确提示（拦截器已弹消息，这里补充标题）
      if (e.code === 40901) {
        ElMessage.warning('该记录已存在（同传感器 + 同采样时间 + 同哈希），未重复入库')
      } else if (e.code === 40903) {
        ElMessage.warning('并发冲突：同一记录正在被处理，请稍后重试')
      }
    }
  } finally {
    submitting.value = false
  }
}

function onDialogClosed() {
  Object.assign(form, emptyForm())
  formRef.value?.clearValidate()
}

// ---------------- 并发重复提交演示 ----------------
const concurrentLoading = ref(false)
async function simulateConcurrent() {
  if (concurrentLoading.value) return
  concurrentLoading.value = true
  try {
    const code = sensors.value[0]?.sensorCode || 'S-NJ-001'
    const sampleTime = '2026-09-10T08:00:00'
    const hash = await sha256Hex(`concurrent-demo-${Date.now()}`)
    const payload = {
      sensorCode: code,
      sampleTime,
      longitude: 118.7788,
      latitude: 32.0417,
      dbValue: 76.5,
      rawDataHash: hash
    }
    // 同时发出 5 个完全相同的请求
    const results = await Promise.allSettled(Array.from({ length: 5 }, () => recordApi.create(payload)))
    const fulfilled = results.filter((r) => r.status === 'fulfilled').length
    const rejected = results.filter((r) => r.status === 'rejected').length
    ElMessage({
      type: fulfilled === 1 ? 'success' : 'warning',
      duration: 6000,
      message: `并发结果：5 个相同请求，仅 ${fulfilled} 条入库，${rejected} 条被判定为重复/并发冲突，数据库无重复数据。`
    })
    onSearch()
  } finally {
    concurrentLoading.value = false
  }
}

onMounted(async () => {
  sensors.value = await sensorApi.list().catch(() => [])
  await loadData()
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
}
.db-high {
  color: #f56c6c;
  font-weight: 700;
}
.hint {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
</style>
