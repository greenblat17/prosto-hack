import { useState, useCallback } from 'react'
import { observer } from 'mobx-react-lite'
import { useStore } from '@/stores/RootStore'
import { fetchTables, fetchTableFields, type TableField } from '@/services/api/connectionApi'
import { FieldItem } from '@/components/FieldPanel/FieldItem'
import {
  Database, Table2, Loader2, Layers,
  ChevronRight, ChevronDown, Columns3, Search, ArrowLeft,
} from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

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

function mapFieldType(pgType: string): 'string' | 'number' | 'date' | 'boolean' {
  const t = pgType.toLowerCase()
  if (['number', 'int', 'integer', 'bigint', 'smallint', 'numeric', 'decimal', 'real', 'double precision', 'float'].some(k => t.includes(k))) return 'number'
  if (['date', 'timestamp', 'time'].some(k => t.includes(k))) return 'date'
  if (t === 'boolean' || t === 'bool') return 'boolean'
  return 'string'
}

function mapFieldCategory(fieldType: string): string {
  switch (fieldType) {
    case 'number': return 'Метрики'
    case 'date': return 'Время'
    case 'boolean': return 'Флаги'
    default: return 'Измерения'
  }
}

interface Props {
  onTableSelected: (schema: string, table: string, fields: any[]) => void
}

