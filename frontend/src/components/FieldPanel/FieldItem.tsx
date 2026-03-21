import { useState } from 'react'
import { useDraggable } from '@dnd-kit/core'
import { cn } from '@/lib/utils'
import { useStore } from '@/stores/RootStore'
import type { PivotZone } from '@/types/pivot'
import { Hash, Type, Calendar, ToggleLeft, GripVertical, BarChart3, Loader2 } from 'lucide-react'
import { fetchColumnStats, type ColumnStats } from '@/services/api/datasetApi'

const typeIcons = {
  string: Type,
  number: Hash,
  date: Calendar,
  boolean: ToggleLeft,
}

const typeBarColors: Record<string, string> = {
  string: 'bg-[#3b82f6]',
  number: 'bg-[#10b981]',
  date: 'bg-[#f59e0b]',
  boolean: 'bg-[#8b5cf6]',
}

const zoneIndicator: Record<PivotZone, string> = {
  rows: 'text-[#0d9488]',
  columns: 'text-[#334155]',
  values: 'text-[#0f172a]',
  filters: 'text-[#78716c]',
}

function formatCompact(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return n.toString()
}

interface FieldItemProps {
  field: { id: string; name: string; type: string }
  usedIn: PivotZone | null
}

export function FieldItem({ field, usedIn }: FieldItemProps) {
  const { datasetStore } = useStore()
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `field-${field.id}`,
    data: { fieldId: field.id, name: field.name, type: field.type },
  })

  const [stats, setStats] = useState<ColumnStats | null>(null)
  const [statsOpen, setStatsOpen] = useState(false)
  const [statsLoading, setStatsLoading] = useState(false)

  const handleInfoClick = async (e: React.MouseEvent) => {
    e.stopPropagation()
    e.preventDefault()
    if (statsOpen) {
      setStatsOpen(false)
      return
    }
    if (!stats) {
      const datasetId = datasetStore.currentDatasetId
      if (!datasetId) return
      setStatsLoading(true)
      try {
        const result = await fetchColumnStats(datasetId, field.id)
        setStats(result)
        setStatsOpen(true)
      } catch {
        // Don't open panel if fetch failed and stats is still null
      } finally {
        setStatsLoading(false)
      }
      return
    }
    setStatsOpen(true)
  }

  const Icon = typeIcons[field.type as keyof typeof typeIcons] ?? Type

  return (
    <div>
      <div
        ref={setNodeRef}
        {...listeners}
        {...attributes}
        className={cn(
          'group flex items-center gap-2.5 px-3 py-1.5 rounded-lg text-[15px] cursor-grab active:cursor-grabbing transition-colors',
          'hover:bg-[#f1f5f9]',
          isDragging && 'opacity-40',
          usedIn ? zoneIndicator[usedIn] : 'text-[#334155]'
        )}
      >
        <div className={cn('w-[3px] h-5 rounded-full shrink-0', typeBarColors[field.type] ?? 'bg-[#94a3b8]')} />
        <GripVertical className="h-4 w-4 shrink-0 opacity-20 group-hover:opacity-40 transition-opacity" />
        <Icon className="h-4 w-4 shrink-0 opacity-40" />
        <span className="truncate flex-1">{field.name}</span>
        {field.type === 'number' && (
          <button
            onPointerDown={e => e.stopPropagation()}
            onClick={handleInfoClick}
            className={cn(
              "shrink-0 p-0.5 rounded transition-all",
              statsOpen
                ? "text-[#0d9488]"
                : "text-[#94a3b8] hover:text-[#0d9488]"
            )}
          >
            {statsLoading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <BarChart3 className="h-3.5 w-3.5" />}
          </button>
        )}
      </div>
      {statsOpen && stats && (
        <div className="mx-3 mb-1 px-3 py-2 rounded-lg bg-[#f8fafc] border border-[#e2e8f0] text-[12px] text-[#64748b] space-y-1.5">
          <div className="flex justify-between">
            <span>{formatCompact(stats.distinctCount)} уник.</span>
            <span>{stats.totalRows > 0 ? Math.round(stats.nullCount / stats.totalRows * 100) : 0}% пусто</span>
          </div>
          {(field.type === 'number' || field.type === 'date') && stats.minValue && (
            <div className="flex justify-between text-[#475569]">
              <span>min: {stats.minValue}</span>
              <span>max: {stats.maxValue}</span>
            </div>
          )}
          {stats.topValues.length > 0 && (
            <div className="space-y-0.5 pt-0.5">
              <span className="text-[11px] text-[#94a3b8]">Топ значения:</span>
              {stats.topValues.map((tv, i) => (
                <div key={i} className="flex items-center gap-2">
                  <div className="flex-1 min-w-0">
                    <div className="h-1 rounded-full bg-[#0d9488] opacity-30"
                      style={{ width: `${stats.topValues[0].count > 0 ? (tv.count / stats.topValues[0].count * 100) : 0}%` }}
                    />
                  </div>
                  <span className="truncate max-w-[100px] text-[#334155]">{tv.value}</span>
                  <span className="shrink-0 tabular-nums">{formatCompact(tv.count)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
