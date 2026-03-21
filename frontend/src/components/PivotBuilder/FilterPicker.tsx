import { useState, useRef, useEffect } from 'react'
import { ChevronDown } from 'lucide-react'
import type { FilterOperator } from '@/types/pivot'

const operators: { value: FilterOperator; label: string; symbol: string }[] = [
  { value: 'eq', label: 'Равно', symbol: '=' },
  { value: 'neq', label: 'Не равно', symbol: '≠' },
  { value: 'gt', label: 'Больше', symbol: '>' },
  { value: 'lt', label: 'Меньше', symbol: '<' },
  { value: 'in', label: 'Содержит', symbol: '∈' },
]

interface FilterPickerProps {
  name: string
  operator: FilterOperator
  value: string | string[]
  onChange: (operator: FilterOperator, value: string | string[]) => void
}

export function FilterPicker({ name, operator, value, onChange }: FilterPickerProps) {
  const [open, setOpen] = useState(false)
  const [localOp, setLocalOp] = useState(operator)
  const [localVal, setLocalVal] = useState(
    Array.isArray(value) ? value.join(', ') : (value || '')
  )
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 50)
    } else {
      setLocalOp(operator)
      setLocalVal(Array.isArray(value) ? value.join(', ') : (value || ''))
    }
  }, [operator, value, open])

  const currentOp = operators.find(o => o.value === operator)
  const displayVal = Array.isArray(value) ? value.join(', ') : value
  const hasValue = displayVal && displayVal.length > 0

  const handleApply = () => {
    const trimmed = localVal.trim()
    if (!trimmed) return
    const finalValue = localOp === 'in' ? trimmed.split(',').map(s => s.trim()).filter(Boolean) : trimmed
    onChange(localOp, finalValue)
    setOpen(false)
  }

  return (
    <div className="relative inline-flex">
      <button
        onClick={e => { e.stopPropagation(); setOpen(!open) }}
        className="flex items-center gap-1 text-[13px] font-medium"
      >
        <span className="text-[#0d9488]">{currentOp?.symbol ?? '='}</span>
        {hasValue ? (
          <span className="text-[#334155] max-w-[120px] truncate">{displayVal}</span>
        ) : (
          <span className="text-[#94a3b8] italic">настроить</span>
        )}
        <ChevronDown className="h-3 w-3 opacity-50" />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute left-0 top-full mt-1 z-50 bg-white rounded-xl border border-[#e2e8f0] shadow-xl p-3 min-w-[240px] animate-fade-in-up"
            onClick={e => e.stopPropagation()}
          >
            <div className="text-[12px] text-[#94a3b8] uppercase tracking-wider mb-2 font-medium">
              Фильтр: {name}
            </div>

            <div className="flex gap-1.5 mb-3">
              {operators.map(op => (
                <button
                  key={op.value}
                  onClick={() => setLocalOp(op.value)}
                  className={`flex-1 py-1.5 rounded-lg text-[13px] font-mono font-semibold transition-all ${
                    localOp === op.value
                      ? 'bg-[#0d9488] text-white'
                      : 'bg-[#f1f5f9] text-[#64748b] hover:bg-[#e2e8f0]'
                  }`}
                  title={op.label}
                >
                  {op.symbol}
                </button>
              ))}
            </div>

            <input
              ref={inputRef}
              type="text"
              value={localVal}
              onChange={e => setLocalVal(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleApply()}
              placeholder={localOp === 'in' ? 'значение1, значение2' : 'значение'}
              className="w-full h-9 px-3 rounded-lg border border-[#e2e8f0] text-[14px] text-[#0f172a] placeholder:text-[#94a3b8] focus:border-[#0d9488] focus:outline-none transition-colors"
            />

            <button
              onClick={handleApply}
              disabled={!localVal.trim()}
              className="w-full mt-2 py-2 rounded-lg bg-[#0d9488] text-white text-[14px] font-medium hover:bg-[#0f766e] transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              Применить
            </button>
          </div>
        </>
      )}
    </div>
  )
}
