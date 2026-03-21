import { get, del, postFile } from './client'

export interface DatasetInfo {
  id: string
  name: string
  rowCount: number
  columnCount: number
  createdAt: string
}

export interface DatasetField {
  id: string
  name: string
  type: 'string' | 'number' | 'date' | 'boolean'
  category: string
}

export function fetchDatasets(): Promise<DatasetInfo[]> {
  return get('/api/datasets')
}

export function fetchFields(datasetId: string): Promise<DatasetField[]> {
  return get(`/api/datasets/${datasetId}/fields`)
}

export function uploadFile(file: File, name?: string): Promise<DatasetInfo> {
  return postFile('/api/datasets/upload', file, name)
}

export function deleteDataset(id: string): Promise<void> {
  return del(`/api/datasets/${id}`)
}

export interface ColumnStats {
  totalRows: number
  distinctCount: number
  nullCount: number
  minValue: string | null
  maxValue: string | null
  topValues: { value: string; count: number }[]
}

export function fetchColumnStats(datasetId: string, fieldName: string): Promise<ColumnStats> {
  return get(`/api/datasets/${datasetId}/fields/${encodeURIComponent(fieldName)}/stats`)
}
