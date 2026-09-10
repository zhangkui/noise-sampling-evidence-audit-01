<template>
  <div class="page-container">
    <el-page-header content="记录详情与证据链" @back="router.back()">
      <template #content>
        <span class="header-title">记录详情与证据链</span>
        <el-tag v-if="loaded" :type="history?.chainValid ? 'success' : 'danger'"
                effect="dark" size="small" class="chain-valid-tag">
          {{ history?.chainValid ? '哈希链校验通过' : '哈希链异常：' + history?.brokenAt }}
        </el-tag>
      </template>
    </el-page-header>

    <div v-loading="loading" class="detail-body">
      <el-alert v-if="loadError" type="error" :closable="false" show-icon
                title="详情加载失败" :description="errorMessage" class="block">
        <el-button type="primary" @click="load">重试</el-button>
      </el-alert>

      <template v-else-if="loaded && history">
        <!-- 记录基本信息 -->
        <el-card class="block" shadow="never">
          <template #header><span class="section-title">采样记录（原始数据不可修改）</span></template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="记录编号">{{ record.recordNo }}</el-descriptions-item>
            <el-descriptions-item label="传感器">{{ record.sensorCode }}</el-descriptions-item>
            <el-descriptions-item label="采样批次">{{ record.batchNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="采样时间">{{ record.sampleTime }}</el-descriptions-item>
            <el-descriptions-item label="经度">{{ record.longitude ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="纬度">{{ record.latitude ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="分贝 dB(A)">
              <span :class="{ 'db-high': record.dbValue > 85 }">{{ record.dbValue }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="异常状态">
              <StatusTag :status="record.anomalyStatus" />
            </el-descriptions-item>
            <el-descriptions-item label="录入人">{{ record.createdBy || '—' }}</el-descriptions-item>
            <el-descriptions-item label="原始数据哈希" :span="3">
              <span class="hash-text">{{ record.rawDataHash }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="频谱摘要" :span="3">
              <pre class="json-box">{{ prettyJson(record.spectrumSummary) || '—' }}</pre>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 异常事件 -->
        <el-card class="block" shadow="never">
          <template #header><span class="section-title">异常事件</span></template>
          <el-empty v-if="anomalies.length === 0" description="无异常事件（分贝未超阈值）" :image-size="70" />
          <el-timeline v-else>
            <el-timeline-item v-for="a in anomalies" :key="a.id" :timestamp="a.createdAt" placement="top"
                              :type="a.status === 'OPEN' ? 'danger' : a.status === 'RESOLVED' ? 'success' : 'warning'">
              <el-card shadow="never" class="anomaly-card">
                <div class="anomaly-head">
                  <strong>{{ a.eventNo }}</strong>
                  <StatusTag :status="a.status" />
                </div>
                <p>{{ a.description }}</p>
                <p class="muted">触发值 {{ a.dbValue }} dB(A) / 阈值 {{ a.thresholdValue }} dB(A)</p>
                <p v-if="a.handleNote" class="handle-note">
                  处理说明（{{ a.handledBy }} · {{ a.handledAt }}）：{{ a.handleNote }}
                </p>
                <el-button v-if="a.status !== 'RESOLVED' && a.status !== 'IGNORED'" link type="primary"
                           @click="goHandle(a)">前往处理</el-button>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <!-- 证据版本 -->
        <el-card class="block" shadow="never">
          <template #header>
            <div class="card-head">
              <span class="section-title">证据版本（哈希链）</span>
              <el-button type="primary" size="small" :icon="Link" @click="evidenceVisible = true">
                追加证据版本
              </el-button>
            </div>
          </template>
          <el-empty v-if="history.evidences.length === 0" description="暂无证据版本" :image-size="70" />
          <el-table v-else :data="history.evidences" size="small" border>
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column prop="fileUri" label="证据地址" show-overflow-tooltip />
            <el-table-column prop="changeNote" label="版本说明" show-overflow-tooltip />
            <el-table-column prop="createdBy" label="操作人" width="100" />
            <el-table-column prop="createdAt" label="时间" width="180" />
            <el-table-column label="证据哈希" min-width="200">
              <template #default="{ row }">
                <span class="hash-text">{{ row.evidenceHash }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 审计时间线 -->
        <el-card class="block" shadow="never">
          <template #header><span class="section-title">审计时间线（按记录回放完整变更历史）</span></template>
          <el-empty v-if="history.timeline.length === 0" description="暂无审计记录" :image-size="70" />
          <el-timeline v-else>
            <el-timeline-item v-for="item in history.timeline" :key="item.id" :timestamp="`${item.createdAt} · ${item.operator}`"
                              placement="top" :type="actionColor(item.action)" :hollow="false">
              <el-card shadow="never" class="audit-card" @click="toggleDetail(item.id)">
                <div class="audit-head">
                  <el-tag size="small">#{{ item.chainSeq }}</el-tag>
                  <el-tag size="small" :type="actionColor(item.action)" effect="plain">
                    {{ actionLabel(item.action) }}
                  </el-tag>
                  <span class="muted">{{ item.entityType }} · {{ item.entityId }}</span>
                </div>
                <el-collapse-transition>
                  <div v-show="expanded.has(item.id)" class="audit-detail">
                    <el-row :gutter="12">
                      <el-col :span="12" v-if="item.beforeJson">
                        <div class="muted">变更前</div>
                        <pre class="json-box">{{ prettyJson(item.beforeJson) }}</pre>
                      </el-col>
                      <el-col :span="item.beforeJson ? 12 : 24">
                        <div class="muted">变更后</div>
                        <pre class="json-box">{{ prettyJson(item.afterJson) }}</pre>
                      </el-col>
                    </el-row>
                    <div class="hash-line">
                      <span class="muted">prev:</span> <span class="hash-text">{{ item.prevHash }}</span><br />
                      <span class="muted">hash:</span> <span class="hash-text">{{ item.entryHash }}</span>
                    </div>
                  </div>
                </el-collapse-transition>
                <div v-if="!expanded.has(item.id)" class="muted click-hint">点击展开变更快照与哈希</div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </template>
    </div>

    <!-- 追加证据 -->
    <el-dialog v-model="evidenceVisible" title="追加证据版本" width="560px">
      <el-form :model="evidenceForm" label-width="90px">
        <el-form-item label="证据地址" required>
          <el-input v-model="evidenceForm.fileUri" placeholder="如 oss://bucket/evidence/file.ext" />
        </el-form-item>
        <el-form-item label="版本说明">
          <el-input v-model="evidenceForm.changeNote" type="textarea" :rows="3"
                    placeholder="说明该版本证据的来源与用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evidenceVisible = false">取消</el-button>
        <el-button type="primary" :loading="evidenceSubmitting" @click="submitEvidence">追加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Link } from '@element-plus/icons-vue'
import { recordApi, type Anomaly, type RecordHistory, type TimelineItem } from '@/api'
import StatusTag from '@/components/StatusTag.vue'
import { prettyJson } from '@/utils/hash'

const route = useRoute()
const router = useRouter()
const recordId = Number(route.params.id)

const loading = ref(false)
const loadError = ref(false)
const errorMessage = ref('')
const history = ref<RecordHistory | null>(null)
const anomalies = ref<Anomaly[]>([])
const expanded = reactive(new Set<number>())

const loaded = computed(() => !!history.value)
const record = computed(() => history.value?.record as RecordHistory['record'])

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const [h, a] = await Promise.all([
      recordApi.history(recordId),
      recordApi.anomalies(recordId)
    ])
    history.value = h
    anomalies.value = a
  } catch (e: any) {
    loadError.value = true
    errorMessage.value = e?.message || '请检查记录编号是否正确或稍后重试'
  } finally {
    loading.value = false
  }
}

function toggleDetail(id: number) {
  if (expanded.has(id)) expanded.delete(id)
  else expanded.add(id)
}

function actionLabel(action: string) {
  return {
    CREATE: '创建',
    STATUS_CHANGE: '状态变更',
    HANDLE: '处理',
    EVIDENCE_LINK: '证据关联',
    BATCH_CREATE: '批次创建'
  }[action] || action
}

function actionColor(action: string) {
  return ({
    CREATE: 'success',
    STATUS_CHANGE: 'warning',
    HANDLE: 'warning',
    EVIDENCE_LINK: 'primary'
  } as Record<string, string>)[action] || 'info' as any
}

function goHandle(a: Anomaly) {
  router.push({ name: 'anomalies', query: { highlight: a.id } })
}

// 追加证据（含防重复提交）
const evidenceVisible = ref(false)
const evidenceSubmitting = ref(false)
const evidenceForm = reactive({ fileUri: '', changeNote: '' })

async function submitEvidence() {
  if (evidenceSubmitting.value) return
  if (!evidenceForm.fileUri.trim()) {
    ElMessage.warning('请填写证据地址')
    return
  }
  evidenceSubmitting.value = true
  try {
    await recordApi.addEvidence(recordId, {
      fileUri: evidenceForm.fileUri.trim(),
      changeNote: evidenceForm.changeNote || undefined
    })
    ElMessage.success('证据版本已追加，审计链已更新')
    evidenceVisible.value = false
    evidenceForm.fileUri = ''
    evidenceForm.changeNote = ''
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    evidenceSubmitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header-title {
  font-weight: 600;
}
.block {
  margin-top: 16px;
}
.detail-body {
  margin-top: 12px;
}
.db-high {
  color: #f56c6c;
  font-weight: 700;
}
.json-box {
  margin: 4px 0 0;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f7f8fa;
  border-radius: 4px;
  padding: 8px;
  font-size: 12px;
  max-height: 240px;
  overflow: auto;
}
.muted {
  color: #909399;
  font-size: 12px;
  margin: 2px 0;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.anomaly-card p {
  margin: 4px 0;
}
.handle-note {
  background: #fdf6ec;
  padding: 6px 8px;
  border-radius: 4px;
}
.audit-card {
  cursor: pointer;
}
.audit-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.audit-detail {
  margin-top: 10px;
}
.hash-line {
  margin-top: 8px;
}
.click-hint {
  margin-top: 4px;
}
</style>
