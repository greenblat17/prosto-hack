import { get, post, postText, del } from './client'
import type { PivotConfig, PivotResult } from '@/types/pivot'
import type { ChatSession, ChatMessageDto } from '@/types/chat'

interface ChatResponse {
  text: string
  config: PivotConfig
}

export function sendMessage(message: string, datasetId: string, sessionId?: string): Promise<ChatResponse> {
  return post('/api/chat/message', { message, datasetId, sessionId })
}

export function explainTable(config: PivotConfig, result: PivotResult): Promise<string> {
  return postText('/api/chat/explain', { config, result })
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
