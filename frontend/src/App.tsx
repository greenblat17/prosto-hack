import { useMemo, useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useSearchParams, useParams, useNavigate } from 'react-router-dom'
import { DndContext, DragOverlay, type DragStartEvent, type DragEndEvent, useSensor, useSensors, PointerSensor } from '@dnd-kit/core'
import { RootStore, StoreProvider, useStore } from '@/stores/RootStore'
import { AppLayout } from '@/components/Layout/AppLayout'
import { AppHeader } from '@/components/Layout/AppHeader'
import { StatusBar } from '@/components/Layout/StatusBar'
import { FieldPanel } from '@/components/FieldPanel/FieldPanel'
import { PivotBuilder } from '@/components/PivotBuilder/PivotBuilder'
import { SQLPreview } from '@/components/PivotBuilder/SQLPreview'
import { PivotTable } from '@/components/PivotTable/PivotTable'
import { ChartView } from '@/components/PivotTable/ChartView'
import { ViewToggle, type ViewMode } from '@/components/PivotTable/ViewToggle'
import { ChatPanel } from '@/components/Chat/ChatPanel'
// WelcomeScreen removed — upload is now part of DashboardsPage
import { AuthPage } from '@/components/Auth/AuthPage'
import { DashboardsPage } from '@/components/Dashboards/DashboardsPage'
import { DatabaseTreePanel } from '@/components/Database/DatabaseTreePanel'
import { Hash, Type, Calendar, ToggleLeft, Bot, Download, FileSpreadsheet, Link2, ChevronDown, BarChart3, PanelLeft, PanelTop } from 'lucide-react'
import { cn } from '@/lib/utils'
import { observer } from 'mobx-react-lite'
import type { PivotZone } from '@/types/pivot'

const typeIcons: Record<string, typeof Type> = {
  string: Type,
  number: Hash,
  date: Calendar,
  boolean: ToggleLeft,
}

