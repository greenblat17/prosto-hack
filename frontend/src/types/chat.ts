import type { PivotConfig } from './pivot'

export type ChatRole = 'user' | 'assistant'

export interface ChatMessage {
  id: string
  role: ChatRole
  text: string
  pivotConfig?: PivotConfig
  timestamp: number
}

export interface ChatSession {
  id: string
  datasetId: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessageDto {
  id: string
  role: ChatRole
  text: string
  appliedConfig: string | null
  createdAt: string
}
