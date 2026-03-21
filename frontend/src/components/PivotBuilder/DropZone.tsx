import { observer } from 'mobx-react-lite'
import { useDroppable } from '@dnd-kit/core'
import { useStore } from '@/stores/RootStore'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { PivotZone, AggregationType, FilterOperator } from '@/types/pivot'
import { AggregationPicker } from './AggregationPicker'
import { FilterPicker } from './FilterPicker'
import type { ReactNode } from 'react'

const colorMap = {
  blue: { dot: 'bg-[#0d9488]', tag: 'bg-[#f0fdfa] text-[#0f766e]' },
  green: { dot: 'bg-[#0891b2]', tag: 'bg-[#ecfeff] text-[#0e7490]' },
  purple: { dot: 'bg-[#10b981]', tag: 'bg-[#ecfdf5] text-[#059669]' },
  orange: { dot: 'bg-[#f59e0b]', tag: 'bg-[#fffbeb] text-[#b45309]' },
}

interface DropZoneProps {
  zone: PivotZone
  label: string
  icon: ReactNode
  color: keyof typeof colorMap
}

export const DropZone = observer(function DropZone({ zone, label, icon, color }: DropZoneProps) {
  const { pivotStore, resultStore } = useStore()
  const { setNodeRef, isOver } = useDroppable({
    id: `zone-${zone}`,
    data: { zone },
  })

  const colors = colorMap[color]
  const fields = pivotStore[zone]

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'flex items-center gap-3 rounded-lg border border-[#e2e8f0] px-3.5 py-2.5 min-h-[42px] transition-all bg-white',
        isOver && 'border-[#0d9488] bg-[#f0fdfa] shadow-sm',
      )}
    >
      <div className="flex items-center gap-2 w-24 shrink-0">
        <div className={cn('w-2 h-2 rounded-full', colors.dot)} />
        <span className="text-[13px] uppercase tracking-wider text-[#94a3b8] font-medium truncate">
          {label}
        </span>
      </div>
      <div className="flex flex-wrap gap-1.5 flex-1 min-w-0">
        {fields.length === 0 && (
          <span className="text-[14px] text-[#94a3b8] italic flex items-center gap-1.5">
            {icon}
            <span>перетащите поле</span>
          </span>
        )}
        {fields.map((field: any) => {
          const itemKey = zone === 'filters' ? field.uid : field.fieldId
          const removeId = zone === 'filters' ? field.uid : field.fieldId
          return (
            <span
              key={itemKey}
              className={cn(
                'inline-flex items-center gap-1.5 text-[14px] px-2.5 py-1 rounded-md font-medium',
                colors.tag
              )}
            >
              {field.name}
              {zone === 'values' && (
                <AggregationPicker
                  value={field.aggregation}
                  fieldType={field.fieldType ?? 'string'}
                  onChange={(agg: AggregationType) => { pivotStore.setAggregation(field.fieldId, agg); resultStore.executeQuery() }}
                />
              )}
              {zone === 'filters' && (
                <FilterPicker
                  name={field.name}
                  operator={field.operator}
                  value={field.filterValue}
                  onChange={(op: FilterOperator, val: string | string[]) => {
                    pivotStore.setFilter(field.uid, op, val)
                    resultStore.executeQuery()
                  }}
                />
              )}
              <button
                onClick={() => {
                  pivotStore.removeField(zone, removeId)
                  resultStore.executeQuery()
                }}
                className="opacity-30 hover:opacity-70 transition-opacity"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </span>
          )
        })}
      </div>
    </div>
  )
})
