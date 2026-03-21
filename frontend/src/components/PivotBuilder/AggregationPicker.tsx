import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import type { AggregationType } from '@/types/pivot'

const options: { value: AggregationType; label: string }[] = [
  { value: 'raw', label: 'RAW' },
  { value: 'sum', label: 'SUM' },
  { value: 'avg', label: 'AVG' },
  { value: 'count', label: 'COUNT' },
  { value: 'min', label: 'MIN' },
  { value: 'max', label: 'MAX' },
]

interface AggregationPickerProps {
  value: AggregationType
  onChange: (value: AggregationType) => void
}

export function AggregationPicker({ value, onChange }: AggregationPickerProps) {
  const [open, setOpen] = useState(false)

  return (
    <div className="relative">
      <button
        onClick={e => { e.stopPropagation(); setOpen(!open) }}
        className="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-white/60 hover:bg-white border border-[#e2e8f0] text-[12px] font-mono font-semibold text-[#0d9488] hover:text-[#0f766e] transition-all"
      >
        {value.toUpperCase()}
        <ChevronDown className="h-3 w-3 opacity-50" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute left-0 top-full mt-1 z-50 bg-white rounded-lg border border-[#e2e8f0] shadow-xl py-1 min-w-[90px] animate-fade-in-up">
            {options.map(opt => (
              <button
                key={opt.value}
                onClick={e => {
                  e.stopPropagation()
                  onChange(opt.value)
                  setOpen(false)
                }}
                className={`w-full text-left px-3 py-2 text-[14px] font-mono font-medium transition-colors ${
                  opt.value === value
                    ? 'bg-[#f0fdfa] text-[#0d9488]'
                    : 'text-[#334155] hover:bg-[#f8fafc]'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
