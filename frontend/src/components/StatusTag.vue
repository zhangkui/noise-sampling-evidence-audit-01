<template>
  <el-tag :type="type" size="small" effect="light">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string; kind?: 'anomaly' | 'import' | 'sensor' }>()

const ANOMALY_MAP: Record<string, { label: string; type: string }> = {
  OPEN: { label: '待处理', type: 'danger' },
  PROCESSING: { label: '处理中', type: 'warning' },
  RESOLVED: { label: '已解决', type: 'success' },
  IGNORED: { label: '已忽略', type: 'info' },
  NONE: { label: '正常', type: 'success' },
  ANY: { label: '有异常', type: 'warning' }
}

const IMPORT_MAP: Record<string, { label: string; type: string }> = {
  DONE: { label: '全部成功', type: 'success' },
  PARTIAL: { label: '部分成功', type: 'warning' },
  ALL_FAILED: { label: '全部失败', type: 'danger' }
}

const SENSOR_MAP: Record<string, { label: string; type: string }> = {
  ONLINE: { label: '在线', type: 'success' },
  OFFLINE: { label: '离线', type: 'info' },
  MAINTENANCE: { label: '维护中', type: 'warning' }
}

const type = computed(() => {
  const map = props.kind === 'import' ? IMPORT_MAP : props.kind === 'sensor' ? SENSOR_MAP : ANOMALY_MAP
  return (map[props.status]?.type || 'info') as any
})
const label = computed(() => {
  const map = props.kind === 'import' ? IMPORT_MAP : props.kind === 'sensor' ? SENSOR_MAP : ANOMALY_MAP
  return map[props.status]?.label || props.status
})
</script>
