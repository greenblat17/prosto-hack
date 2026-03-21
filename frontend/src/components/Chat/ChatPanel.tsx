import { observer } from 'mobx-react-lite'
import { useState, useRef, useEffect } from 'react'
import { useStore } from '@/stores/RootStore'
import { Input } from '@/components/ui/input'
import { ChatMessage } from './ChatMessage'
import { SendHorizontal, Bot, Plus, X, MessageSquare } from 'lucide-react'
import { cn } from '@/lib/utils'

export const ChatPanel = observer(function ChatPanel({ onClose }: { onClose?: () => void }) {
  const { chatStore, datasetStore } = useStore()
  const [input, setInput] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const datasetId = datasetStore.currentDatasetId

  useEffect(() => {
    chatStore.resetForDataset()
    if (datasetId) chatStore.loadSessions()
  }, [chatStore, datasetId])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [chatStore.messages.length, chatStore.loading])

  const handleSend = () => {
    const text = input.trim()
    if (!text || chatStore.loading) return
    setInput('')
    chatStore.sendMessage(text)
  }

  const isEnabled = input.trim().length > 0 && !chatStore.loading

  return (
    <div className="flex flex-col h-full">
      {/* Session tabs */}
      <div className="px-3 pt-3 pb-2 border-b border-[#e2e8f0]">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <Bot className="h-5 w-5 text-[#0d9488]" />
            <span className="text-[13px] uppercase tracking-widest text-[#94a3b8] font-medium">
              AI Чат
            </span>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => chatStore.createSession()}
              className="p-1.5 rounded-lg text-[#94a3b8] hover:text-[#0d9488] hover:bg-[#f0fdfa] transition-colors"
              title="Новый чат"
            >
              <Plus className="h-4 w-4" />
            </button>
            {onClose && (
              <button
                onClick={onClose}
                className="p-1.5 rounded-lg text-[#94a3b8] hover:text-[#334155] hover:bg-[#f1f5f9] transition-colors"
                title="Закрыть"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>
        </div>
        {chatStore.sessions.length > 1 && (
          <div className="flex gap-1 overflow-x-auto pb-1 scrollbar-none">
            {chatStore.sessions.map((session: { id: string; title: string }) => (
              <button
                key={session.id}
                onClick={() => chatStore.switchSession(session.id)}
                className={cn(
                  "flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[12px] whitespace-nowrap shrink-0 transition-all group max-w-[160px]",
                  session.id === chatStore.currentSessionId
                    ? "bg-[#0f172a] text-white"
                    : "text-[#64748b] hover:bg-[#f1f5f9]"
                )}
              >
                <MessageSquare className="h-3 w-3 shrink-0" />
                <span className="truncate">{session.title}</span>
                <button
                  onClick={e => {
                    e.stopPropagation()
                    chatStore.deleteSessionById(session.id)
                  }}
                  className={cn(
                    "shrink-0 p-0.5 rounded transition-colors",
                    session.id === chatStore.currentSessionId
                      ? "text-[#94a3b8] hover:text-white"
                      : "text-[#cbd5e1] hover:text-[#64748b] opacity-0 group-hover:opacity-100"
                  )}
                >
                  <X className="h-3 w-3" />
                </button>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Messages */}
      <div className="flex-1 min-h-0 overflow-y-auto">
        <div className="px-4 pb-4 space-y-4">
          {chatStore.messages.length === 0 && (
            <div className="space-y-5 pt-8">
              <div className="text-center space-y-3">
                <div className="mx-auto w-14 h-14 rounded-2xl bg-[#0f172a] flex items-center justify-center shadow-lg">
                  <Bot className="h-7 w-7 text-[#2dd4bf]" />
                </div>
                <div>
                  <p className="text-[16px] font-semibold text-[#0f172a]">
                    AI Ассистент
                  </p>
                  <p className="text-[14px] text-[#94a3b8] mt-1">
                    Опишите данные, которые хотите увидеть
                  </p>
                </div>
              </div>
            </div>
          )}
          {chatStore.messages.map((msg: any) => (
            <ChatMessage
              key={msg.id}
              message={{
                id: msg.id,
                role: msg.role,
                text: msg.text,
                timestamp: msg.timestamp,
                appliedConfig: msg.appliedConfig,
                applied: msg.applied,
              }}
            />
          ))}
          {chatStore.loading && (
            <div className="flex items-center gap-3 py-2 px-1 animate-slide-in-left">
              <div className="h-7 w-7 rounded-lg flex items-center justify-center shrink-0 bg-[#0f172a]">
                <Bot className="h-4 w-4 text-[#2dd4bf]" />
              </div>
              <div className="flex items-center gap-2 text-[15px] text-[#64748b]">
                <div className="h-2 w-2 rounded-full bg-[#0d9488] animate-pulse" />
                Анализирую...
              </div>
              <button
                onClick={() => chatStore.cancelRequest()}
                className="ml-auto shrink-0 px-2.5 py-1 rounded-lg text-[12px] text-[#64748b] hover:text-[#dc2626] hover:bg-[#fef2f2] border border-[#e2e8f0] transition-colors"
              >
                Отмена
              </button>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input */}
      <div className="px-4 pb-4 pt-3 border-t border-[#e2e8f0]">
        <div className="flex gap-2 items-end">
          <div className="flex-1 relative">
            <Input
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSend()}
              placeholder="Задайте вопрос..."
              className="h-10 text-[15px] bg-[#f8fafc] border-[#e2e8f0] text-[#0f172a] placeholder:text-[#94a3b8] focus:border-[#0d9488] focus:bg-white rounded-xl pr-10 transition-colors"
              disabled={chatStore.loading}
            />
            {!input && !chatStore.loading && (
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[11px] text-[#cbd5e1] pointer-events-none">
                Enter ↵
              </span>
            )}
          </div>
          <button
            onClick={handleSend}
            disabled={!isEnabled}
            className={cn(
              "h-10 w-10 shrink-0 flex items-center justify-center rounded-xl border transition-all duration-200",
              isEnabled
                ? "bg-[#0d9488] border-[#0d9488] text-white shadow-sm hover:bg-[#0f766e] hover:shadow-md"
                : "bg-[#f8fafc] border-[#e2e8f0] text-[#cbd5e1]"
            )}
          >
            <SendHorizontal className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  )
})
