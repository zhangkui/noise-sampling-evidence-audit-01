<template>
  <div class="page-container">
    <el-tabs v-model="tab" class="tabs-card">
      <!-- 传感器 -->
      <el-tab-pane label="传感器" name="sensors">
        <div class="table-card">
          <div class="toolbar">
            <el-button type="primary" :icon="Plus" @click="sensorVisible = true">新增传感器</el-button>
          </div>
          <el-table v-loading="sensorLoading" :data="sensors" border>
            <el-table-column prop="sensorCode" label="传感器编号" width="150" />
            <el-table-column prop="name" label="名称" width="200" />
            <el-table-column prop="location" label="安装位置" show-overflow-tooltip />
            <el-table-column label="经纬度" width="220">
              <template #default="{ row }">
                {{ row.longitude ?? '—' }}, {{ row.latitude ?? '—' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><StatusTag :status="row.status" kind="sensor" /></template>
            </el-table-column>
            <template #empty>
              <el-empty :description="sensorError ? '加载失败' : '暂无传感器'">
                <el-button v-if="sensorError" type="primary" @click="loadSensors">重试</el-button>
              </el-empty>
            </template>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 采样批次 -->
      <el-tab-pane label="采样批次" name="batches">
        <div class="filter-bar" style="margin-bottom: 16px">
          <el-form :inline="true" @submit.prevent>
            <el-form-item label="传感器">
              <el-select v-model="batchSensor" placeholder="全部" clearable filterable style="width: 240px"
                         @change="loadBatches">
                <el-option v-for="s in sensors" :key="s.id" :label="`${s.sensorCode} ${s.name}`"
                           :value="s.sensorCode" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Plus" @click="openBatchDialog">新增批次</el-button>
            </el-form-item>
          </el-form>
        </div>
        <div class="table-card">
          <el-table v-loading="batchLoading" :data="batches" border @row-click="onBatchRow">
            <el-table-column prop="batchNo" label="批次编号" width="200" />
            <el-table-column prop="sensorCode" label="传感器" width="130" />
            <el-table-column prop="startTime" label="开始时间" width="190" />
            <el-table-column prop="endTime" label="结束时间" width="190" />
            <el-table-column prop="purpose" label="采样目的" show-overflow-tooltip />
            <el-table-column prop="operator" label="负责人" width="110" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="checkOverlap(row)">冲突检测</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :description="batchError ? '加载失败' : '暂无采样批次'">
                <el-button v-if="batchError" type="primary" @click="loadBatches">重试</el-button>
              </el-empty>
            </template>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增传感器 -->
    <el-dialog v-model="sensorVisible" title="新增传感器" width="520px">
      <el-form ref="sensorFormRef" :model="sensorForm" :rules="sensorRules" label-width="90px">
        <el-form-item label="编号" prop="sensorCode">
          <el-input v-model="sensorForm.sensorCode" placeholder="如 S-NJ-005" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="sensorForm.name" />
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="sensorForm.location" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="经度">
              <el-input-number v-model="sensorForm.longitude" :controls="false" :precision="6"
                               :min="-180" :max="180" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度">
              <el-input-number v-model="sensorForm.latitude" :controls="false" :precision="6"
                               :min="-90" :max="90" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="sensorVisible = false">取消</el-button>
        <el-button type="primary" :loading="sensorSubmitting" @click="submitSensor">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增批次（带重叠检测） -->
    <el-dialog v-model="batchVisible" title="新增采样批次" width="560px">
      <el-form ref="batchFormRef" :model="batchForm" :rules="batchRules" label-width="90px">
        <el-form-item label="传感器" prop="sensorCode">
          <el-select v-model="batchForm.sensorCode" filterable placeholder="选择传感器" style="width: 100%">
            <el-option v-for="s in sensors" :key="s.id" :label="`${s.sensorCode} ${s.name}`"
                       :value="s.sensorCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间区间" prop="timeRange">
          <el-date-picker v-model="batchForm.timeRange" type="datetimerange" range-separator="至"
                          start-placeholder="开始" end-placeholder="结束"
                          value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="采样目的">
          <el-input v-model="batchForm.purpose" />
        </el-form-item>
      </el-form>

      <el-alert v-if="overlapConflicts.length > 0" type="error" :closable="false" show-icon
                title="检测到时间区间重叠，该批次将无法创建" class="block">
        <div v-for="c in overlapConflicts" :key="c.id" class="conflict-line">
          {{ c.batchNo }}：{{ c.startTime }} ~ {{ c.endTime }}（{{ c.purpose }}）
        </div>
      </el-alert>

      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button :loading="checking" @click="onOverlapCheck">仅检测冲突</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatch">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  batchApi,
  sensorApi,
  type SamplingBatch,
  type Sensor
} from '@/api'
import StatusTag from '@/components/StatusTag.vue'
import { ApiBusinessError } from '@/api/http'

const tab = ref('sensors')

// 传感器
const sensors = ref<Sensor[]>([])
const sensorLoading = ref(false)
const sensorError = ref(false)
const sensorVisible = ref(false)
const sensorSubmitting = ref(false)
const sensorFormRef = ref<FormInstance>()
const sensorForm = reactive({
  sensorCode: '',
  name: '',
  location: '',
  longitude: 118.7788 as number | undefined,
  latitude: 32.0417 as number | undefined
})
const sensorRules = {
  sensorCode: [{ required: true, message: '请输入编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

async function loadSensors() {
  sensorLoading.value = true
  sensorError.value = false
  try {
    sensors.value = await sensorApi.list()
  } catch {
    sensors.value = []
    sensorError.value = true
  } finally {
    sensorLoading.value = false
  }
}

async function submitSensor() {
  if (sensorSubmitting.value) return
  const valid = await sensorFormRef.value?.validate().catch(() => false)
  if (!valid) return
  sensorSubmitting.value = true
  try {
    await sensorApi.create({ ...sensorForm })
    ElMessage.success('传感器已创建，变更已留痕')
    sensorVisible.value = false
    sensorForm.sensorCode = ''
    sensorForm.name = ''
    sensorForm.location = ''
    loadSensors()
  } catch (e) {
    if (e instanceof ApiBusinessError && e.code === 40901) {
      ElMessage.error('传感器编号已存在')
    }
  } finally {
    sensorSubmitting.value = false
  }
}

// 批次
const batches = ref<SamplingBatch[]>([])
const batchLoading = ref(false)
const batchError = ref(false)
const batchSensor = ref('')

const batchVisible = ref(false)
const batchSubmitting = ref(false)
const checking = ref(false)
const batchFormRef = ref<FormInstance>()
const overlapConflicts = ref<SamplingBatch[]>([])
const batchForm = reactive({
  sensorCode: '',
  timeRange: null as [string, string] | null,
  purpose: ''
})
const batchRules = {
  sensorCode: [{ required: true, message: '请选择传感器', trigger: 'change' }],
  timeRange: [{ required: true, message: '请选择时间区间', trigger: 'change' }]
}

async function loadBatches() {
  batchLoading.value = true
  batchError.value = false
  try {
    batches.value = await batchApi.list(batchSensor.value || undefined)
  } catch {
    batches.value = []
    batchError.value = true
  } finally {
    batchLoading.value = false
  }
}

function openBatchDialog() {
  batchForm.sensorCode = batchSensor.value || sensors.value[0]?.sensorCode || ''
  batchForm.timeRange = null
  batchForm.purpose = ''
  overlapConflicts.value = []
  batchVisible.value = true
}

async function onOverlapCheck(): Promise<boolean> {
  if (!batchForm.sensorCode || !batchForm.timeRange) {
    ElMessage.warning('请先选择传感器和时间区间')
    return false
  }
  checking.value = true
  try {
    const res = await batchApi.overlapCheck({
      sensorCode: batchForm.sensorCode,
      startTime: batchForm.timeRange[0],
      endTime: batchForm.timeRange[1]
    })
    overlapConflicts.value = res.conflicts
    if (res.overlap) {
      ElMessage.warning(`检测到 ${res.conflicts.length} 个重叠批次`)
    } else {
      ElMessage.success('未检测到时间区间重叠')
    }
    return !res.overlap
  } catch {
    return false
  } finally {
    checking.value = false
  }
}

async function submitBatch() {
  if (batchSubmitting.value) return
  const valid = await batchFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!batchForm.timeRange) return
  batchSubmitting.value = true
  try {
    await batchApi.create({
      sensorCode: batchForm.sensorCode,
      startTime: batchForm.timeRange[0],
      endTime: batchForm.timeRange[1],
      purpose: batchForm.purpose
    })
    ElMessage.success('采样批次已创建')
    batchVisible.value = false
    batchSensor.value = batchForm.sensorCode
    loadBatches()
  } catch (e) {
    if (e instanceof ApiBusinessError && e.code === 40902) {
      // 服务端再次检测到重叠：刷新冲突列表展示
      onOverlapCheck()
    }
  } finally {
    batchSubmitting.value = false
  }
}

async function checkOverlap(row: SamplingBatch) {
  const res = await batchApi.overlapCheck({
    sensorCode: row.sensorCode,
    startTime: row.startTime,
    endTime: row.endTime
  })
  const conflicts = res.conflicts.filter((c) => c.id !== row.id)
  if (conflicts.length === 0) {
    ElMessage.success(`批次 ${row.batchNo} 与其他批次无时间重叠`)
  } else {
    ElMessage.warning(`与 ${conflicts.length} 个批次重叠：${conflicts.map((c) => c.batchNo).join(', ')}`)
  }
}

function onBatchRow(row: SamplingBatch) {
  batchSensor.value = row.sensorCode
}

onMounted(async () => {
  await loadSensors()
  await loadBatches()
})
</script>

<style scoped>
.tabs-card {
  background: transparent;
}
.toolbar {
  margin-bottom: 12px;
}
.block {
  margin-top: 12px;
}
.conflict-line {
  font-size: 12px;
  line-height: 1.8;
}
</style>