function ExportMenu() {
  const { resultStore, pivotStore, datasetStore } = useStore()
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  const buildShareUrl = () => {
    const id = datasetStore.currentDatasetId
    if (!id || !pivotStore.isValid) return window.location.href
    try {
      const config = btoa(encodeURIComponent(JSON.stringify(pivotStore.configSnapshot)))
      return `${window.location.origin}/dashboard/${id}?config=${config}`
    } catch {
      return window.location.href
    }
  }

  const items = [
    {
      label: 'CSV', icon: Download, onClick: async () => {
        if (resultStore.data) {
          const { exportCSV } = await import('@/services/api/exportApi')
          await exportCSV(resultStore.data)
        }
        setOpen(false)
      }
    },
    {
      label: 'Excel', icon: FileSpreadsheet, onClick: async () => {
        if (resultStore.data) {
          const { exportExcel } = await import('@/services/api/exportApi')
          await exportExcel(resultStore.data)
        }
        setOpen(false)
      }
    },
    {
      label: copied ? 'Скопировано!' : 'Share link',
      icon: Link2,
      onClick: () => {
        navigator.clipboard.writeText(buildShareUrl())
        setCopied(true)
        setTimeout(() => { setCopied(false); setOpen(false) }, 1200)
      },
    },
  ]

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[14px] text-[#475569] hover:bg-[#f8fafc] border border-[#e2e8f0] hover:border-[#94a3b8] transition-all"
      >
        <Download className="h-4 w-4" />
        Export
        <ChevronDown className="h-3.5 w-3.5 ml-0.5 opacity-50" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-full mt-1 z-40 bg-white rounded-xl border border-[#e2e8f0] shadow-xl py-1.5 min-w-[180px] animate-fade-in-up">
            {items.map(({ label, icon: Icon, onClick }) => (
              <button
                key={label}
                onClick={onClick}
                className="w-full flex items-center gap-2.5 px-4 py-2.5 text-[15px] text-[#334155] hover:bg-[#f8fafc] transition-colors"
              >
                <Icon className="h-[18px] w-[18px] text-[#64748b]" />
                {label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

const CenterPanel = observer(function CenterPanel({ chatOpen, onToggleChat, fieldsOpen, onToggleFields }: { chatOpen: boolean; onToggleChat: () => void; fieldsOpen: boolean; onToggleFields: () => void }) {
  const { pivotStore, resultStore, chatStore } = useStore()
  const { viewMode, data } = resultStore
  const [builderOpen, setBuilderOpen] = useState(true)

  const showChart = viewMode !== 'table' && pivotStore.isValid && data && data.rows.length > 0

  return (
    <div className="flex flex-col h-full">
      {builderOpen && (
        <>
          <PivotBuilder />
          <SQLPreview />
        </>
      )}
      <div className="flex items-center justify-between px-4 pt-3 pb-2">
        <div className="flex items-center gap-3">
          {pivotStore.isValid && (
            <span className="text-[14px] text-[#64748b]">
              {data ? `${data.rows.length} строк` : ''}
            </span>
          )}
          {pivotStore.isValid && data && data.rows.length > 0 && (
            <button
              onClick={() => chatStore.requestExplain()}
              disabled={chatStore.loading}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[14px] text-[#0f766e] hover:bg-[#f0fdfa] border border-[#ccfbf1] hover:border-[#5eead4] transition-all disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Bot className="h-4 w-4" />
              Explain
            </button>
          )}
        </div>
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => setBuilderOpen(v => !v)}
            className={cn(
              "flex items-center justify-center h-9 w-9 rounded-lg border transition-all",
              builderOpen
                ? "bg-[#f0fdfa] border-[#0d9488] text-[#0d9488]"
                : "text-[#475569] hover:bg-[#f8fafc] border-[#e2e8f0] hover:border-[#94a3b8]"
            )}
            title={builderOpen ? 'Скрыть конструктор' : 'Показать конструктор'}
          >
            <PanelTop className="h-4 w-4" />
          </button>
          <button
            onClick={onToggleFields}
            className={cn(
              "flex items-center justify-center h-9 w-9 rounded-lg border transition-all",
              fieldsOpen
                ? "bg-[#f0fdfa] border-[#0d9488] text-[#0d9488]"
                : "text-[#475569] hover:bg-[#f8fafc] border-[#e2e8f0] hover:border-[#94a3b8]"
            )}
            title={fieldsOpen ? 'Скрыть поля' : 'Показать поля'}
          >
            <PanelLeft className="h-4 w-4" />
          </button>
          <button
            onClick={onToggleChat}
            className={cn(
              "flex items-center justify-center h-9 w-9 rounded-lg border transition-all",
              chatOpen
                ? "bg-[#f0fdfa] border-[#0d9488] text-[#0d9488]"
                : "text-[#475569] hover:bg-[#f8fafc] border-[#e2e8f0] hover:border-[#94a3b8]"
            )}
            title={chatOpen ? 'Скрыть AI чат' : 'Показать AI чат'}
          >
            <Bot className="h-4 w-4" />
          </button>
          {pivotStore.isValid && (
            <>
              <ExportMenu />
              <ViewToggle
                value={viewMode as ViewMode}
                onChange={(mode) => resultStore.setViewMode(mode)}
              />
            </>
          )}
        </div>
      </div>
      {showChart ? (
        <ChartView data={data!} mode={viewMode as ViewMode} />
      ) : (
        <PivotTable />
      )}
    </div>
  )
})

const DashboardPage = observer(function DashboardPage() {
  const { pivotStore, resultStore, datasetStore } = useStore()
  const { datasetId } = useParams<{ datasetId: string }>()
  const [fullscreen, _setFullscreen] = useState(false)
  const [fieldsOpen, setFieldsOpen] = useState(true)
  const [chatOpen, setChatOpen] = useState(true)
  const [searchParams] = useSearchParams()
  const [activeField, setActiveField] = useState<{ id: string; name: string; type: string } | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } })
  )

  // Load dataset from URL param — re-runs when datasetId or config changes
  useEffect(() => {
    if (!datasetId) return

    const load = async () => {
      try {
        if (datasetStore.currentDatasetId !== datasetId) {
          await datasetStore.openDataset(datasetId)
        }
        // Apply shared config if present
        const configParam = searchParams.get('config')
        if (configParam) {
          const config = JSON.parse(decodeURIComponent(atob(configParam)))
          pivotStore.applyConfig(config)
          resultStore.executeQuery()
        }
      } catch (e) {
        console.error('Failed to load dataset:', e)
      }
    }
    load()
  }, [datasetId, searchParams, datasetStore, pivotStore, resultStore])

  if (!datasetId) {
    return <Navigate to="/dashboards" replace />
  }

  if (!datasetStore.dataLoaded || datasetStore.currentDatasetId !== datasetId) {
    return <LoadingPage />
  }

  function handleDragStart(event: DragStartEvent) {
    const data = event.active.data.current
    if (data) {
      setActiveField({ id: data.fieldId, name: data.name, type: data.type })
    }
  }

  function handleDragEnd(event: DragEndEvent) {
    setActiveField(null)
    const { active, over } = event
    if (!over) return

    const overId = String(over.id)
    if (!overId.startsWith('zone-')) return

    const zone = overId.replace('zone-', '') as PivotZone
    const data = active.data.current
    if (!data) return

    const { fieldId, name, type: fieldType } = data

    if (zone === 'filters') {
      pivotStore.addField('filters', fieldId, name, fieldType)
    } else {
      const currentZone = pivotStore.isFieldUsed(fieldId)
      if (currentZone) {
        if (currentZone !== zone) {
          pivotStore.moveField(currentZone, zone, fieldId, fieldType)
        }
      } else {
        pivotStore.addField(zone, fieldId, name, fieldType)
      }
    }

    resultStore.executeQuery()
  }

  const DragIcon = activeField ? typeIcons[activeField.type] ?? Type : Type

  return (
    <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
      <div className="flex flex-col h-screen">
        <AppHeader />
        <div className="flex-1 min-h-0 relative">
          <AppLayout
            left={!fullscreen && fieldsOpen ? <FieldPanel /> : null}
            center={<CenterPanel chatOpen={chatOpen} onToggleChat={() => setChatOpen(o => !o)} fieldsOpen={fieldsOpen} onToggleFields={() => setFieldsOpen(f => !f)} />}
            right={!fullscreen && chatOpen ? <ChatPanel onClose={() => setChatOpen(false)} /> : null}
          />
        </div>
        <StatusBar />
      </div>
      <DragOverlay dropAnimation={null}>
        {activeField && (
          <div className="flex items-center gap-2 px-3 py-1.5 bg-white rounded-md shadow-lg border border-[#e5e5e5] text-[13px] text-[#333] pointer-events-none">
            <DragIcon className="h-3.5 w-3.5 opacity-40" />
            <span>{activeField.name}</span>
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
})

const ProtectedRoute = observer(function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { authStore } = useStore()
  if (!authStore.isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
})

function LoadingPage() {
  return (
    <div className="flex flex-col items-center justify-center h-screen bg-[#f8fafc]">
      <div className="w-16 h-16 rounded-2xl bg-[#0f172a] flex items-center justify-center shadow-lg mb-5 animate-pulse">
        <BarChart3 className="h-8 w-8 text-[#2dd4bf]" />
      </div>
      <p className="text-[15px] text-[#94a3b8]">Загрузка данных...</p>
    </div>
  )
}

function NotFoundPage() {
  return (
    <div className="flex flex-col items-center justify-center h-screen bg-[#f8fafc]">
      <span className="text-[120px] font-black text-[#ccfbf1] tracking-tight select-none">404</span>
      <p className="text-[18px] text-[#64748b] mt-2">Страница не найдена</p>
      <a href="/dashboards" className="mt-6 px-5 py-2.5 rounded-xl bg-[#0f172a] text-white text-[15px] font-medium hover:bg-[#1e293b] transition-colors">
        На главную
      </a>
    </div>
  )
}

const ExternalDashboardPage = observer(function ExternalDashboardPage() {
  const { pivotStore, resultStore, datasetStore, connectionStore } = useStore()
  const { connectionId } = useParams<{ connectionId: string }>()
  const navigate = useNavigate()
  const [fullscreen, _setFullscreen] = useState(false)
  const [fieldsOpen, setFieldsOpen] = useState(true)
  const [chatOpen, setChatOpen] = useState(true)
  const [activeField, setActiveField] = useState<{ id: string; name: string; type: string } | null>(null)

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } })
  )

  useEffect(() => {
    if (!connectionId) return
    if (!connectionStore.isConnected || connectionStore.connectionId !== connectionId) {
      navigate('/dashboards')
    }
  }, [connectionId, connectionStore.isConnected, connectionStore.connectionId, navigate])

  if (!connectionStore.isConnected) return <LoadingPage />

  const handleTableSelected = (schema: string, table: string, fields: any[]) => {
    pivotStore.clear()
    resultStore.setData(null)
    datasetStore.setExternalFields(fields, `${schema}.${table}`)
    connectionStore.selectTable(schema, table)
  }

  function handleDragStart(event: DragStartEvent) {
    const data = event.active.data.current
    if (data) setActiveField({ id: data.fieldId, name: data.name, type: data.type })
  }

  function handleDragEnd(event: DragEndEvent) {
    setActiveField(null)
    const { active, over } = event
    if (!over) return
    const overId = String(over.id)
    if (!overId.startsWith('zone-')) return
    const zone = overId.replace('zone-', '') as PivotZone
    const data = active.data.current
    if (!data) return
    const { fieldId, name, type: fieldType } = data
    if (zone === 'filters') {
      pivotStore.addField('filters', fieldId, name, fieldType)
    } else {
      const currentZone = pivotStore.isFieldUsed(fieldId)
      if (currentZone) {
        if (currentZone !== zone) pivotStore.moveField(currentZone, zone, fieldId, fieldType)
      } else {
        pivotStore.addField(zone, fieldId, name, fieldType)
      }
    }
    resultStore.executeQuery()
  }

  const DragIcon = activeField ? typeIcons[activeField.type] ?? Type : Type

  return (
    <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
      <div className="flex flex-col h-screen">
        <AppHeader />
        <div className="flex-1 min-h-0 relative">
          <AppLayout
            left={!fullscreen && fieldsOpen ? <DatabaseTreePanel onTableSelected={handleTableSelected} /> : null}
            center={<CenterPanel chatOpen={chatOpen} onToggleChat={() => setChatOpen(o => !o)} fieldsOpen={fieldsOpen} onToggleFields={() => setFieldsOpen(f => !f)} />}
            right={!fullscreen && chatOpen ? <ChatPanel onClose={() => setChatOpen(false)} /> : null}
          />
        </div>
        <StatusBar />
      </div>
      <DragOverlay dropAnimation={null}>
        {activeField && (
          <div className="flex items-center gap-2 px-3 py-1.5 bg-white rounded-md shadow-lg border border-[#e5e5e5] text-[13px] text-[#333] pointer-events-none">
            <DragIcon className="h-3.5 w-3.5 opacity-40" />
            <span>{activeField.name}</span>
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
})

function DashboardsLayout() {
  return (
    <div className="flex flex-col h-screen">
      <AppHeader />
      <DashboardsPage />
    </div>
  )
}

export default function App() {
  const store = useMemo(() => RootStore.create(), [])

  return (
    <BrowserRouter>
      <StoreProvider value={store}>
        <Routes>
          <Route path="/login" element={<AuthPage />} />
          <Route path="/" element={<Navigate to="/dashboards" replace />} />
          <Route path="/dashboards" element={<ProtectedRoute><DashboardsLayout /></ProtectedRoute>} />
          <Route path="/dashboard/ext/:connectionId" element={<ProtectedRoute><ExternalDashboardPage /></ProtectedRoute>} />
          <Route path="/dashboard/:datasetId" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </StoreProvider>
    </BrowserRouter>
  )
}
