import {
  BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer,
} from 'recharts'
import type { PivotResult } from '@/types/pivot'
import type { ViewMode } from './ViewToggle'

const COLORS = ['#0d9488', '#0f172a', '#64748b', '#0f766e', '#334155', '#94a3b8']

interface ChartViewProps {
  data: PivotResult
  mode: ViewMode
}

export function ChartView({ data, mode }: ChartViewProps) {
  const allColumns = Object.keys(data.rows[0]?.values ?? {})
  const valueColumns = allColumns.filter(col => {
    const sample = data.rows.find(r => r.values[col] != null)
    return sample ? typeof sample.values[col] === 'number' : true
  })

  const chartData = data.rows.map(row => {
    const numericValues: Record<string, number> = {}
    for (const col of valueColumns) {
      const v = row.values[col]
      numericValues[col] = typeof v === 'number' ? v : 0
    }
    return { name: row.keys.join(' / '), ...numericValues }
  })

  const commonProps = {
    data: chartData,
    margin: { top: 10, right: 20, left: 10, bottom: 5 },
  }

  return (
    <div className="flex-1 p-4">
      <ResponsiveContainer width="100%" height="100%" minHeight={300}>
        {mode === 'bar' ? (
          <BarChart {...commonProps}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
            <XAxis
              dataKey="name"
              tick={{ fontSize: 12, fill: '#64748b' }}
              tickLine={false}
              axisLine={{ stroke: '#e2e8f0' }}
            />
            <YAxis
              tick={{ fontSize: 12, fill: '#64748b' }}
              tickLine={false}
              axisLine={{ stroke: '#e2e8f0' }}
              tickFormatter={(v: number) =>
                v >= 1000 ? `${(v / 1000).toFixed(0)}k` : String(v)
              }
            />
            <Tooltip
              formatter={(value) =>
                new Intl.NumberFormat('ru-RU').format(Number(value))
              }
              contentStyle={{
                fontSize: 13,
                borderRadius: 8,
                border: '1px solid #e2e8f0',
                boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
              }}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            {valueColumns.map((col, i) => (
              <Bar
                key={col}
                dataKey={col}
                fill={COLORS[i % COLORS.length]}
                radius={[3, 3, 0, 0]}
                opacity={0.85}
              />
            ))}
          </BarChart>
        ) : (
          <LineChart {...commonProps}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
            <XAxis
              dataKey="name"
              tick={{ fontSize: 12, fill: '#64748b' }}
              tickLine={false}
              axisLine={{ stroke: '#e2e8f0' }}
            />
            <YAxis
              tick={{ fontSize: 12, fill: '#64748b' }}
              tickLine={false}
              axisLine={{ stroke: '#e2e8f0' }}
              tickFormatter={(v: number) =>
                v >= 1000 ? `${(v / 1000).toFixed(0)}k` : String(v)
              }
            />
            <Tooltip
              formatter={(value) =>
                new Intl.NumberFormat('ru-RU').format(Number(value))
              }
              contentStyle={{
                fontSize: 13,
                borderRadius: 8,
                border: '1px solid #e2e8f0',
                boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
              }}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            {valueColumns.map((col, i) => (
              <Line
                key={col}
                type="monotone"
                dataKey={col}
                stroke={COLORS[i % COLORS.length]}
                strokeWidth={2}
                dot={{ r: 3, fill: COLORS[i % COLORS.length] }}
                activeDot={{ r: 5 }}
              />
            ))}
          </LineChart>
        )}
      </ResponsiveContainer>
    </div>
  )
}
