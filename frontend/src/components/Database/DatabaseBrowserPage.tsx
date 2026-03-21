import { useEffect, useState, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { observer } from 'mobx-react-lite'
import { useStore } from '@/stores/RootStore'
import { AppHeader } from '@/components/Layout/AppHeader'
import { fetchTables, fetchTableFields, type TableField } from '@/services/api/connectionApi'
import {
  ArrowLeft, Database, Table2, Loader2, LogOut, Layers,
  ChevronRight, ChevronDown, Columns3, ExternalLink,
} from 'lucide-react'

interface SchemaNode {
  name: string
  expanded: boolean
  loading: boolean
  tables: TableNode[]
}

interface TableNode {
  name: string
  schema: string
  estimatedRows: number | null
  expanded: boolean
  loading: boolean
  columns: TableField[]
}

const typeColor: Record<string, string> = {
  NUMBER: '#10b981',
  DATE: '#f59e0b',
  BOOLEAN: '#8b5cf6',
  STRING: '#3b82f6',
}

export const DatabaseBrowserPage = observer(function DatabaseBrowserPage() {
  const { connectionStore } = useStore()
  const { connectionId } = useParams<{ connectionId: string }>()
  const navigate = useNavigate()
  const [schemas, setSchemas] = useState<SchemaNode[]>([])

  useEffect(() => {
    if (!connectionStore.isConnected || connectionStore.connectionId !== connectionId) {
      navigate('/dashboards')
      return
    }
    setSchemas(connectionStore.schemas.map(s => ({
      name: s.name,
      expanded: false,
      loading: false,
      tables: [],
    })))
  }, [connectionStore.isConnected, connectionStore.connectionId, connectionId, navigate, connectionStore.schemas])

  if (!connectionStore.isConnected) return null

  const handleDisconnect = async () => {
    await connectionStore.disconnect()
    navigate('/dashboards')
  }

  const toggleSchema = useCallback(async (schemaName: string) => {
    setSchemas(prev => prev.map(s => {
      if (s.name !== schemaName) return s
      if (s.expanded) return { ...s, expanded: false }
      if (s.tables.length > 0) return { ...s, expanded: true }
      return { ...s, expanded: true, loading: true }
    }))

    const schema = schemas.find(s => s.name === schemaName)
    if (schema && schema.tables.length === 0 && connectionId) {
      try {
        const tables = await fetchTables(connectionId, schemaName)
        setSchemas(prev => prev.map(s =>
          s.name === schemaName
            ? { ...s, loading: false, tables: tables.map(t => ({ ...t, expanded: false, loading: false, columns: [] })) }
            : s
        ))
      } catch {
        setSchemas(prev => prev.map(s => s.name === schemaName ? { ...s, loading: false } : s))
      }
    }
  }, [schemas, connectionId])

  const toggleTable = useCallback(async (schemaName: string, tableName: string) => {
    setSchemas(prev => prev.map(s => {
      if (s.name !== schemaName) return s
      return {
        ...s,
        tables: s.tables.map(t => {
          if (t.name !== tableName) return t
          if (t.expanded) return { ...t, expanded: false }
          if (t.columns.length > 0) return { ...t, expanded: true }
          return { ...t, expanded: true, loading: true }
        })
      }
    }))

    const schema = schemas.find(s => s.name === schemaName)
    const table = schema?.tables.find(t => t.name === tableName)
    if (table && table.columns.length === 0 && connectionId) {
      try {
        const fields = await fetchTableFields(connectionId, schemaName, tableName)
        setSchemas(prev => prev.map(s => {
          if (s.name !== schemaName) return s
          return {
            ...s,
            tables: s.tables.map(t =>
              t.name === tableName ? { ...t, loading: false, columns: fields } : t
            )
          }
        }))
      } catch {
        setSchemas(prev => prev.map(s => {
          if (s.name !== schemaName) return s
          return { ...s, tables: s.tables.map(t => t.name === tableName ? { ...t, loading: false } : t) }
        }))
      }
    }
  }, [schemas, connectionId])

  const openTable = (schema: string, table: string) => {
    navigate(`/dashboard/ext/${connectionId}/${encodeURIComponent(schema)}/${encodeURIComponent(table)}`)
  }

  const formatRows = (n: number | null) => {
    if (n === null) return null
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
    if (n >= 1_000) return `${(n / 1_000).toFixed(0)}K`
    return String(n)
  }

  return (
    <div className="flex flex-col h-screen">
      <AppHeader />
      <div className="flex-1 bg-[#f8fafc] overflow-auto">
        <div className="max-w-[900px] mx-auto px-6 py-8">
          {/* Top bar */}
          <div className="flex items-center justify-between mb-5">
            <div className="flex items-center gap-3">
              <button
                onClick={() => navigate('/dashboards')}
                className="p-2 rounded-lg text-[#64748b] hover:text-[#0f172a] hover:bg-[#f1f5f9] transition-all"
              >
                <ArrowLeft className="h-4 w-4" />
              </button>
              <div className="flex items-center gap-2">
                <Database className="h-5 w-5 text-[#0d9488]" />
                <h1 className="text-[18px] font-bold text-[#0f172a]">
                  {connectionStore.connectionName || 'Database'}
                </h1>
              </div>
            </div>
            <button
              onClick={handleDisconnect}
              className="flex items-center gap-2 px-3 py-2 rounded-lg text-[13px] font-medium text-[#64748b] hover:text-[#dc2626] hover:bg-red-50 border border-[#e2e8f0] hover:border-red-200 transition-all"
            >
              <LogOut className="h-3.5 w-3.5" />
              Отключиться
            </button>
          </div>

          {/* Tree */}
          <div className="bg-white rounded-xl border border-[#e2e8f0] overflow-hidden">
            {/* Connection root */}
            <div className="flex items-center gap-2 px-4 py-2.5 bg-[#f8fafc] border-b border-[#e2e8f0]">
              <Database className="h-4 w-4 text-[#0d9488]" />
              <span className="text-[14px] font-semibold text-[#0f172a]">
                {connectionStore.connectionName || 'Database'}
              </span>
              <span className="text-[12px] text-[#94a3b8] ml-1">
                {schemas.length} {schemas.length === 1 ? 'схема' : 'схем'}
              </span>
            </div>

            {schemas.length === 0 && (
              <div className="px-4 py-8 text-center">
                <Loader2 className="h-5 w-5 animate-spin text-[#94a3b8] mx-auto" />
              </div>
            )}

            {schemas.map(schema => (
              <div key={schema.name}>
                {/* Schema row */}
                <button
                  onClick={() => toggleSchema(schema.name)}
                  className="w-full flex items-center gap-1.5 pl-6 pr-4 py-1.5 text-left hover:bg-[#f8fafc] transition-colors group"
                >
                  {schema.loading ? (
                    <Loader2 className="h-3 w-3 animate-spin text-[#94a3b8] shrink-0" />
                  ) : schema.expanded ? (
                    <ChevronDown className="h-3 w-3 text-[#94a3b8] shrink-0" />
                  ) : (
                    <ChevronRight className="h-3 w-3 text-[#94a3b8] shrink-0" />
                  )}
                  <Layers className="h-3.5 w-3.5 text-[#f59e0b] shrink-0" />
                  <span className="text-[13px] font-medium text-[#334155]">{schema.name}</span>
                  {schema.tables.length > 0 && (
                    <span className="text-[11px] text-[#94a3b8] ml-1">{schema.tables.length}</span>
                  )}
                </button>

                {/* Tables */}
                {schema.expanded && schema.tables.map(table => (
                  <div key={`${schema.name}.${table.name}`}>
                    {/* Table row */}
                    <div className="flex items-center pl-12 pr-4 py-1.5 hover:bg-[#f8fafc] transition-colors group">
                      <button
                        onClick={() => toggleTable(schema.name, table.name)}
                        className="flex items-center gap-1.5 flex-1 min-w-0 text-left"
                      >
                        {table.loading ? (
                          <Loader2 className="h-3 w-3 animate-spin text-[#94a3b8] shrink-0" />
                        ) : table.expanded ? (
                          <ChevronDown className="h-3 w-3 text-[#94a3b8] shrink-0" />
                        ) : (
                          <ChevronRight className="h-3 w-3 text-[#94a3b8] shrink-0" />
                        )}
                        <Table2 className="h-3.5 w-3.5 text-[#0d9488] shrink-0" />
                        <span className="text-[13px] text-[#0f172a] truncate">{table.name}</span>
                        {table.estimatedRows !== null && (
                          <span className="text-[11px] text-[#94a3b8] ml-1 shrink-0">{formatRows(table.estimatedRows)}</span>
                        )}
                      </button>
                      <button
                        onClick={() => openTable(schema.name, table.name)}
                        className="opacity-0 group-hover:opacity-100 p-1 rounded text-[#94a3b8] hover:text-[#0d9488] hover:bg-[#f0fdfa] transition-all"
                        title="Открыть в Pivot"
                      >
                        <ExternalLink className="h-3.5 w-3.5" />
                      </button>
                    </div>

                    {/* Columns */}
                    {table.expanded && (
                      <div>
                        {table.columns.length === 0 && !table.loading && (
                          <div className="pl-20 pr-4 py-1 text-[12px] text-[#94a3b8]">Нет колонок</div>
                        )}
                        {table.columns.map(col => {
                          const color = typeColor[col.type] || '#94a3b8'
                          return (
                            <div
                              key={`${schema.name}.${table.name}.${col.name}`}
                              className="flex items-center gap-1.5 pl-20 pr-4 py-1 hover:bg-[#f8fafc] transition-colors"
                            >
                              <Columns3 className="h-3 w-3 shrink-0" style={{ color }} />
                              <span className="text-[12px] text-[#334155] truncate">{col.name}</span>
                              <span className="text-[11px] ml-auto shrink-0" style={{ color, opacity: 0.7 }}>
                                {col.type.toLowerCase()}
                              </span>
                              {col.nullable && (
                                <span className="text-[10px] text-[#cbd5e1]">null</span>
                              )}
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
})
