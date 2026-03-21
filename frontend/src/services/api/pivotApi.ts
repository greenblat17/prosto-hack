import { post } from './client'
import type { PivotConfig, PivotResult } from '@/types/pivot'

export function executePivot(datasetId: string, config: PivotConfig, offset?: number, limit?: number): Promise<PivotResult> {
  return post('/api/pivot/execute', { datasetId, config, offset, limit })
}

export function executeExternalPivot(
  connectionId: string, schema: string, tableName: string,
  config: PivotConfig, offset?: number, limit?: number
): Promise<PivotResult> {
  return post('/api/pivot/external/execute', { connectionId, schema, tableName, config, offset, limit })
}

