import { observer } from 'mobx-react-lite'
import { cn } from '@/lib/utils'
import { useStore } from '@/stores/RootStore'
import { User, Bot, Check, Plus, Minus } from 'lucide-react'
import type { PivotConfig, AggregationType } from '@/types/pivot'
import { aggregationLabels } from '@/types/pivot'

interface ChatMessageProps {
  message: {
    id: string
    role: 'user' | 'assistant'
    text: string
    timestamp: number
    appliedConfig?: PivotConfig
    applied?: boolean
  }
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
}

function fieldName(f: any): string {
  if (typeof f === 'string') return f
  return f?.name || f?.fieldId || '?'
}

interface DiffItem {
  type: 'add' | 'remove'
  label: string
  zone: string
}

function computeDiff(newConfig: PivotConfig, currentConfig: PivotConfig): DiffItem[] {
  const items: DiffItem[] = []

  const zones: Array<{ key: keyof PivotConfig; label: string }> = [
    { key: 'rows', label: 'строки' },
    { key: 'columns', label: 'колонки' },
    { key: 'values', label: 'значения' },
    { key: 'filters', label: 'фильтры' },
  ]

  for (const { key, label } of zones) {
    const newFields = (newConfig[key] ?? []) as any[]
    const oldFields = (currentConfig[key] ?? []) as any[]

    const newIds = new Set(newFields.map((f: any) => f.fieldId || f))
    const oldIds = new Set(oldFields.map((f: any) => f.fieldId || f))

    for (const f of newFields) {
      const id = f.fieldId || f
      if (!oldIds.has(id)) {
        const agg = f.aggregation ? ` (${aggregationLabels[f.aggregation as AggregationType] ?? f.aggregation})` : ''
        items.push({ type: 'add', label: `${fieldName(f)}${agg}`, zone: label })
      }
    }

    for (const f of oldFields) {
      const id = f.fieldId || f
      if (!newIds.has(id)) {
        items.push({ type: 'remove', label: fieldName(f), zone: label })
      }
    }
  }

  return items
}

const ConfigDiff = observer(function ConfigDiff({ config, messageId, applied }: {
  config: PivotConfig
  messageId: string
  applied: boolean
}) {
  const { pivotStore, chatStore } = useStore()
  const currentConfig = pivotStore.configSnapshot
  const diff = computeDiff(config, currentConfig)

  // If no diff, show summary instead
  const hasContent = diff.length > 0 || !applied

  if (!hasContent && applied) {
    return (
      <div className="mt-2 flex items-center gap-1.5 text-[13px] text-[#0d9488]">
        <Check className="h-3.5 w-3.5" />
        Применено
      </div>
    )
  }

  return (
    <div className="mt-2 rounded-lg border border-[#e2e8f0] bg-[#fafafa] px-3 py-2.5">
      {diff.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-2">
          {diff.map((item, i) => (
            <span
              key={i}
              className={cn(
                "inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[12px] font-medium",
                item.type === 'add'
                  ? "bg-[#dcfce7] text-[#166534]"
                  : "bg-[#fee2e2] text-[#991b1b]"
              )}
            >
              {item.type === 'add' ? <Plus className="h-3 w-3" /> : <Minus className="h-3 w-3" />}
              {item.label} → {item.zone}
            </span>
          ))}
        </div>
      )}
      {applied ? (
        <div className="flex items-center gap-1.5 text-[13px] text-[#0d9488]">
          <Check className="h-3.5 w-3.5" />
          Применено
        </div>
      ) : (
        <button
          onClick={() => chatStore.applyMessageConfig(messageId)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[13px] font-medium bg-[#0d9488] text-white hover:bg-[#0f766e] transition-colors"
        >
          Применить
        </button>
      )}
    </div>
  )
})

function MessageText({ text }: { text: string }) {
  const lines = text.split('\n')
  if (lines.length <= 1) return <>{text}</>

  const hasBullets = lines.some(l => l.startsWith('•') || l.startsWith('- '))
  if (!hasBullets) {
    return (
      <div className="space-y-1">
        {lines.map((line, i) => (
          <div key={i}>{line || '\u00A0'}</div>
        ))}
      </div>
    )
  }

  const title = lines.find(l => !l.startsWith('•') && !l.startsWith('- ') && l.trim())
  const bullets = lines.filter(l => l.startsWith('•') || l.startsWith('- '))

  return (
    <div className="space-y-2.5">
      {title && (
        <div className="font-semibold text-[#0f172a]">{title}</div>
      )}
      <div className="space-y-2">
        {bullets.map((line, i) => {
          const content = line.replace(/^[•\-]\s*/, '')
          return (
            <div key={i} className="flex gap-2 items-start">
              <div className="w-1.5 h-1.5 rounded-full bg-[#0d9488] mt-2 shrink-0" />
              <span className="text-[#334155] leading-relaxed">{content}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === 'user'

  return (
    <div className={cn(
      'flex gap-3',
      isUser ? 'flex-row-reverse animate-slide-in-right' : 'animate-slide-in-left'
    )}>
      <div
        className={cn(
          'h-7 w-7 rounded-lg flex items-center justify-center shrink-0 mt-0.5',
          isUser ? 'bg-[#f1f5f9]' : 'bg-[#0f172a]'
        )}
      >
        {isUser ? (
          <User className="h-4 w-4 text-[#64748b]" />
        ) : (
          <Bot className="h-4 w-4 text-[#2dd4bf]" />
        )}
      </div>
      <div className={cn('max-w-[85%]', isUser && 'flex flex-col items-end')}>
        <div
          className={cn(
            'rounded-xl px-3.5 py-2.5 text-[15px] leading-relaxed shadow-sm',
            isUser
              ? 'bg-[#f1f5f9] text-[#334155]'
              : 'bg-white text-[#334155] border border-[#e2e8f0]'
          )}
        >
          <MessageText text={message.text} />
        </div>
        {!isUser && message.appliedConfig && (
          <ConfigDiff
            config={message.appliedConfig}
            messageId={message.id}
            applied={!!message.applied}
          />
        )}
        <span className="text-[11px] text-[#94a3b8] mt-1 px-1">
          {formatTime(message.timestamp)}
        </span>
      </div>
    </div>
  )
}
