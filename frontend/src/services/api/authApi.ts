import { post } from './client'

interface AuthResponse {
  token: string
  email: string
}

export function register(email: string, password: string): Promise<AuthResponse> {
  return post('/api/auth/register', { email, password })
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return post('/api/auth/login', { email, password })
}
