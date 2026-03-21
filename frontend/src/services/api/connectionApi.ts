import { get, post, del } from './client'

export interface ConnectionConfig {
  host: string
  port: number
  database: string
  username: string
  password: string
  name?: string
}

export interface ConnectionResponse {
  connectionId: string
  name: string
  host: string
  port: number
  database: string
}

export interface ConnectionTestResult {
  success: boolean
  message: string
}

export interface SchemaInfo {
  name: string
}

export interface TableInfo {
  name: string
  schema: string
  estimatedRows: number | null
}

export interface TableField {
  name: string
  type: string
  nullable: boolean
}

export function testConnection(config: ConnectionConfig): Promise<ConnectionTestResult> {
  return post('/api/connections/test', config)
}

export function connect(config: ConnectionConfig): Promise<ConnectionResponse> {
  return post('/api/connections', config)
}

export function disconnect(connectionId: string): Promise<void> {
  return del(`/api/connections/${connectionId}`)
}

export function fetchSchemas(connectionId: string): Promise<SchemaInfo[]> {
  return get(`/api/connections/${connectionId}/schemas`)
}

export function fetchTables(connectionId: string, schema: string): Promise<TableInfo[]> {
  return get(`/api/connections/${connectionId}/schemas/${encodeURIComponent(schema)}/tables`)
}

export function fetchTableFields(connectionId: string, schema: string, table: string): Promise<TableField[]> {
  return get(`/api/connections/${connectionId}/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}/fields`)
}
