<template>
  <div class="page-container">
    <!-- 加载中 -->
    <div v-if="loading" v-loading="true" class="loading-block"></div>

    <!-- 批次不存在 -->
    <el-result v-else-if="notFound" icon="warning" title="采样批次不存在"
               sub-title="该批次可能已被删除，或链接有误">
      <template #extra>
        <el-button type="primary" @click="goBatchList">返回传感器与批次</el-button>
      </template>
    </el-result>

    <!-- 接口失败（非 404，可重试） -->
    <el-result v-else-if="loadError" icon="error" title="统计加载失败"
               sub-title="网络或服务异常，批次数据未被修改，可稍后重试">
      <template #extra>
        <el-button type="primary" :loading="loading" @click="load">重新加载</el-button>
        <el-button @click="goBatchList">返回列表</el-button>
      </template>
    </el-result>

    <template v-else-if="stats">
      <!-- 头部 -->
      <div class="header">
        <el-button :icon="ArrowLeft" link @click="goBatchList">传感器与批次</el-button>
        <div class="title-row">
          <h2 class="title">{{ stats.batchNo }}</h2>
          <StatusTag :status="stats.status" kind="batch" />
        </div>
      </div>

      <!-- 批次基本信息 -->
      <div class="table-card block-card">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="传感器编号">{{ stats.sensorCode }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ stats.operator || '—' }}</el-descriptions-item>
          <el-descriptions-item label="采样目的">{{ stats.purpose || '—' }}</el-descriptions-item>
          <el-descriptions-item label="计划开始">{{ stats.startTime }}</el-descriptions-item>
          <el-descriptions-item label="计划结束">{{ stats.endTime }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ stats.createdAt || '—' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 统计卡片 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">记录总数</div>
            <div class="stat-value">{{ stats.recordCount }}</div>
            <div class="stat-unit">条</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">平均分贝</div>
            <div class="stat-value">{{ fmtDb(stats.avgDb) }}</div>
            <div class="stat-unit">dB(A)</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">最大分贝</div>
            <div class="stat-value" :class="{ alarm: (stats.maxDb ?? 0) > THRESHOLD }">
              {{ fmtDb(stats.maxDb) }}
            </div>
            <div class="stat-unit">dB(A)</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="8" :md="4">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">最小分贝</div>
            <div class="stat-value">{{ fmtDb(stats.minDb) }}</div>
            <div class="stat-unit">dB(A)</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="16" :md="8">
          <el-card shadow="never" class="stat-card">
            <div class="stat-label">实际采样时间范围</div>
            <div v-if="stats.firstSampleTime" class="time-range">
              <span>{{ stats.firstSampleTime }}</span>
              <span class="arrow">→</span>
              <span>{{ stats.lastSampleTime }}</span>
            </div>
            <div v-else class="stat-empty">暂无采样记录</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 异常分布 -->
      <div class="table-card block-card">
        <div class="section-head">
          <h3>异常分布</h3>
          <span class="section-sub">
            共 {{ stats.anomalyTotal }} 个异常事件，关联本批次 {{ stats.recordCount }} 条记录
          </span>
        </div>

        <el-empty v-if="stats.anomalyTotal === 0" description="该批次暂无异常事件" />

        <el-row v-else :gutter="16">
          <el-col :xs="24" :md="12">
            <div ref="chartRef" class="chart"></div>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-table :data="anomalyRows" border size="small" @row-click="goRecordsWithAnomaly">
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <span class="legend-dot" :style="{ background: row.color }"></span>
                  <StatusTag :status="row.status" />
                </template>
              </el-table-column>
              <el-table-column prop="count" label="数量" width="90" />
              <el-table-column label="占比">
                <template #default="{ row }">{{ row.percent }}%</template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button link type="primary"
                             :loading="navigating === row.status"
                             @click.stop="goRecordsWithAnomaly(row)">查看记录</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
        </el-row>
      </div>

      <!-- 底部操作 -->
      <div class="actions">
        <el-button type="primary" :loading="navigating === 'ALL'" @click="goRecords">
          查看该批次采样记录（{{ stats.recordCount }}）
        </el-button>
        <el-button @click="load" :loading="loading">刷新统计</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ArrowLeft } from '@element-plus/icons-vue'
import { batchApi, type BatchStatistics } from '@/api'
import { ApiBusinessError } from '@/api/http'
import StatusTag from '@/components/StatusTag.vue'

const THRESHOLD = 85

const route = useRoute()
const router = useRouter()
const batchId = Number(route.params.id)

const loading = ref(false)
const loadError = ref(false)
const notFound = ref(false)
const stats = ref<BatchStatistics | null>(null)

// 重复点击防护：记录正在进行的跳转目标，按钮进入 loading
const navigating = ref<'' | 'ALL' | string>('')

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

// 状态语义色（与 StatusTag 的 待处理/处理中/已解决/已忽略 对应），
// 饼图同时提供图例、扇区直接标注和右侧明细表，颜色不作为唯一编码。
const STATUS_META: Record<string, { label: string; color: string }> = {
  OPEN: { label: '待处理', color: '#e34948' },
  PROCESSING: { label: '处理中', color: '#e6a23c' },
  RESOLVED: { label: '已解决', color: '#1baf7a' },
  IGNORED: { label: '已忽略', color: '#909399' }
}
const STATUS_ORDER = ['OPEN', 'PROCESSING', 'RESOLVED', 'IGNORED']

