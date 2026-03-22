import { observer } from 'mobx-react-lite'
import { useEffect, useState, useMemo, useRef, useCallback } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useStore } from '@/stores/RootStore'
import { cn } from '@/lib/utils'
import { ArrowUp, ArrowDown, ArrowUpDown, BarChart3, Type, Hash, Calendar, X } from 'lucide-react'

function formatValue(v: number | string): string {
  if (v == null) return '—'
  if (typeof v === 'string') return v || '—'
  if (isNaN(v)) return '—'
  return new Intl.NumberFormat('ru-RU', {
    maximumFractionDigits: 2,
  }).format(v)
}

type SortDir = 'asc' | 'desc' | null

const MIN_COL_WIDTH = 80

export const PivotTable = observer(function PivotTable() {
  const { resultStore, pivotStore } = useStore()
  const { data, loading, error } = resultStore
  const [sortCol, setSortCol] = useState<string | null>(null)
  const [sortDir, setSortDir] = useState<SortDir>(null)
  const [colWidths, setColWidths] = useState<Record<string, number>>({})
  const resizeRef = useRef<{ col: string; startX: number; startW: number } | null>(null)

  useEffect(() => {
    setSortCol(null)
    setSortDir(null)
    setColWidths({})
  }, [data])

  const handleResizeStart = useCallback((col: string, e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    const th = (e.target as HTMLElement).closest('th')
    const startW = th?.offsetWidth ?? 150
    resizeRef.current = { col, startX: e.clientX, startW }

    const onMouseMove = (ev: MouseEvent) => {
      if (!resizeRef.current) return
      const delta = ev.clientX - resizeRef.current.startX
      const newW = Math.max(MIN_COL_WIDTH, resizeRef.current.startW + delta)
      setColWidths(prev => ({ ...prev, [resizeRef.current!.col]: newW }))
    }

    const onMouseUp = () => {
      resizeRef.current = null
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
    }

    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
  }, [])

  const columnRanges = useMemo(() => {
    if (!data || data.rows.length === 0) return {}
    const valueColumns = Object.keys(data.rows[0]?.values ?? {})
    const ranges: Record<string, { min: number; max: number }> = {}
    for (const col of valueColumns) {
      let min = Infinity
      let max = -Infinity
      for (const row of data.rows) {
        const v = row.values[col]
        if (typeof v !== 'number' || !Number.isFinite(v)) continue
        if (v < min) min = v
        if (v > max) max = v
      }
      if (!Number.isFinite(min)) { min = 0; max = 0 }
      ranges[col] = { min, max }
    }
    return ranges
  }, [data])

  const handleSort = (col: string) => {
    if (sortCol !== col) {
      setSortCol(col)
      setSortDir('asc')
    } else if (sortDir === 'asc') {
      setSortDir('desc')
    } else {
      setSortCol(null)
      setSortDir(null)
    }
  }

  const rowFieldNames = pivotStore.rows.map((f: any) => f.name)
  const valueColumns = data ? Object.keys(data.rows[0]?.values ?? {}) : []
  const hasCustomWidths = Object.keys(colWidths).length > 0

  const sortedRows = useMemo(() => {
    if (!data || !sortCol || !sortDir) return data?.rows ?? []

    const rows = [...data.rows]
    const keyIndex = rowFieldNames.indexOf(sortCol)
    const isKeyCol = keyIndex >= 0

    rows.sort((a, b) => {
      let cmp: number
      if (isKeyCol) {
        const aVal = a.keys[keyIndex] ?? ''
        const bVal = b.keys[keyIndex] ?? ''
        cmp = aVal.localeCompare(bVal, 'ru')
      } else {
        const aVal = a.values[sortCol] ?? 0
        const bVal = b.values[sortCol] ?? 0
        if (typeof aVal === 'string' || typeof bVal === 'string') {
          cmp = String(aVal).localeCompare(String(bVal), 'ru')
        } else {
          cmp = (aVal as number) - (bVal as number)
        }
      }
      return sortDir === 'desc' ? -cmp : cmp
    })

    return rows
  }, [data, sortCol, sortDir, rowFieldNames])

  const parentRef = useRef<HTMLDivElement>(null)

  const rowVirtualizer = useVirtualizer({
    count: sortedRows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 40,
    overscan: 20,
  })

  function SortIcon({ col }: { col: string }) {
    if (sortCol !== col) return <ArrowUpDown className="h-3 w-3 opacity-0 group-hover:opacity-40 transition-opacity" />
    if (sortDir === 'asc') return <ArrowUp className="h-3 w-3 text-[#0d9488]" />
    return <ArrowDown className="h-3 w-3 text-[#0d9488]" />
  }

  function ResizeHandle({ col }: { col: string }) {
    return (
      <div
        onMouseDown={e => handleResizeStart(col, e)}
        className="absolute right-0 top-2 bottom-2 w-[3px] cursor-col-resize bg-[#e2e8f0] hover:bg-[#0d9488] hover:w-[4px] rounded-full transition-all z-10"
      />
    )
  }

  if (!pivotStore.isValid) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center space-y-4">
          <BarChart3 className="h-12 w-12 text-[#ccfbf1] mx-auto" />
          <div className="space-y-1">
            <p className="text-[16px] font-medium text-[#475569]">Настройте сводную таблицу</p>
            <p className="text-[14px] text-[#94a3b8]">
              Перетащите поля в зоны выше или используйте AI-ассистент
            </p>
          </div>
          <div className="flex justify-center gap-6 pt-2">
            <div className="flex items-center gap-2 text-[13px] text-[#94a3b8]">
              <div className="w-[3px] h-4 rounded-full bg-[#3b82f6]" />
              <Type className="h-3.5 w-3.5" />
              <span>→ Строки</span>
            </div>
            <div className="flex items-center gap-2 text-[13px] text-[#94a3b8]">
              <div className="w-[3px] h-4 rounded-full bg-[#10b981]" />
              <Hash className="h-3.5 w-3.5" />
              <span>→ Значения</span>
            </div>
            <div className="flex items-center gap-2 text-[13px] text-[#94a3b8]">
              <div className="w-[3px] h-4 rounded-full bg-[#f59e0b]" />
              <Calendar className="h-3.5 w-3.5" />
              <span>→ Колонки</span>
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3">
        <div className="flex items-center gap-2 text-[14px] text-[#94a3b8]">
          <div className="h-2 w-2 rounded-full bg-[#0d9488] animate-pulse" />
          Выполняется запрос...
        </div>
        <button
          onClick={() => resultStore.cancelQuery()}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[13px] text-[#64748b] hover:text-[#dc2626] hover:bg-[#fef2f2] border border-[#e2e8f0] transition-colors"
        >
          <X className="h-3.5 w-3.5" />
          Отменить
        </button>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center text-[#dc2626] text-[14px]">
        {error}
      </div>
    )
  }

  if (!data || data.rows.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-[#94a3b8] text-[14px]">
        Нет данных
      </div>
    )
  }

  function barWidth(col: string, value: number): number {
    if (!Number.isFinite(value)) return 0
    const range = columnRanges[col]
    if (!range || range.max === range.min) return 0
    return ((value - range.min) / (range.max - range.min)) * 100
  }

  function colStyle(col: string): React.CSSProperties | undefined {
    const w = colWidths[col]
    if (!w) return undefined
    return { width: w, minWidth: MIN_COL_WIDTH }
  }

  return (
    <div ref={parentRef} className="flex-1 min-h-0 overflow-auto">
      <div className="p-4">
        <div className="rounded-xl border border-[#e2e8f0] overflow-x-auto">
          <table
            className={cn(
              "w-full min-w-max text-[15px] border-collapse",
              hasCustomWidths && "table-fixed"
            )}
          >
            <thead>
              <tr className="bg-[#f8fafc] border-b border-[#e2e8f0]">
                {rowFieldNames.map((name: string, i: number) => (
                  <th
                    key={`row-${i}`}
                    onClick={() => handleSort(name)}
                    style={colStyle(name)}
                    className="relative text-left font-semibold text-[#0f172a] py-3 px-4 whitespace-nowrap text-[14px] uppercase tracking-wide sticky top-0 bg-[#f8fafc] cursor-pointer select-none hover:bg-[#f1f5f9] transition-colors group"
                  >
                    <span className="flex items-center gap-1.5">
                      {name}
                      <SortIcon col={name} />
                    </span>
                    <ResizeHandle col={name} />
                  </th>
                ))}
                {valueColumns.map((col, i) => (
                  <th
                    key={`val-${i}`}
                    onClick={() => handleSort(col)}
                    style={colStyle(col)}
                    className="relative text-right font-semibold text-[#0f172a] py-3 px-4 whitespace-nowrap text-[14px] uppercase tracking-wide sticky top-0 bg-[#f8fafc] cursor-pointer select-none hover:bg-[#f1f5f9] transition-colors group"
                  >
                    <span className="flex items-center justify-end gap-1.5">
                      {col}
                      <SortIcon col={col} />
                    </span>
                    <ResizeHandle col={col} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rowVirtualizer.getVirtualItems().length > 0 && (
                <tr style={{ height: `${rowVirtualizer.getVirtualItems()[0].start}px` }}>
                  <td colSpan={rowFieldNames.length + valueColumns.length} />
                </tr>
              )}
              {rowVirtualizer.getVirtualItems().map((virtualRow) => {
                const row = sortedRows[virtualRow.index] as { keys: string[]; values: Record<string, number | string> }
                const rowIdx = virtualRow.index
                const tooltipParts = [
                  ...row.keys.map((k: string, i: number) => `${rowFieldNames[i]}: ${k}`),
                  ...valueColumns.map(col => `${col}: ${formatValue(row.values[col])}`),
                ]
                const tooltip = tooltipParts.join('\n')
                return (
                  <tr
                    key={rowIdx}
                    title={tooltip}
                    className="border-b border-[#f1f5f9] hover:bg-[#eef9f7] transition-colors even:bg-[#fafafa] cursor-default"
                  >
                    {row.keys.map((key: string, keyIdx: number) => (
                      <td
                        key={`k-${keyIdx}`}
                        className="py-2.5 px-4 text-[#334155] whitespace-nowrap overflow-hidden text-ellipsis"
                      >
                        {key}
                      </td>
                    ))}
                    {valueColumns.map((col, colIdx) => {
                      const value = row.values[col]
                      const isText = typeof value === 'string'
                      const width = isText ? 0 : barWidth(col, value as number)
                      return (
                        <td
                          key={`v-${colIdx}`}
                          className={cn(
                            "py-2.5 px-4 text-[#475569] relative overflow-hidden",
                            isText ? "text-left max-w-[300px]" : "text-right font-mono tabular-nums"
                          )}
                        >
                          {!isText && (
                            <div
                              className="absolute inset-y-0 right-0 bg-[#99f6e4] opacity-60 transition-all"
                              style={{ width: `${width}%` }}
                            />
                          )}
                          <span className={cn("relative z-10", isText && "truncate block")}>{formatValue(value)}</span>
                        </td>
                      )
                    })}
                  </tr>
                )
              })}
              {rowVirtualizer.getVirtualItems().length > 0 && (
                <tr style={{ height: `${rowVirtualizer.getTotalSize() - (rowVirtualizer.getVirtualItems().at(-1)?.end ?? 0)}px` }}>
                  <td colSpan={rowFieldNames.length + valueColumns.length} />
                </tr>
              )}
              {data.totals && Object.keys(data.totals).length > 0 && (
                <tr className="font-semibold border-t-2 border-[#cbd5e1] bg-[#f8fafc]">
                  <td
                    colSpan={rowFieldNames.length}
                    className="py-3 px-4 text-[#334155]"
                  >
                    {(() => {
                      const aggs = pivotStore.values.map((v: any) => v.aggregation)
                      const uniqueAggs = [...new Set(aggs)] as string[]
                      const labels: Record<string, string> = {
                        sum: 'Сумма', avg: 'Среднее', count: 'Количество',
                        min: 'Минимум', max: 'Максимум', original: 'Значение',
                        sum_pct_total: '% от общего', sum_pct_row: '% по строке',
                        sum_pct_col: '% по столбцу', running_sum: 'Нарастающий итог',
                        count_pct_total: '% количества',
                      }
                      const aggLabel = uniqueAggs.length === 1
                        ? labels[uniqueAggs[0]] ?? uniqueAggs[0]
                        : uniqueAggs.map(a => labels[a] ?? a).join(', ')
                      return `Итого (${aggLabel})`
                    })()}
                  </td>
                  {valueColumns.map((col, i) => {
                    const val = data.totals[col]
                    const isText = typeof val === 'string'
                    return (
                      <td
                        key={`t-${i}`}
                        className={cn(
                          "py-3 px-4 text-[#0f172a]",
                          isText ? "text-left" : "text-right font-mono tabular-nums"
                        )}
                      >
                        {formatValue(val ?? (isText ? '' : 0))}
                      </td>
                    )
                  })}
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {resultStore.totalRows > resultStore.limit && (
          <div className="flex items-center justify-between mt-3 px-1 text-[14px] text-[#64748b]">
            <span>
              Строки {resultStore.offset + 1}–{Math.min(resultStore.offset + resultStore.limit, resultStore.totalRows)} из {resultStore.totalRows.toLocaleString('ru-RU')}
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={!resultStore.hasPrevPage}
                onClick={() => resultStore.prevPage()}
                className="px-3 py-1.5 rounded-lg border border-[#e2e8f0] text-[13px] hover:bg-[#f8fafc] transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Назад
              </button>
              <span className="text-[13px]">
                {resultStore.currentPage} / {resultStore.totalPages}
              </span>
              <button
                disabled={!resultStore.hasNextPage}
                onClick={() => resultStore.nextPage()}
                className="px-3 py-1.5 rounded-lg border border-[#e2e8f0] text-[13px] hover:bg-[#f8fafc] transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Вперёд
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
})
