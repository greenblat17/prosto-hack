import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import type { AggregationType } from '@/types/pivot'

interface AggOption {
  value: AggregationType
  label: string
}

interface AggGroup {
  title: string
  options: AggOption[]
}

const groups: AggGroup[] = [
  {
    title: 'Базовые',
    options: [
      { value: 'original', label: 'Оригинал' },
      { value: 'count', label: 'Количество' },
      { value: 'count_distinct', label: 'Кол-во уникальных' },
      { value: 'list_distinct', label: 'Список уникальных' },
    ],
  },
  {
    title: 'Математические',
    options: [
      { value: 'sum', label: 'Сумма' },
      { value: 'int_sum', label: 'Целочисленная сумма' },
      { value: 'avg', label: 'Среднее' },
      { value: 'median', label: 'Медиана' },
      { value: 'min', label: 'Минимум' },
      { value: 'max', label: 'Максимум' },
      { value: 'variance', label: 'Дисперсия' },
      { value: 'stddev', label: 'Ст. отклонение' },
    ],
  },
  {
    title: 'Позиционные',
    options: [
      { value: 'first', label: 'Первое' },
      { value: 'last', label: 'Последнее' },
      { value: 'running_sum', label: 'Нарастающий итог' },
    ],
  },
  {
    title: 'Доля от суммы',
    options: [
      { value: 'sum_pct_total', label: '% от итога' },
      { value: 'sum_pct_row', label: '% от строк' },
      { value: 'sum_pct_col', label: '% от колонок' },
    ],
  },
  {
    title: 'Доля от количества',
    options: [
      { value: 'count_pct_total', label: '% от итога' },
      { value: 'count_pct_row', label: '% от строк' },
      { value: 'count_pct_col', label: '% от колонок' },
    ],
  },
]

const shortLabels: Record<AggregationType, string> = {
  original: 'ОРИГ',
  count: 'КОЛ',
  count_distinct: 'УНИК',
  list_distinct: 'СПИС',
  sum: 'СУММ',
  int_sum: 'ЦЕЛ',
  avg: 'СРЕД',
  median: 'МЕД',
  variance: 'ДИСП',
  stddev: 'ОТКЛ',
  min: 'МИН',
  max: 'МАКС',
  first: 'ПЕРВ',
  last: 'ПОСЛ',
  running_sum: 'НАРАСТ',
  sum_pct_total: '%∑',
  sum_pct_row: '%∑ стр',
  sum_pct_col: '%∑ кол',
  count_pct_total: '%#',
  count_pct_row: '%# стр',
  count_pct_col: '%# кол',
}

const numericOnly = new Set<AggregationType>([
  'sum', 'int_sum', 'avg', 'median', 'min', 'max', 'variance', 'stddev',
  'running_sum', 'sum_pct_total', 'sum_pct_row', 'sum_pct_col',
])

interface AggregationPickerProps {
  value: AggregationType
  fieldType?: string
  onChange: (value: AggregationType) => void
}

export function AggregationPicker({ value, fieldType, onChange }: AggregationPickerProps) {
  const [open, setOpen] = useState(false)
  const isNumeric = fieldType === 'number'

  const filteredGroups = groups
    .map(group => ({
      ...group,
      options: group.options.filter(opt => isNumeric || !numericOnly.has(opt.value)),
    }))
    .filter(group => group.options.length > 0)

  return (
    <div className="relative">
      <button
        onClick={e => { e.stopPropagation(); setOpen(!open) }}
        className="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-white/60 hover:bg-white border border-[#e2e8f0] text-[11px] font-semibold text-[#0d9488] hover:text-[#0f766e] transition-all max-w-[80px] truncate"
      >
        <span className="truncate">{shortLabels[value]}</span>
        <ChevronDown className="h-3 w-3 opacity-50 shrink-0" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute left-0 top-full mt-1 z-50 bg-white rounded-lg border border-[#e2e8f0] shadow-xl py-1 min-w-[180px] max-h-[320px] overflow-y-auto animate-fade-in-up">
            {filteredGroups.map((group, gi) => (
              <div key={gi}>
                {gi > 0 && <div className="border-t border-[#f1f5f9] my-1" />}
                <div className="px-3 pt-1.5 pb-0.5 text-[10px] font-semibold uppercase tracking-wider text-[#94a3b8]">
                  {group.title}
                </div>
                {group.options.map(opt => (
                  <button
                    key={opt.value}
                    onClick={e => {
                      e.stopPropagation()
                      onChange(opt.value)
                      setOpen(false)
                    }}
                    className={`w-full text-left px-3 py-1.5 text-[13px] transition-colors ${
                      opt.value === value
                        ? 'bg-[#f0fdfa] text-[#0d9488] font-medium'
                        : 'text-[#334155] hover:bg-[#f8fafc]'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
