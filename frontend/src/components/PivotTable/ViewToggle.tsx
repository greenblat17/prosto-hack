import { Table2, BarChart3, TrendingUp } from 'lucide-react'
import { cn } from '@/lib/utils'

export type ViewMode = 'table' | 'bar' | 'line'

interface ViewToggleProps {
  value: ViewMode
  onChange: (mode: ViewMode) => void
}

const modes: { mode: ViewMode; icon: typeof Table2; label: string }[] = [
  { mode: 'table', icon: Table2, label: 'Таблица' },
  { mode: 'bar', icon: BarChart3, label: 'Столбцы' },
  { mode: 'line', icon: TrendingUp, label: 'Линии' },
]

export function ViewToggle({ value, onChange }: ViewToggleProps) {
  return (
    <div className="flex items-center gap-0.5 bg-[#f5f5f5] rounded-md p-0.5">
      {modes.map(({ mode, icon: Icon, label }) => (
        <button
          key={mode}
          onClick={() => onChange(mode)}
          className={cn(
            'flex items-center gap-1 px-2 py-1 rounded text-[12px] transition-all',
            value === mode
              ? 'bg-white text-[#333] shadow-sm font-medium'
              : 'text-[#999] hover:text-[#666]'
          )}
          title={label}
        >
          <Icon className="h-3.5 w-3.5" />
          <span className="hidden sm:inline">{label}</span>
        </button>
      ))}
    </div>
  )
}
