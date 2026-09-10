<template>
  <div class="page-container">
    <div class="filter-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="传感器">
          <el-select v-model="sensorCode" placeholder="全部传感器" clearable filterable style="width: 220px">
            <el-option v-for="s in sensors" :key="s.id" :label="`${s.sensorCode} ${s.name}`" :value="s.sensorCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
                          start-placeholder="开始" end-placeholder="结束"
                          value-format="YYYY-MM-DDTHH:mm:ss" style="width: 360px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="onSearch">查询</el-button>
          <el-button :icon="RefreshLeft" @click="onReset">重置</el-button>
          <el-radio-group v-model="viewMode" size="default">
            <el-radio-button value="chart">图表</el-radio-button>
            <el-radio-button value="table">数据表</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <div v-loading="loading" class="chart-wrap">
        <el-alert v-if="loadError" type="error" :closable="false" show-icon title="趋势数据加载失败"
                  class="block">
          <el-button type="primary" @click="load">重试</el-button>
        </el-alert>
        <el-empty v-else-if="!loading && points.length === 0"
                  description="所选条件下暂无采样数据，试试放宽时间范围" />
        <div v-show="!loadError && viewMode === 'chart'" ref="chartRef" class="chart"></div>
      </div>

      <el-table v-if="!loadError && viewMode === 'table' && !loading" :data="points" border size="small">
        <el-table-column prop="timeBucket" label="时间（小时）" width="200" />
        <el-table-column prop="avgDb" label="平均 dB(A)" />
        <el-table-column prop="maxDb" label="最大 dB(A)">
          <template #default="{ row }">
            <span :class="{ alarm: row.maxDb > THRESHOLD }">{{ row.maxDb }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="minDb" label="最小 dB(A)" />
        <el-table-column prop="sampleCount" label="样本数" width="100" />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { recordApi, sensorApi, type Sensor, type TrendPoint } from '@/api'

const THRESHOLD = 85

const sensors = ref<Sensor[]>([])
const sensorCode = ref('')
const timeRange = ref<[string, string] | null>(null)
const loading = ref(false)
const loadError = ref(false)
const points = ref<TrendPoint[]>([])
const viewMode = ref<'chart' | 'table'>('chart')
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const data = await recordApi.trend({
      sensorCode: sensorCode.value || undefined,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1]
    })
    points.value = data
    if (viewMode.value === 'chart') {
      await nextTick()
      renderChart()
    }
  } catch {
    points.value = []
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load()
}

function onReset() {
  sensorCode.value = ''
  timeRange.value = null
  load()
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  const data = points.value
  chart.setOption({
    color: ['#2a78d6', '#eb6834', '#1baf7a'],
    grid: { left: 56, right: 24, top: 56, bottom: 64 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#52514e' } },
      valueFormatter: (v: any) => (v == null ? '—' : `${v} dB(A)`)
    },
    legend: {
      top: 12,
      data: ['平均分贝', '最大分贝', '最小分贝']
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map((p) => p.timeBucket),
      axisLine: { lineStyle: { color: '#c8c6bd' } },
      axisLabel: { color: '#52514e', rotate: data.length > 12 ? 38 : 0 }
    },
    yAxis: {
      type: 'value',
      name: 'dB(A)',
      min: (v: { min: number }) => Math.max(0, Math.floor(v.min - 10)),
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#eceae4' } },
      axisLabel: { color: '#52514e' }
    },
    series: [
      {
        name: '平均分贝',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 2 },
        data: data.map((p) => p.avgDb),
        markLine: {
          symbol: 'none',
          silent: true,
          lineStyle: { color: '#e34948', type: 'dashed', width: 2 },
          label: {
            formatter: '异常阈值 85 dB(A)',
            color: '#e34948',
            position: 'insideEndTop'
          },
          data: [{ yAxis: THRESHOLD }]
        }
      },
      {
        name: '最大分贝',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2 },
        data: data.map((p) => p.maxDb)
      },
      {
        name: '最小分贝',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2 },
        data: data.map((p) => p.minDb)
      }
    ]
  })
}

watch(viewMode, async (mode) => {
  if (mode === 'chart') {
    await nextTick()
    renderChart()
    chart?.resize()
  }
})

onMounted(async () => {
  sensors.value = await sensorApi.list().catch(() => [])
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
.chart-wrap {
  min-height: 420px;
  position: relative;
}
.chart {
  width: 100%;
  height: 460px;
}
.block {
  margin: 24px 0;
}
.alarm {
  color: #e34948;
  font-weight: 700;
}
</style>
