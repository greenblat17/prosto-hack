import { useMemo, useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useSearchParams, useParams, useNavigate } from 'react-router-dom'
import { DndContext, DragOverlay, type DragStartEvent, type DragEndEvent, useSensor, useSensors, PointerSensor } from '@dnd-kit/core'
import { RootStore, StoreProvider, useStore } from '@/stores/RootStore'
import { AppLayout } from '@/components/Layout/AppLayout'
import { AppHeader } from '@/components/Layout/AppHeader'
import { StatusBar } from '@/components/Layout/StatusBar'
import { FieldPanel } from '@/components/FieldPanel/FieldPanel'
import { CenterPanel } from '@/components/Layout/CenterPanel'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { ChatPanel } from '@/components/Chat/ChatPanel'
// WelcomeScreen removed — upload is now part of DashboardsPage
import { AuthPage } from '@/components/Auth/AuthPage'
import { DashboardsPage } from '@/components/Dashboards/DashboardsPage'
import { DatabaseTreePanel } from '@/components/Database/DatabaseTreePanel'
import { Hash, Type, Calendar, ToggleLeft, BarChart3 } from 'lucide-react'
import { observer } from 'mobx-react-lite'
import type { PivotZone } from '@/types/pivot'

const typeIcons: Record<string, typeof Type> = {
  string: Type,
  number: Hash,
  date: Calendar,
  boolean: ToggleLeft,
}

const DashboardPage = observer(function DashboardPage() {
  const { pivotStore, resultStore, datasetStore, chatStore } = useStore()
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
          // Reset stores when switching datasets
          pivotStore.clear()
          resultStore.setData(null)
          chatStore.resetForDataset()
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

    // Cleanup on unmount (navigating away from dashboard)
    return () => {
      pivotStore.clear()
      resultStore.setData(null)
      chatStore.resetForDataset()
      datasetStore.resetData()
    }
  }, [datasetId, searchParams, datasetStore, pivotStore, resultStore, chatStore])

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
  const { pivotStore, resultStore, datasetStore, connectionStore, chatStore } = useStore()
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

    // Cleanup on unmount (navigating away from external dashboard)
    return () => {
      pivotStore.clear()
      resultStore.setData(null)
      chatStore.resetForDataset()
    }
  }, [connectionId, connectionStore.isConnected, connectionStore.connectionId, navigate, pivotStore, resultStore, chatStore])

  if (!connectionStore.isConnected) return <LoadingPage />

  const handleTableSelected = (schema: string, table: string, fields: any[]) => {
    pivotStore.clear()
    resultStore.setData(null)
    chatStore.resetForDataset()
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
          <Route path="/dashboard/ext/:connectionId" element={<ProtectedRoute><ErrorBoundary><ExternalDashboardPage /></ErrorBoundary></ProtectedRoute>} />
          <Route path="/dashboard/:datasetId" element={<ProtectedRoute><ErrorBoundary><DashboardPage /></ErrorBoundary></ProtectedRoute>} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </StoreProvider>
    </BrowserRouter>
  )
}
