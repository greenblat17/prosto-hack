import { types, flow } from 'mobx-state-tree'
import {
  connect as apiConnect,
  disconnect as apiDisconnect,
  testConnection as apiTestConnection,
  fetchSchemas as apiFetchSchemas,
  fetchTables as apiFetchTables,
  fetchTableFields as apiFetchTableFields,
  type ConnectionConfig,
  type ConnectionTestResult,
  type SchemaInfo,
  type TableInfo,
  type TableField,
} from '@/services/api/connectionApi'

export const ConnectionStore = types
  .model('ConnectionStore', {
    connectionId: types.maybeNull(types.string),
    connectionName: types.optional(types.string, ''),
    selectedSchema: types.maybeNull(types.string),
    selectedTable: types.maybeNull(types.string),
    isConnected: types.optional(types.boolean, false),
    isConnecting: types.optional(types.boolean, false),
    error: types.maybeNull(types.string),
  })
  .volatile(() => ({
    schemas: [] as SchemaInfo[],
    tables: [] as TableInfo[],
    fields: [] as TableField[],
  }))
  .actions(self => ({
    connect: flow(function* (config: ConnectionConfig) {
      self.isConnecting = true
      self.error = null
      try {
        const res: Awaited<ReturnType<typeof apiConnect>> = yield apiConnect(config)
        self.connectionId = res.connectionId
        self.connectionName = res.name
        self.isConnected = true
        self.schemas = yield apiFetchSchemas(res.connectionId)
      } catch (e: any) {
        self.error = e.message ?? 'Ошибка подключения'
        throw e
      } finally {
        self.isConnecting = false
      }
    }),
    disconnect: flow(function* () {
      if (self.connectionId) {
        try {
          yield apiDisconnect(self.connectionId)
        } catch {
          // ignore disconnect errors
        }
      }
      self.connectionId = null
      self.connectionName = ''
      self.selectedSchema = null
      self.selectedTable = null
      self.isConnected = false
      self.schemas = []
      self.tables = []
      self.fields = []
      self.error = null
    }),
    testConnection: flow(function* (config: ConnectionConfig) {
      self.error = null
      try {
        const result: ConnectionTestResult = yield apiTestConnection(config)
        return result
      } catch (e: any) {
        const result: ConnectionTestResult = { success: false, message: e.message ?? 'Ошибка соединения' }
        return result
      }
    }),
    selectSchema: flow(function* (name: string) {
      self.selectedSchema = name
      self.selectedTable = null
      self.fields = []
      if (self.connectionId) {
        self.tables = yield apiFetchTables(self.connectionId, name)
      }
    }),
    selectTable: flow(function* (schema: string, table: string) {
      self.selectedSchema = schema
      self.selectedTable = table
      if (self.connectionId) {
        self.fields = yield apiFetchTableFields(self.connectionId, schema, table)
      }
    }),
    reset() {
      self.connectionId = null
      self.connectionName = ''
      self.selectedSchema = null
      self.selectedTable = null
      self.isConnected = false
      self.isConnecting = false
      self.schemas = []
      self.tables = []
      self.fields = []
      self.error = null
    },
  }))
