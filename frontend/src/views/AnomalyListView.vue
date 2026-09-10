<template>
  <div class="page-container" v-loading="loading">
    <div class="filter-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="异常状态">
          <el-select v-model="status" placeholder="全部" clearable style="width: 150px" @change="onSearch">
            <el-option label="待处理" value="OPEN" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已解决" value="RESOLVED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table :data="anomalies" stripe @row-click="openRecord" style="width: 100%">
        <el-table-column prop="eventNo" label="事件编号" width="200" show-overflow-tooltip />
        <el-table-column prop="recordId" label="记录ID" width="90" />
        <el-table-column label="分贝/阈值" width="130">
          <template #default="{ row }">{{ row.dbValue }} / {{ row.thresholdValue }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span :class="{ highlight: highlightId === row.id }">
              <StatusTag :status="row.status" />
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="handledBy" label="处理人" width="110">
          <template #default="{ row }">{{ row.handledBy || '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="190" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openRecord(row)">证据链</el-button>
            <el-button v-if="row.status === 'OPEN' || row.status === 'PROCESSING'"
                       link type="warning" @click.stop="openHandle(row)">处理</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="loadError ? '数据加载失败' : '暂无异常事件'">
            <el-button v-if="loadError" type="primary" @click="loadData">重新加载</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
                       :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
                       @size-change="onSizeChange" @current-change="loadData" :disabled="loading" />
      </div>
    </div>

    <!-- 处理弹窗 -->
    <el-dialog v-model="handleVisible" title="异常处理" width="560px">
      <el-form label-width="90px" v-if="current">
        <el-form-item label="事件编号">{{ current.eventNo }}</el-form-item>
        <el-form-item label="异常描述">{{ current.description }}</el-form-item>
        <el-form-item label="处理状态" required>
          <el-radio-group v-model="handleForm.status">
            <el-radio value="PROCESSING">处理中</el-radio>
            <el-radio value="RESOLVED">已解决</el-radio>
            <el-radio value="IGNORED">已忽略</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明" required>
          <el-input v-model="handleForm.handleNote" type="textarea" :rows="4"
                    placeholder="请填写现场核查与处置过程，该说明将写入不可篡改审计日志" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">提交处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { anomalyApi, type Anomaly } from '@/api'
import StatusTag from '@/components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loadError = ref(false)
const anomalies = ref<Anomaly[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')
const highlightId = ref(Number(route.query.highlight || 0))

async function loadData() {
  loading.value = true
  loadError.value = false
  try {
    const data = await anomalyApi.page({ page: page.value, size: size.value, status: status.value || undefined })
    anomalies.value = data.records
    total.value = Number(data.total)
  } catch {
    anomalies.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadData()
}

function onReset() {
  status.value = ''
  page.value = 1
  loadData()
}

function onSizeChange() {
  page.value = 1
  loadData()
}

function openRecord(row: Anomaly) {
  router.push({ name: 'record-detail', params: { id: row.recordId } })
}

// 处理弹窗（防重复提交）
const handleVisible = ref(false)
const submitting = ref(false)
const current = ref<Anomaly | null>(null)
const handleForm = reactive({ status: 'RESOLVED', handleNote: '' })

function openHandle(row: Anomaly) {
  current.value = row
  handleForm.status = row.status === 'PROCESSING' ? 'PROCESSING' : 'RESOLVED'
  handleForm.handleNote = row.handleNote || ''
  handleVisible.value = true
}

async function submitHandle() {
  if (submitting.value) return
  if (!handleForm.handleNote.trim()) {
    ElMessage.warning('请填写处理说明')
    return
  }
  submitting.value = true
  try {
    await anomalyApi.handle(current.value!.id, {
      status: handleForm.status,
      handleNote: handleForm.handleNote.trim()
    })
    ElMessage.success('处理已提交，状态变更与处理说明已写入审计哈希链')
    handleVisible.value = false
    await loadData()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.highlight {
  display: inline-block;
  padding: 2px 6px;
  background: #fdf6ec;
  border-radius: 4px;
  animation: flash 1.6s ease 2;
}
@keyframes flash {
  50% {
    background: #e6a23c;
  }
}
</style>
