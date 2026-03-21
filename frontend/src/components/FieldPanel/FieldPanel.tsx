import { observer } from 'mobx-react-lite'
import { useState } from 'react'
import { useStore } from '@/stores/RootStore'
import { Input } from '@/components/ui/input'
import { FieldItem } from './FieldItem'
import { Search, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

export const FieldPanel = observer(function FieldPanel() {
  const { datasetStore, pivotStore } = useStore()
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({})

  const toggleCategory = (cat: string) => {
    setCollapsed(prev => ({ ...prev, [cat]: !prev[cat] }))
  }

  return (
    <div className="flex flex-col h-full">
      <div className="px-4 pt-4 pb-3">
        <div className="text-[14px] uppercase tracking-widest text-[#64748b] font-medium mb-3">
          Поля данных
        </div>
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
      <div className="flex-1 min-h-0 overflow-y-auto">
        <div className="px-2 pb-3">
          {Object.entries(datasetStore.fieldsByCategory).map(([category, fields]) => {
            const isCollapsed = collapsed[category]
            const fieldList = fields as any[]
            return (
              <div key={category} className="mb-1">
                <button
                  onClick={() => toggleCategory(category)}
                  className="flex items-center gap-1.5 w-full text-left px-2.5 py-1.5 hover:bg-[#f1f5f9] rounded-lg transition-colors"
                >
                  <ChevronRight
                    className={cn(
                      'h-4 w-4 text-[#94a3b8] transition-transform duration-150',
                      !isCollapsed && 'rotate-90'
                    )}
                  />
                  <span className="text-[13px] uppercase tracking-wider text-[#64748b] font-medium flex-1">
                    {category}
                  </span>
                  <span className="text-[11px] bg-[#f1f5f9] text-[#64748b] rounded-full px-2 py-0.5 font-medium">
                    {fieldList.length}
                  </span>
                </button>
                {!isCollapsed && fieldList.map((field: any) => (
                  <FieldItem
                    key={field.id}
                    field={field}
                    usedIn={pivotStore.isFieldUsed(field.id)}
                  />
                ))}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
})