export const DatabaseTreePanel = observer(function DatabaseTreePanel({ onTableSelected }: Props) {
  const { connectionStore, pivotStore, datasetStore } = useStore()
  const [schemas, setSchemas] = useState<SchemaNode[]>(() =>
    connectionStore.schemas.map(s => ({ name: s.name, expanded: false, loading: false, tables: [] }))
  )
  const [activeTable, setActiveTable] = useState<string | null>(null)
  const [showFields, setShowFields] = useState(false)

  const connectionId = connectionStore.connectionId

  const formatRows = (n: number | null) => {
    if (n === null) return null
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
    if (n >= 1_000) return `${(n / 1_000).toFixed(0)}K`
    return String(n)
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

  const selectTable = useCallback(async (schemaName: string, tableName: string) => {
    if (!connectionId) return
    const key = `${schemaName}.${tableName}`
    if (activeTable === key) {
      setShowFields(true)
      return
    }

    setActiveTable(key)
    setShowFields(true)
    try {
      const fields = await fetchTableFields(connectionId, schemaName, tableName)
      const mapped = fields.map(f => {
        const type = mapFieldType(f.type)
        return { id: f.name, name: f.name, type, category: mapFieldCategory(type) }
      })
      onTableSelected(schemaName, tableName, mapped)
    } catch (e) {
      console.error('Failed to load table fields:', e)
    }
  }, [connectionId, activeTable, onTableSelected])

  // Mode: draggable fields
  if (showFields && datasetStore.dataLoaded) {
    return (
      <div className="flex flex-col h-full">
        {/* Back to tree */}
        <button
          onClick={() => setShowFields(false)}
          className="flex items-center gap-2.5 px-4 py-3 text-[14px] text-[#64748b] hover:text-[#0d9488] hover:bg-[#f0fdfa] transition-colors border-b border-[#f1f5f9]"
        >
          <ArrowLeft className="h-4 w-4" />
          <Database className="h-4 w-4" />
          <span>Назад к дереву БД</span>
        </button>

        {/* Active table name */}
        <div className="px-4 py-3 border-b border-[#f1f5f9] bg-[#f8fafc]">
          <div className="flex items-center gap-2.5">
            <Table2 className="h-4 w-4 text-[#0d9488]" />
            <span className="text-[14px] font-semibold text-[#0f172a]">{activeTable}</span>
          </div>
        </div>

        {/* Search */}
        <div className="px-4 pt-4 pb-3">
          <div className="relative">
            <Search className="absolute left-2.5 top-[9px] h-4 w-4 text-[#94a3b8]" />
            <Input
              placeholder="Поиск..."
              value={datasetStore.searchQuery}
              onChange={e => datasetStore.setSearchQuery(e.target.value)}
              className="pl-9 h-9 text-[15px] bg-white border-[#e2e8f0] text-[#334155] placeholder:text-[#94a3b8] focus:border-[#0d9488] rounded-lg"
            />
          </div>
        </div>

        {/* Draggable fields — same as FieldPanel */}
        <div className="flex-1 min-h-0 overflow-y-auto">
          <div className="px-2 pb-3">
            {Object.entries(datasetStore.fieldsByCategory).map(([category, fields]) => (
              <div key={category} className="mb-1">
                <div className="flex items-center gap-1.5 px-2.5 py-1.5">
                  <span className="text-[13px] uppercase tracking-wider text-[#64748b] font-medium flex-1">
                    {category}
                  </span>
                  <span className="text-[11px] bg-[#f1f5f9] text-[#64748b] rounded-full px-2 py-0.5 font-medium">
                    {(fields as any[]).length}
                  </span>
                </div>
                {(fields as any[]).map((field: any) => (
                  <FieldItem
                    key={field.id}
                    field={field}
                    usedIn={pivotStore.isFieldUsed(field.id)}
                  />
                ))}
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  // Mode: tree view
  return (
    <div className="flex flex-col h-full">
      <div className="px-4 pt-4 pb-3">
        <div className="text-[14px] uppercase tracking-widest text-[#64748b] font-medium">
          База данных
        </div>
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto">
        {/* Connection root */}
        <div className="flex items-center gap-2.5 px-4 py-2.5 bg-[#f8fafc] border-b border-[#f1f5f9]">
          <Database className="h-4 w-4 text-[#0d9488]" />
          <span className="text-[14px] font-semibold text-[#0f172a] truncate">
            {connectionStore.connectionName || 'Database'}
          </span>
        </div>

        {schemas.length === 0 && (
          <div className="px-4 py-8 text-center">
            <Loader2 className="h-5 w-5 animate-spin text-[#94a3b8] mx-auto" />
          </div>
        )}

        {schemas.map(schema => (
          <div key={schema.name}>
            {/* Schema */}
            <button
              onClick={() => toggleSchema(schema.name)}
              className="w-full flex items-center gap-2 pl-6 pr-4 py-2 text-left hover:bg-[#f8fafc] transition-colors"
            >
              {schema.loading ? (
                <Loader2 className="h-4 w-4 animate-spin text-[#94a3b8] shrink-0" />
              ) : schema.expanded ? (
                <ChevronDown className="h-4 w-4 text-[#94a3b8] shrink-0" />
              ) : (
                <ChevronRight className="h-4 w-4 text-[#94a3b8] shrink-0" />
              )}
              <Layers className="h-4 w-4 text-[#64748b] shrink-0" />
              <span className="text-[14px] font-medium text-[#334155] truncate">{schema.name}</span>
              {schema.tables.length > 0 && (
                <span className="text-[12px] text-[#94a3b8] ml-auto">{schema.tables.length}</span>
              )}
            </button>

            {/* Tables */}
            {schema.expanded && schema.tables.map(table => {
              const key = `${schema.name}.${table.name}`
              const isActive = activeTable === key
              return (
                <div key={key}>
                  <div className={cn(
                    "flex items-center pl-12 pr-4 py-2 hover:bg-[#f8fafc] transition-colors group cursor-pointer",
                    isActive && "bg-[#f0fdfa]"
                  )}>
                    <button
                      onClick={() => toggleTable(schema.name, table.name)}
                      className="shrink-0 p-0.5"
                    >
                      {table.loading ? (
                        <Loader2 className="h-4 w-4 animate-spin text-[#94a3b8]" />
                      ) : table.expanded ? (
                        <ChevronDown className="h-4 w-4 text-[#94a3b8]" />
                      ) : (
                        <ChevronRight className="h-4 w-4 text-[#94a3b8]" />
                      )}
                    </button>
                    <button
                      onClick={() => selectTable(schema.name, table.name)}
                      className="flex items-center gap-2 flex-1 min-w-0 ml-1 text-left"
                    >
                      <Table2 className={cn("h-4 w-4 shrink-0", isActive ? "text-[#0d9488]" : "text-[#64748b]")} />
                      <span className={cn("text-[14px] truncate", isActive ? "text-[#0d9488] font-medium" : "text-[#0f172a]")}>
                        {table.name}
                      </span>
                      {table.estimatedRows !== null && (
                        <span className="text-[12px] text-[#94a3b8] ml-auto shrink-0">{formatRows(table.estimatedRows)}</span>
                      )}
                    </button>
                  </div>

                  {/* Columns */}
                  {table.expanded && table.columns.map(col => (
                    <div
                      key={`${key}.${col.name}`}
                      className="flex items-center gap-2 pl-20 pr-4 py-1.5 hover:bg-[#f8fafc] transition-colors"
                    >
                      <Columns3 className="h-3.5 w-3.5 text-[#94a3b8] shrink-0" />
                      <span className="text-[13px] text-[#475569] truncate">{col.name}</span>
                      <span className="text-[12px] text-[#94a3b8] ml-auto shrink-0">
                        {col.type.toLowerCase()}
                      </span>
                    </div>
                  ))}
                </div>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
})
