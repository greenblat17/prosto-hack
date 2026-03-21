import { types, flow } from 'mobx-state-tree'
import { login as loginApi, register as registerApi } from '@/services/api/authApi'

export const AuthStore = types
  .model('AuthStore', {
    token: types.maybeNull(types.string),
    email: types.maybeNull(types.string),
  })
  .views(self => ({
    get isAuthenticated() {
      return !!self.token
    },
  }))
  .actions(self => ({
    afterCreate() {
      const token = localStorage.getItem('auth_token')
      const email = localStorage.getItem('auth_email')
      if (token) {
        self.token = token
        self.email = email
      }
    },
    login: flow(function* (email: string, password: string) {
      const res: { token: string; email: string } = yield loginApi(email, password)
      self.token = res.token
      self.email = res.email
      localStorage.setItem('auth_token', res.token)
      localStorage.setItem('auth_email', res.email)
    }),
    register: flow(function* (email: string, password: string) {
      const res: { token: string; email: string } = yield registerApi(email, password)
      self.token = res.token
      self.email = res.email
      localStorage.setItem('auth_token', res.token)
      localStorage.setItem('auth_email', res.email)
    }),
    logout() {
      self.token = null
      self.email = null
      localStorage.removeItem('auth_token')
      localStorage.removeItem('auth_email')
    },
  }))
