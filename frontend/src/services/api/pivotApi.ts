import { post, type RequestOptions } from './client'
import type { PivotConfig, PivotResult } from '@/types/pivot'

export function executePivot(datasetId: string, config: PivotConfig, offset?: number, limit?: number, opts?: RequestOptions): Promise<PivotResult> {
  return post('/api/pivot/execute', { datasetId, config, offset, limit }, opts)
}

export function executeExternalPivot(
  connectionId: string, schema: string, tableName: string,
  config: PivotConfig, offset?: number, limit?: number, opts?: RequestOptions
): Promise<PivotResult> {
  return post('/api/pivot/external/execute', { connectionId, schema, tableName, config, offset, limit }, opts)
}

