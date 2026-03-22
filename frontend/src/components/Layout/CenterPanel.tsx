import { useState } from 'react'
import { observer } from 'mobx-react-lite'
import { useStore } from '@/stores/RootStore'
import { PivotBuilder } from '@/components/PivotBuilder/PivotBuilder'
import { PivotTable } from '@/components/PivotTable/PivotTable'
import { ChartView } from '@/components/PivotTable/ChartView'
import { ViewToggle, type ViewMode } from '@/components/PivotTable/ViewToggle'
import { ExportMenu } from '@/components/Export/ExportMenu'
import { Bot, PanelLeft, PanelTop } from 'lucide-react'
import { cn } from '@/lib/utils'

interface CenterPanelProps {
  chatOpen: boolean
  onToggleChat: () => void
  fieldsOpen: boolean
  onToggleFields: () => void
}

export const CenterPanel = observer(function CenterPanel({ chatOpen, onToggleChat, fieldsOpen, onToggleFields }: CenterPanelProps) {
  const { pivotStore, resultStore, chatStore } = useStore()
  const { viewMode, data } = resultStore
  const [builderOpen, setBuilderOpen] = useState(true)

  const showChart = viewMode !== 'table' && pivotStore.isValid && data && data.rows.length > 0

  return (
    <div className="flex flex-col h-full">
      {builderOpen && <PivotBuilder />}
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
