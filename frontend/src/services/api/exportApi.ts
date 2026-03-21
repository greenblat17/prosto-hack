import { postBlob } from './client'
import type { PivotResult } from '@/types/pivot'

export function exportCSV(result: PivotResult): Promise<void> {
  return postBlob('/api/export/csv', result, 'pivot_export.csv')
}

export function exportExcel(result: PivotResult): Promise<void> {
  return postBlob('/api/export/excel', result, 'pivot_export.xlsx')
}
