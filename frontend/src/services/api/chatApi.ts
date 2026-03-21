import { get, post, postText, del, type RequestOptions } from './client'
import type { PivotConfig } from '@/types/pivot'
import type { ChatSession, ChatMessageDto } from '@/types/chat'

interface ChatResponse {
  text: string
  config: PivotConfig
}

export function sendMessage(message: string, datasetId: string, sessionId?: string, opts?: RequestOptions): Promise<ChatResponse> {
  return post('/api/chat/message', { message, datasetId, sessionId }, opts)
}

export interface ExplainRequest {
  config: PivotConfig
  datasetId?: string
  connectionId?: string
  schema?: string
  tableName?: string
}

export function explainTable(request: ExplainRequest, opts?: RequestOptions): Promise<string> {
  return postText('/api/chat/explain', request, opts)
}

export function fetchSessions(datasetId: string): Promise<ChatSession[]> {
  return get(`/api/chat/sessions?datasetId=${datasetId}`)
}

export function createSession(datasetId: string): Promise<ChatSession> {
  return post('/api/chat/sessions', { datasetId })
}

export function deleteSession(sessionId: string): Promise<void> {
  return del(`/api/chat/sessions/${sessionId}`)
}

export function fetchMessages(sessionId: string): Promise<ChatMessageDto[]> {
  return get(`/api/chat/sessions/${sessionId}/messages`)
}
