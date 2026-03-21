import { post } from './client'
import type { PivotConfig, PivotResult } from '@/types/pivot'

export function executePivot(datasetId: string, config: PivotConfig, offset?: number, limit?: number): Promise<PivotResult> {
  return post('/api/pivot/execute', { datasetId, config, offset, limit })
}

export function fetchSQL(datasetId: string, config: PivotConfig): Promise<string> {
  return post<{ sql: string }>('/api/pivot/sql', { datasetId, config }).then(r => r.sql)
}

export function executeExternalPivot(
  connectionId: string, schema: string, tableName: string,
  config: PivotConfig, offset?: number, limit?: number
): Promise<PivotResult> {
  return post('/api/pivot/external/execute', { connectionId, schema, tableName, config, offset, limit })
}

export function fetchExternalSQL(
  connectionId: string, schema: string, tableName: string,
  config: PivotConfig
): Promise<string> {
  return post<{ sql: string }>('/api/pivot/external/sql', { connectionId, schema, tableName, config }).then(r => r.sql)
}
