import { get, post } from './http'

export interface PageData<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface RecordItem {
  id: number
  recordNo: string
  sensorCode: string
  batchId?: number
  batchNo?: string
  sampleTime: string
  longitude?: number
  latitude?: number
  dbValue: number
  spectrumSummary?: string
  rawDataHash: string
  createdBy?: string
  createdAt: string
  anomalyStatus: string
  anomalyId?: number
}

export interface RecordQuery {
  page?: number
  size?: number
  sensorCode?: string
  startTime?: string
  endTime?: string
  anomalyStatus?: string
  dbMin?: number
  dbMax?: number
}

export interface RecordForm {
  sensorCode: string
  batchNo?: string
  sampleTime: string
  longitude?: number
  latitude?: number
  dbValue: number
  spectrumSummary?: string
  rawDataHash: string
}

export interface Anomaly {
  id: number
  eventNo: string
  recordId: number
  anomalyType: string
  thresholdValue: number
  dbValue: number
  status: string
  description?: string
  handleNote?: string
  handledBy?: string
  handledAt?: string
  createdAt: string
}

export interface EvidenceVersion {
  id: number
  versionNo: number
  algorithm: string
  prevHash: string
  evidenceHash: string
  fileUri?: string
  changeNote?: string
  createdBy?: string
  createdAt?: string
}

export interface TimelineItem {
  id: number
  chainSeq: number
  entityType: string
  entityId?: string
  action: string
  beforeJson?: string
  afterJson?: string
  operator: string
  prevHash: string
  entryHash: string
  createdAt: string
}

export interface RecordHistory {
  record: RecordItem
  evidences: EvidenceVersion[]
  timeline: TimelineItem[]
  chainValid: boolean
  brokenAt?: string
}

export interface TrendPoint {
  timeBucket: string
  avgDb: number
  maxDb: number
  minDb: number
  sampleCount: number
}

export interface Sensor {
  id: number
  sensorCode: string
  name: string
  location?: string
  longitude?: number
  latitude?: number
  status: string
}

export interface SamplingBatch {
  id: number
  batchNo: string
  sensorCode: string
  startTime: string
  endTime: string
  purpose?: string
  operator?: string
  status: string
}

export interface ImportTask {
  id: number
  importNo: string
  fileName?: string
  totalCount: number
  successCount: number
  failCount: number
  status: string
  operator?: string
  createdAt: string
}

export interface ImportItem {
  id: number
  taskId: number
  rowIndex: number
  recordNo?: string
  sensorCode?: string
  sampleTime?: string
  dbValue?: number
  success: boolean
  failReason?: string
}

export interface ImportResult {
  taskId: number
  importNo: string
  totalCount: number
  successCount: number
  failCount: number
  status: string
  items: Array<{
    rowIndex: number
    sensorCode?: string
    sampleTime?: string
    dbValue?: number
    success: boolean
    failReason?: string
    recordId?: number
    recordNo?: string
  }>
}

export interface AuditLog {
  id: number
  chainSeq: number
  traceId?: string
  recordId?: number
  entityType: string
  entityId?: string
  action: string
  beforeJson?: string
  afterJson?: string
  operator: string
  operatorIp?: string
  prevHash: string
  entryHash: string
  createdAt: string
}

export const authApi = {
  login: (username: string, password: string) =>
    post<{ token: string; expireIn: number; user: any }>('/auth/login', { username, password })
}

export const recordApi = {
  page: (params: RecordQuery) => get<PageData<RecordItem>>('/records', params),
  detail: (id: number) => get<RecordItem>(`/records/${id}`),
  create: (data: RecordForm) => post<RecordItem>('/records', data),
  anomalies: (id: number) => get<Anomaly[]>(`/records/${id}/anomalies`),
  evidences: (id: number) => get<EvidenceVersion[]>(`/records/${id}/evidences`),
  trend: (params: { sensorCode?: string; startTime?: string; endTime?: string }) =>
    get<TrendPoint[]>('/records/trend/chart', params),
  history: (id: number) => get<RecordHistory>(`/audit/records/${id}/history`),
  addEvidence: (id: number, data: { fileUri: string; changeNote?: string }) =>
    post<EvidenceVersion>(`/records/${id}/evidences`, data)
}

export const anomalyApi = {
  page: (params: { page: number; size: number; status?: string }) =>
    get<PageData<Anomaly>>('/anomalies', params),
  handle: (id: number, data: { status: string; handleNote: string }) =>
    post<Anomaly>(`/anomalies/${id}/handle`, data)
}

export const sensorApi = {
  list: () => get<Sensor[]>('/sensors'),
  create: (data: any) => post<Sensor>('/sensors', data)
}

export const batchApi = {
  list: (sensorCode?: string) => get<SamplingBatch[]>('/batches', { sensorCode }),
  create: (data: any) => post<SamplingBatch>('/batches', data),
  overlapCheck: (params: { sensorCode: string; startTime: string; endTime: string }) =>
    get<{ overlap: boolean; conflicts: SamplingBatch[] }>('/batches/overlap-check', params)
}

export const importApi = {
  doImport: (data: { fileName?: string; records: RecordForm[] }) => post<ImportResult>('/imports', data),
  page: (params: { page: number; size: number }) => get<PageData<ImportTask>>('/imports', params),
  detail: (id: number) => get<ImportTask>(`/imports/${id}`),
  items: (id: number) => get<ImportItem[]>(`/imports/${id}/items`)
}

export const auditApi = {
  logs: (params: { page: number; size: number; recordId?: number; entityType?: string; action?: string }) =>
    get<PageData<AuditLog>>('/audit/logs', params)
}
