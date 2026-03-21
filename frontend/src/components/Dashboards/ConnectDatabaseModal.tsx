import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { observer } from 'mobx-react-lite'
import { useStore } from '@/stores/RootStore'
import { X, Loader2, CheckCircle2, XCircle } from 'lucide-react'
import type { ConnectionConfig } from '@/services/api/connectionApi'

interface Props {
  open: boolean
  onClose: () => void
}

export const ConnectDatabaseModal = observer(function ConnectDatabaseModal({ open, onClose }: Props) {
  const { connectionStore } = useStore()
  const navigate = useNavigate()

  const [host, setHost] = useState('localhost')
  const [port, setPort] = useState('5432')
  const [database, setDatabase] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')

  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null)

  if (!open) return null

  const config: ConnectionConfig = {
    host,
    port: parseInt(port, 10) || 5432,
    database,
    username,
    password,
    name: name || undefined,
  }

  const canSubmit = host && port && database && username && password

  const handleTest = async () => {
    setTesting(true)
    setTestResult(null)
    try {
      const result = await connectionStore.testConnection(config)
      setTestResult(result)
    } finally {
      setTesting(false)
    }
  }

  const handleConnect = async () => {
    try {
      await connectionStore.connect(config)
      onClose()
      navigate(`/dashboard/ext/${connectionStore.connectionId}`)
    } catch {
      // error is set in store
    }
  }

  return (
    <>
      <div className="fixed inset-0 z-50 bg-black/40" onClick={onClose} />
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
        <div
          className="bg-white rounded-2xl shadow-2xl w-full max-w-[460px] pointer-events-auto"
          onClick={e => e.stopPropagation()}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-[#f1f5f9]">
            <h2 className="text-[17px] font-bold text-[#0f172a]">Подключение к PostgreSQL</h2>
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-[#94a3b8] hover:text-[#334155] hover:bg-[#f1f5f9] transition-all"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          {/* Form */}
          <div className="px-6 py-5 space-y-4">
            <div>
              <label className="block text-[13px] font-medium text-[#475569] mb-1.5">Название (необязательно)</label>
              <input
                type="text"
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder="Мой сервер"
                className="w-full h-9 px-3 rounded-lg border border-[#e2e8f0] text-[14px] text-[#0f172a] placeholder:text-[#94a3b8] focus:outline-none focus:border-[#0d9488] focus:ring-2 focus:ring-[#0d9488]/20 transition-all"
              />
            </div>
            <div className="grid grid-cols-[1fr_100px] gap-3">
              <div>
                <label className="block text-[13px] font-medium text-[#475569] mb-1.5">Хост</label>
                <input
                  type="text"
                  value={host}
                  onChange={e => setHost(e.target.value)}
                  placeholder="localhost"
                  className="w-full h-9 px-3 rounded-lg border border-[#e2e8f0] text-[14px] text-[#0f172a] placeholder:text-[#94a3b8] focus:outline-none focus:border-[#0d9488] focus:ring-2 focus:ring-[#0d9488]/20 transition-all"
                />
              </div>
              <div>
                <label className="block text-[13px] font-medium text-[#475569] mb-1.5">Порт</label>
                <input
                  type="text"
                  value={port}
                  onChange={e => setPort(e.target.value)}
                  placeholder="5432"
                  className="w-full h-9 px-3 rounded-lg border border-[#e2e8f0] text-[14px] text-[#0f172a] placeholder:text-[#94a3b8] focus:outline-none focus:border-[#0d9488] focus:ring-2 focus:ring-[#0d9488]/20 transition-all"
                />
              </div>
            </div>
            <div>
              <label className="block text-[13px] font-medium text-[#475569] mb-1.5">База данных</label>
              <input
                type="text"
                value={database}
                onChange={e => setDatabase(e.target.value)}
                placeholder="mydb"
                className="w-full h-9 px-3 rounded-lg border border-[#e2e8f0] text-[14px] text-[#0f172a] placeholder:text-[#94a3b8] focus:outline-none focus:border-[#0d9488] focus:ring-2 focus:ring-[#0d9488]/20 transition-all"
              />
            </div>
            <div>
              <label className="block text-[13px] font-medium text-[#475569] mb-1.5">Пользователь</label>
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="postgres"
                className="w-full h-9 px-3 rounded-lg border border-[#e2e8f0] text-[14px] text-[#0f172a] placeholder:text-[#94a3b8] focus:outline-none focus:border-[#0d9488] focus:ring-2 focus:ring-[#0d9488]/20 transition-all"
              />
            </div>
            <div>
              <label className="block text-[13px] font-medium text-[#475569] mb-1.5">Пароль</label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="password"
                className="w-full h-9 px-3 rounded-lg border border-[#e2e8f0] text-[14px] text-[#0f172a] placeholder:text-[#94a3b8] focus:outline-none focus:border-[#0d9488] focus:ring-2 focus:ring-[#0d9488]/20 transition-all"
              />
            </div>

            {/* Test result */}
            {testResult && (
              <div className={`flex items-center gap-2 px-3 py-2 rounded-lg text-[13px] ${
                testResult.success
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                  : 'bg-red-50 text-red-700 border border-red-200'
              }`}>
                {testResult.success ? <CheckCircle2 className="h-4 w-4 shrink-0" /> : <XCircle className="h-4 w-4 shrink-0" />}
                {testResult.message}
              </div>
            )}

            {/* Store error */}
            {connectionStore.error && (
              <div className="flex items-center gap-2 px-3 py-2 rounded-lg text-[13px] bg-red-50 text-red-700 border border-red-200">
                <XCircle className="h-4 w-4 shrink-0" />
                {connectionStore.error}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-[#f1f5f9]">
            <button
              onClick={handleTest}
              disabled={!canSubmit || testing}
              className="flex items-center gap-2 px-4 py-2 rounded-lg text-[14px] font-medium text-[#0d9488] border border-[#ccfbf1] hover:bg-[#f0fdfa] hover:border-[#5eead4] transition-all disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {testing && <Loader2 className="h-4 w-4 animate-spin" />}
              Проверить соединение
            </button>
            <button
              onClick={handleConnect}
              disabled={!canSubmit || connectionStore.isConnecting}
              className="flex items-center gap-2 px-4 py-2 rounded-lg text-[14px] font-medium text-white bg-[#0f172a] hover:bg-[#1e293b] transition-all disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {connectionStore.isConnecting && <Loader2 className="h-4 w-4 animate-spin" />}
              Подключиться
            </button>
          </div>
        </div>
      </div>
    </>
  )
})
