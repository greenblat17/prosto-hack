import { useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { observer } from 'mobx-react-lite'
import { useStore } from '@/stores/RootStore'
import { Upload, FileSpreadsheet, BarChart3, Play, ArrowRight, Loader2, LayoutDashboard } from 'lucide-react'

export const WelcomeScreen = observer(function WelcomeScreen() {
  const store = useStore()
  const navigate = useNavigate()
  const [dragOver, setDragOver] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const handleFile = (file: File) => {
    setSelectedFile(file)
    setError(null)
  }

  const handleDemo = async () => {
    setUploading(true)
    setError(null)
    try {
      await store.loadDemo()
      navigate(`/dashboard/${store.datasetStore.currentDatasetId}`)
    } catch (e: any) {
      setError(e.message || 'Ошибка загрузки демо-данных')
    } finally {
      setUploading(false)
    }
  }

  const handleContinue = async () => {
    if (!selectedFile) return
    setUploading(true)
    setError(null)
    try {
      await store.datasetStore.uploadDataset(selectedFile)
      navigate(`/dashboard/${store.datasetStore.currentDatasetId}`)
    } catch (e: any) {
      setError(e.message || 'Ошибка загрузки файла')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="flex-1 flex items-center justify-center bg-[#f8fafc]">
      <div className="w-full max-w-[520px] px-6">
        <div className="text-center mb-8">
          <div className="mx-auto w-16 h-16 rounded-2xl bg-[#0f172a] flex items-center justify-center shadow-lg mb-5">
            <BarChart3 className="h-8 w-8 text-[#2dd4bf]" />
          </div>
          <h1 className="text-[24px] font-bold text-[#0f172a] tracking-tight">
            Prosto Analytics
          </h1>
          <p className="text-[16px] text-[#64748b] mt-2">
            Загрузите данные, чтобы начать анализ
          </p>
        </div>

        {error && (
          <div className="mb-4 px-4 py-3 rounded-xl bg-red-50 border border-red-200 text-[14px] text-red-700">
            {error}
          </div>
        )}

        <div
          onDragOver={e => { e.preventDefault(); setDragOver(true) }}
          onDragLeave={() => setDragOver(false)}
          onDrop={e => {
            e.preventDefault()
            setDragOver(false)
            const file = e.dataTransfer.files[0]
            if (file) handleFile(file)
          }}
          onClick={() => !selectedFile && !uploading && fileRef.current?.click()}
          className={`border-2 border-dashed rounded-2xl p-10 text-center transition-all ${
            dragOver
              ? 'border-[#0d9488] bg-[#f0fdfa] shadow-sm cursor-pointer'
              : selectedFile
              ? 'border-[#0d9488] bg-[#f0fdfa]'
              : 'border-[#cbd5e1] hover:border-[#94a3b8] hover:bg-white hover:shadow-sm cursor-pointer'
          }`}
        >
          <input
            ref={fileRef}
            type="file"
            accept=".csv,.xlsx,.xls"
            className="hidden"
            onChange={e => {
              const file = e.target.files?.[0]
              if (file) handleFile(file)
            }}
          />
          {selectedFile ? (
            <div className="space-y-3">
              <FileSpreadsheet className="h-10 w-10 text-[#0d9488] mx-auto" />
              <p className="text-[16px] font-medium text-[#0f172a]">{selectedFile.name}</p>
              <button
                onClick={e => {
                  e.stopPropagation()
                  handleContinue()
                }}
                disabled={uploading}
                className="mx-auto flex items-center gap-2 px-5 py-2 rounded-lg bg-[#0d9488] text-white text-[14px] font-medium hover:bg-[#0f766e] transition-colors shadow-sm disabled:opacity-50"
              >
                {uploading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <ArrowRight className="h-4 w-4" />
                )}
                {uploading ? 'Загружаем...' : 'Продолжить'}
              </button>
            </div>
          ) : (
            <div className="space-y-2">
              <Upload className="h-10 w-10 text-[#94a3b8] mx-auto" />
              <p className="text-[16px] text-[#334155]">
                Перетащите файл сюда или{' '}
                <span className="text-[#0d9488] font-semibold">выберите</span>
              </p>
              <p className="text-[13px] text-[#94a3b8]">CSV, XLSX — до 50 MB</p>
            </div>
          )}
        </div>

        <div className="mt-6 flex items-center gap-3">
          <div className="flex-1 h-px bg-[#e2e8f0]" />
          <span className="text-[13px] text-[#94a3b8] uppercase tracking-wider">или</span>
          <div className="flex-1 h-px bg-[#e2e8f0]" />
        </div>

        <button
          onClick={handleDemo}
          disabled={uploading}
          className="mt-6 w-full flex items-center justify-center gap-2 px-4 py-3 rounded-xl bg-[#0f172a] text-white text-[15px] font-medium hover:bg-[#1e293b] transition-colors shadow-sm hover:shadow-md disabled:opacity-50"
        >
          {uploading ? (
            <Loader2 className="h-4 w-4 animate-spin text-[#2dd4bf]" />
          ) : (
            <Play className="h-4 w-4 text-[#2dd4bf]" />
          )}
          Попробовать с демо-данными
        </button>

        <p className="text-center text-[12px] text-[#94a3b8] mt-4">
          Демо-набор: 50 000 строк — продажи, регионы, категории
        </p>

        <button
          onClick={() => navigate('/dashboards')}
          className="mt-6 w-full flex items-center justify-center gap-2 px-4 py-3 rounded-xl border border-[#e2e8f0] text-[#475569] text-[15px] font-medium hover:bg-white hover:border-[#94a3b8] transition-colors"
        >
          <LayoutDashboard className="h-4 w-4 text-[#64748b]" />
          Мои дашборды
        </button>
      </div>
    </div>
  )
})
