import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { observer } from 'mobx-react-lite'
import { useStore } from '@/stores/RootStore'
import { BarChart3, Loader2 } from 'lucide-react'

type Tab = 'login' | 'register'

export const AuthPage = observer(function AuthPage() {
  const { authStore } = useStore()
  const navigate = useNavigate()

  const [tab, setTab] = useState<Tab>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { setError('Введите корректный email'); return }
    if (password.length < 6) { setError('Пароль минимум 6 символов'); return }

    setLoading(true)
    try {
      if (tab === 'login') await authStore.login(email, password)
      else await authStore.register(email, password)
      navigate('/')
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Ошибка')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#f8fafc] flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm">

        {/* Logo */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-14 h-14 rounded-2xl bg-[#0f172a] flex items-center justify-center mb-4 shadow-lg">
            <BarChart3 className="h-7 w-7 text-[#2dd4bf]" />
          </div>
          <span className="text-xl font-bold text-[#0f172a]">Prosto Analytics</span>
        </div>

        {/* Card */}
        <div className="bg-white rounded-xl border border-[#e2e8f0] shadow-sm p-6">

          {/* Tabs */}
          <div className="flex gap-4 mb-6 border-b border-[#e2e8f0]">
            <button
              onClick={() => { setTab('login'); setError('') }}
              className={`pb-2.5 text-[15px] font-medium border-b-2 transition-colors ${
                tab === 'login'
                  ? 'border-[#0f172a] text-[#0f172a]'
                  : 'border-transparent text-[#94a3b8] hover:text-[#64748b]'
              }`}
            >
              Вход
            </button>
            <button
              onClick={() => { setTab('register'); setError('') }}
              className={`pb-2.5 text-[15px] font-medium border-b-2 transition-colors ${
                tab === 'register'
                  ? 'border-[#0f172a] text-[#0f172a]'
                  : 'border-transparent text-[#94a3b8] hover:text-[#64748b]'
              }`}
            >
              Регистрация
            </button>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-[14px] text-[#334155] mb-1">Email</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="name@example.com"
                autoComplete="email"
                className="w-full px-3 py-2.5 rounded-lg border border-[#e2e8f0] text-[15px] text-[#0f172a] placeholder:text-[#cbd5e1] focus:outline-none focus:border-[#0d9488] transition-colors"
              />
            </div>

            <div>
              <label className="block text-[14px] text-[#334155] mb-1">Пароль</label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder={tab === 'register' ? 'Минимум 6 символов' : '••••••••'}
                autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
                className="w-full px-3 py-2.5 rounded-lg border border-[#e2e8f0] text-[15px] text-[#0f172a] placeholder:text-[#cbd5e1] focus:outline-none focus:border-[#0d9488] transition-colors"
              />
            </div>

            {error && (
              <p className="text-[13px] text-red-600">{error}</p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 rounded-lg bg-[#0f172a] text-white text-[15px] font-medium hover:bg-[#1e293b] transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              {tab === 'login' ? 'Войти' : 'Зарегистрироваться'}
            </button>
          </form>
        </div>

        <p className="text-center text-[13px] text-[#94a3b8] mt-4">
          © 2026 Prosto Analytics
        </p>
      </div>
    </div>
  )
})