const anomalyRows = computed(() => {
  if (!stats.value || stats.value.anomalyTotal === 0) return []
  const raw: Array<{ status: string; count: number }> = [
    { status: 'OPEN', count: stats.value.anomalyOpen },
    { status: 'PROCESSING', count: stats.value.anomalyProcessing },
    { status: 'RESOLVED', count: stats.value.anomalyResolved },
    { status: 'IGNORED', count: stats.value.anomalyIgnored }
  ]
  // 服务端可能出现四个已知状态之外的新状态：计入总数但不落已知字段，
  // 若有差额以“其他”补位，保证图表与总数对得上。
  const knownSum = raw.reduce((s, r) => s + r.count, 0)
  if (knownSum < stats.value.anomalyTotal) {
    raw.push({ status: 'OTHER', count: stats.value.anomalyTotal - knownSum })
  }
  const meta = (s: string) => STATUS_META[s] || { label: s === 'OTHER' ? '其他' : s, color: '#52514e' }
  return raw
    .filter((r) => r.count > 0)
    .map((r) => ({
      status: r.status,
      label: meta(r.status).label,
      color: meta(r.status).color,
      count: r.count,
      percent: Math.round((r.count / (stats.value!.anomalyTotal || 1)) * 1000) / 10
    }))
})

function fmtDb(v: number | null | undefined): string {
  // 空批次统计值为 null：明确展示“—”而不是 0
  return v === null || v === undefined ? '—' : String(v)
}

async function load() {
  if (!Number.isFinite(batchId) || batchId <= 0) {
    notFound.value = true
    return
  }
  loading.value = true
  loadError.value = false
  notFound.value = false
  try {
    // 统计接口已携带批次全部基本信息，一次请求即可渲染整页（只读，不产生审计）
    stats.value = await batchApi.statistics(batchId)
    await nextTick()
    renderChart()
  } catch (e) {
    stats.value = null
    if (e instanceof ApiBusinessError && e.code === 40400) {
      notFound.value = true
    } else {
      loadError.value = true
    }
  } finally {
    loading.value = false
  }
}

function renderChart() {
  const rows = anomalyRows.value
  if (rows.length === 0 || !chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  chart.setOption(
    {
      color: rows.map((r) => r.color),
      tooltip: {
        trigger: 'item',
        formatter: (p: any) => `${p.name}：${p.value} 个（${p.percent}%）`
      },
      legend: { bottom: 0, textStyle: { color: '#52514e' } },
      series: [
        {
          name: '异常状态',
          type: 'pie',
          radius: ['42%', '66%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fcfcfb', borderWidth: 2, borderRadius: 4 },
          label: {
            // 直接标注：状态名 + 数量，颜色不作为唯一编码
            formatter: (p: any) => `${p.name}\n${p.value} 个`,
            color: '#52514e'
          },
          labelLine: { length: 12, length2: 10 },
          data: rows.map((r) => ({ name: r.label, value: r.count }))
        }
      ]
    },
    { notMerge: true }
  )
}

function goBatchList() {
  router.push({ name: 'sensors', query: { tab: 'batches' } })
}

async function guardNav(key: string, fn: () => void) {
  // 防止重复点击：跳转进行中忽略后续点击
  if (navigating.value) return
  navigating.value = key
  try {
    fn()
  } finally {
    // 路由切换后组件卸载，重置仅为同页异常状态跳转等场景兜底
    setTimeout(() => (navigating.value = ''), 300)
  }
}

function goRecords() {
  guardNav('ALL', () => {
    router.push({
      name: 'records',
      query: { batchId: String(batchId), batchNo: stats.value?.batchNo || '' }
    })
  })
}

function goRecordsWithAnomaly(row: { status: string }) {
  const anomalyStatus = row.status === 'OTHER' ? 'ANY' : row.status
  guardNav(row.status, () => {
    router.push({
      name: 'records',
      query: {
        batchId: String(batchId),
        batchNo: stats.value?.batchNo || '',
        anomalyStatus
      }
    })
  })
}

watch(anomalyRows, async () => {
  await nextTick()
  renderChart()
})

onMounted(async () => {
  await load()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  if (chartRef.value) resizeObserver.observe(chartRef.value)
})
onActivated(() => chart?.resize())
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.loading-block {
  min-height: 360px;
}
.header {
  margin-bottom: 12px;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}
.title {
  margin: 0;
  font-size: 20px;
}
.block-card {
  margin-bottom: 16px;
}
.stat-row {
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
  margin-bottom: 12px;
}
.stat-label {
  color: #909399;
  font-size: 13px;
}
.stat-value {
  display: inline-block;
  margin-top: 6px;
  font-size: 28px;
  font-weight: 700;
  color: #2f2e2b;
}
.stat-value.alarm {
  color: #e34948;
}
.stat-unit {
  margin-left: 4px;
  color: #909399;
  font-size: 12px;
}
.time-range {
  margin-top: 8px;
  font-size: 13px;
  color: #2f2e2b;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.time-range .arrow {
  color: #909399;
}
.stat-empty {
  margin-top: 10px;
  color: #909399;
  font-size: 13px;
}
.section-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
}
.section-head h3 {
  margin: 0;
  font-size: 16px;
}
.section-sub {
  color: #909399;
  font-size: 13px;
}
.chart {
  width: 100%;
  height: 320px;
}
.legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}
.actions {
  display: flex;
  gap: 12px;
}
</style>
