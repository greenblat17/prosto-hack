import { types, getRoot, flow, type Instance } from 'mobx-state-tree'
import {
  sendMessage as sendMessageApi,
  sendExternalMessage as sendExternalMessageApi,
  explainTable as explainTableApi,
  fetchSessions as fetchSessionsApi,
  createSession as createSessionApi,
  deleteSession as deleteSessionApi,
  fetchMessages as fetchMessagesApi,
} from '@/services/api/chatApi'
import type { ChatSession, ChatMessageDto } from '@/types/chat'

const CHAT_TIMEOUT_MS = 60_000
const EXPLAIN_TIMEOUT_MS = 90_000

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
    _abortController: null as AbortController | null,
  }))
  .actions(self => ({
    loadSessions: flow(function* () {
      const root = getRoot(self) as any
      const datasetId = root.datasetStore.currentDatasetId
      if (!datasetId) return

      self.sessionsLoading = true
      try {
        self.sessions = yield fetchSessionsApi(datasetId)
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
      if (!datasetId) {
        self.messages.clear()
        return
      }

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

    cancelRequest() {
      if (self._abortController) {
        self._abortController.abort()
        self._abortController = null
        self.loading = false
      }
    },

    sendMessage: flow(function* (text: string) {
      const root = getRoot(self) as any
      const datasetId = root.datasetStore.currentDatasetId
      const conn = root.connectionStore

      const isExternal = !datasetId && conn.isConnected && conn.connectionId
      if (!datasetId && !isExternal) return

      if (datasetId && !self.currentSessionId) {
        const session: ChatSession = yield createSessionApi(datasetId)
        self.sessions.unshift(session)
        self.currentSessionId = session.id
      }

      const userMsg = {
        id: `msg-${Date.now()}`,
        role: 'user' as const,
        text,
        timestamp: Date.now(),
      }
      self.messages.push(userMsg)

      self._abortController?.abort()
      const ac = new AbortController()
      self._abortController = ac
      self.loading = true

      try {
        let response: Awaited<ReturnType<typeof sendMessageApi>>

        if (isExternal) {
          response = yield sendExternalMessageApi(
            text,
            conn.connectionId,
            conn.selectedSchema,
            conn.selectedTable,
            { signal: ac.signal, timeoutMs: CHAT_TIMEOUT_MS },
          )
        } else {
          response = yield sendMessageApi(text, datasetId!, self.currentSessionId ?? undefined, {
            signal: ac.signal,
            timeoutMs: CHAT_TIMEOUT_MS,
          })
        }

        const assistantMsg = {
          id: `msg-${Date.now()}-ai`,
          role: 'assistant' as const,
          text: response.text,
          timestamp: Date.now(),
          appliedConfig: response.config ?? undefined,
          applied: false,
        }
        self.messages.push(assistantMsg)

        if (self.currentSessionId) {
          const session = self.sessions.find(s => s.id === self.currentSessionId)
          if (session && session.title === 'Новый чат') {
            session.title = text.length > 50 ? text.substring(0, 50) + '...' : text
          }
        }
      } catch (e: any) {
        if (e.message === 'Запрос отменён') return
        const errorMsg = {
          id: `msg-${Date.now()}-err`,
          role: 'assistant' as const,
          text: `Ошибка: ${e.message}`,
          timestamp: Date.now(),
        }
        self.messages.push(errorMsg)
      } finally {
        self.loading = false
        self._abortController = null
      }
    }),

    requestExplain: flow(function* () {
      const root = getRoot(self) as any
      const config = root.pivotStore.configSnapshot
      const datasetId = root.datasetStore.currentDatasetId
      const conn = root.connectionStore

      const isExternal = conn?.isConnected && conn.connectionId && conn.selectedSchema && conn.selectedTable
      if (!datasetId && !isExternal) return

      const userMsg = {
        id: `msg-${Date.now()}`,
        role: 'user' as const,
        text: 'Проанализируй эту таблицу',
        timestamp: Date.now(),
      }
      self.messages.push(userMsg)

      self._abortController?.abort()
      const ac = new AbortController()
      self._abortController = ac
      self.loading = true

      try {
        const request: Parameters<typeof explainTableApi>[0] = isExternal
          ? { config, connectionId: conn.connectionId, schema: conn.selectedSchema, tableName: conn.selectedTable }
          : { config, datasetId }

        const explanation: string = yield explainTableApi(request, {
          signal: ac.signal,
          timeoutMs: EXPLAIN_TIMEOUT_MS,
        })
        const assistantMsg = {
          id: `msg-${Date.now()}-ai`,
          role: 'assistant' as const,
          text: explanation,
          timestamp: Date.now(),
        }
        self.messages.push(assistantMsg)
      } catch (e: any) {
        if (e.message === 'Запрос отменён') return
        const errorMsg = {
          id: `msg-${Date.now()}-err`,
          role: 'assistant' as const,
          text: `Ошибка: ${e.message}`,
          timestamp: Date.now(),
        }
        self.messages.push(errorMsg)
      } finally {
        self.loading = false
        self._abortController = null
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
