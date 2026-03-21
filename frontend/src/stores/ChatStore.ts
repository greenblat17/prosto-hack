import { types, getRoot, flow, type Instance } from 'mobx-state-tree'
import {
  sendMessage as sendMessageApi,
  explainTable as explainTableApi,
  fetchSessions as fetchSessionsApi,
  createSession as createSessionApi,
  deleteSession as deleteSessionApi,
  fetchMessages as fetchMessagesApi,
} from '@/services/api/chatApi'
import type { ChatSession, ChatMessageDto } from '@/types/chat'

const ChatMessageModel = types.model('ChatMessage', {
  id: types.identifier,
  role: types.enumeration(['user', 'assistant']),
  text: types.string,
  timestamp: types.number,
  appliedConfig: types.maybe(types.frozen()),
  applied: types.optional(types.boolean, false),
})

export const ChatStore = types
  .model('ChatStore', {
    messages: types.array(ChatMessageModel),
    loading: types.optional(types.boolean, false),
    currentSessionId: types.maybeNull(types.string),
  })
  .volatile(() => ({
    sessions: [] as ChatSession[],
    sessionsLoading: false,
  }))
  .actions(self => ({
    loadSessions: flow(function* () {
      const root = getRoot(self) as any
      const datasetId = root.datasetStore.currentDatasetId
      if (!datasetId) return

      self.sessionsLoading = true
      try {
        self.sessions = yield fetchSessionsApi(datasetId)
        // If we have sessions but none selected, select the first one
        if (self.sessions.length > 0 && !self.currentSessionId) {
          const first = self.sessions[0]
          self.currentSessionId = first.id
          yield (self as any).loadMessages(first.id)
        }
      } finally {
        self.sessionsLoading = false
      }
    }),

    createSession: flow(function* () {
      const root = getRoot(self) as any
      const datasetId = root.datasetStore.currentDatasetId
      if (!datasetId) return

      const session: ChatSession = yield createSessionApi(datasetId)
      self.sessions.unshift(session)
      self.currentSessionId = session.id
      self.messages.clear()
    }),

    deleteSessionById: flow(function* (sessionId: string) {
      yield deleteSessionApi(sessionId)
      self.sessions = self.sessions.filter(s => s.id !== sessionId)
      if (self.currentSessionId === sessionId) {
        if (self.sessions.length > 0) {
          self.currentSessionId = self.sessions[0].id
          yield (self as any).loadMessages(self.sessions[0].id)
        } else {
          self.currentSessionId = null
          self.messages.clear()
        }
      }
    }),

    switchSession: flow(function* (sessionId: string) {
      self.currentSessionId = sessionId
      yield (self as any).loadMessages(sessionId)
    }),

    loadMessages: flow(function* (sessionId: string) {
      const msgs: ChatMessageDto[] = yield fetchMessagesApi(sessionId)
      self.messages.clear()
      for (const m of msgs) {
        let appliedConfig = undefined
        if (m.appliedConfig) {
          try { appliedConfig = JSON.parse(m.appliedConfig) } catch {}
        }
        self.messages.push({
          id: m.id,
          role: m.role as 'user' | 'assistant',
          text: m.text,
          timestamp: new Date(m.createdAt).getTime(),
          appliedConfig,
        })
      }
    }),

    sendMessage: flow(function* (text: string) {
      const root = getRoot(self) as any
      const datasetId = root.datasetStore.currentDatasetId
      if (!datasetId) return

      // Auto-create session if none exists
      if (!self.currentSessionId) {
        const session: ChatSession = yield createSessionApi(datasetId)
        self.sessions.unshift(session)
        self.currentSessionId = session.id
      }

      // Optimistic user message
      const userMsg = {
        id: `msg-${Date.now()}`,
        role: 'user' as const,
        text,
        timestamp: Date.now(),
      }
      self.messages.push(userMsg)
      self.loading = true

      try {
        const response: Awaited<ReturnType<typeof sendMessageApi>> =
          yield sendMessageApi(text, datasetId, self.currentSessionId ?? undefined)

        const assistantMsg = {
          id: `msg-${Date.now()}-ai`,
          role: 'assistant' as const,
          text: response.text,
          timestamp: Date.now(),
          appliedConfig: response.config ?? undefined,
          applied: false,
        }
        self.messages.push(assistantMsg)

        // Update session title in local list
        if (self.currentSessionId) {
          const session = self.sessions.find(s => s.id === self.currentSessionId)
          if (session && session.title === 'Новый чат') {
            session.title = text.length > 50 ? text.substring(0, 50) + '...' : text
          }
        }
      } catch (e: any) {
        const errorMsg = {
          id: `msg-${Date.now()}-err`,
          role: 'assistant' as const,
          text: `Ошибка: ${e.message}`,
          timestamp: Date.now(),
        }
        self.messages.push(errorMsg)
      } finally {
        self.loading = false
      }
    }),

    requestExplain: flow(function* () {
      const root = getRoot(self) as any
      const data = root.resultStore.data
      const config = root.pivotStore.configSnapshot
      if (!data || data.rows.length === 0) return

      const userMsg = {
        id: `msg-${Date.now()}`,
        role: 'user' as const,
        text: 'Explain this table',
        timestamp: Date.now(),
      }
      self.messages.push(userMsg)
      self.loading = true

      try {
        const plainData = JSON.parse(JSON.stringify(data))
        const explanation: string = yield explainTableApi(config, plainData)
        const assistantMsg = {
          id: `msg-${Date.now()}-ai`,
          role: 'assistant' as const,
          text: explanation,
          timestamp: Date.now(),
        }
        self.messages.push(assistantMsg)
      } catch (e: any) {
        const errorMsg = {
          id: `msg-${Date.now()}-err`,
          role: 'assistant' as const,
          text: `Ошибка: ${e.message}`,
          timestamp: Date.now(),
        }
        self.messages.push(errorMsg)
      } finally {
        self.loading = false
      }
    }),

    resetForDataset() {
      self.currentSessionId = null
      self.messages.clear()
      self.sessions = []
    },
    applyMessageConfig: flow(function* (messageId: string) {
      const msg = self.messages.find(m => m.id === messageId)
      if (!msg || !msg.appliedConfig || msg.applied) return
      const root = getRoot(self) as any
      root.pivotStore.applyConfig(msg.appliedConfig)
      yield root.resultStore.executeQuery()
      msg.applied = true
    }),
    clearHistory() {
      self.messages.clear()
    },
  }))

export type IChatStore = Instance<typeof ChatStore>
