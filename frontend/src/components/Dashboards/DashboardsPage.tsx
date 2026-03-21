import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { observer } from 'mobx-react-lite'
import { useStore } from '@/stores/RootStore'
import { BarChart3, Upload, Play, Table2, Trash2, MoreVertical, Loader2, Database } from 'lucide-react'
import { ConnectDatabaseModal } from './ConnectDatabaseModal'
import type { DatasetInfo } from '@/services/api/datasetApi'

type DateGroup = { label: string; datasets: DatasetInfo[] }

function groupByDate(datasets: DatasetInfo[]): DateGroup[] {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400000)
  const weekAgo = new Date(today.getTime() - 7 * 86400000)
  const monthAgo = new Date(today.getTime() - 30 * 86400000)

  const buckets: Record<string, DatasetInfo[]> = {
    'Сегодня': [],
    'Вчера': [],
    'За неделю': [],
    'За месяц': [],
    'Ранее': [],
  }

  for (const ds of datasets) {
    const d = new Date(ds.createdAt)
    if (d >= today) buckets['Сегодня'].push(ds)
    else if (d >= yesterday) buckets['Вчера'].push(ds)
    else if (d >= weekAgo) buckets['За неделю'].push(ds)
    else if (d >= monthAgo) buckets['За месяц'].push(ds)
    else buckets['Ранее'].push(ds)
  }

  return Object.entries(buckets)
    .filter(([, items]) => items.length > 0)
    .map(([label, items]) => ({ label, datasets: items }))
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' })
}

function formatCount(n: number): string {
  return n.toLocaleString('ru-RU')
}

export const DashboardsPage = observer(function DashboardsPage() {
  const store = useStore()
  const { datasetStore } = store
  const navigate = useNavigate()
  const fileRef = useRef<HTMLInputElement>(null)
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dbModalOpen, setDbModalOpen] = useState(false)

  useEffect(() => {
    datasetStore.loadDatasets()
  }, [datasetStore])

  const handleUpload = async (file: File) => {
    setUploading(true)
    setError(null)
    try {
      await datasetStore.uploadDataset(file)
      navigate(`/dashboard/${datasetStore.currentDatasetId}`)
    } catch (e: any) {
      setError(e.message || 'Ошибка загрузки файла')
    } finally {
      setUploading(false)
    }
  }

  const handleDemo = async () => {
    setUploading(true)
    setError(null)
    try {
      await store.loadDemo()
      navigate(`/dashboard/${datasetStore.currentDatasetId}`)
    } catch (e: any) {
      setError(e.message || 'Ошибка загрузки демо-данных')
    } finally {
      setUploading(false)
    }
  }

  const handleOpen = async (ds: DatasetInfo) => {
    setError(null)
    try {
      await datasetStore.openDataset(ds.id)
      navigate(`/dashboard/${ds.id}`)
    } catch (e: any) {
      setError(e.message ?? 'Ошибка открытия датасета')
    }
  }

  const handleDelete = async (id: string) => {
    setOpenMenu(null)
    if (!confirm('Удалить датасет?')) return
    setDeleting(id)
    try {
      await datasetStore.deleteDatasetById(id)
    } catch (e: any) {
      setError(e.message ?? 'Ошибка удаления')
    } finally {
      setDeleting(null)
    }
  }

  const groups = groupByDate(
    [...datasetStore.datasets].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  )

  return (
    <div className="flex-1 bg-[#f8fafc] overflow-auto">
      <div className="max-w-[900px] mx-auto px-6 py-10">
        {/* Header */}
        <div className="text-center mb-8">
          <h1 className="text-[22px] font-bold text-[#0f172a] tracking-tight">Мои дашборды</h1>
          <p className="text-[14px] text-[#64748b] mt-1">Загрузите данные или откройте существующий датасет</p>
        </div>

        {error && (
          <div className="mb-6 px-4 py-3 rounded-xl bg-red-50 border border-red-200 text-[14px] text-red-700">
            {error}
          </div>
        )}

        {/* Upload area */}
        <div className="mb-8">
          <h2 className="text-[13px] font-semibold text-[#64748b] uppercase tracking-wider mb-4">
            Создать дашборд
          </h2>
          <div className="flex gap-4 items-start">
            <input
              ref={fileRef}
              type="file"
              accept=".csv,.xlsx,.xls"
              className="hidden"
              onChange={e => {
                const file = e.target.files?.[0]
                if (file) handleUpload(file)
                e.target.value = ''
              }}
            />
            <div
              onDragOver={e => { e.preventDefault(); setDragOver(true) }}
              onDragLeave={() => setDragOver(false)}
              onDrop={e => {
                e.preventDefault()
                setDragOver(false)
                const file = e.dataTransfer.files[0]
                if (file) handleUpload(file)
              }}
              onClick={() => !uploading && fileRef.current?.click()}
              className={`w-[180px] h-[120px] rounded-xl border-2 border-dashed flex flex-col items-center justify-center gap-2 transition-all cursor-pointer ${
                dragOver
                  ? 'border-[#0d9488] bg-[#f0fdfa]'
                  : 'border-[#cbd5e1] hover:border-[#0d9488] hover:bg-[#f0fdfa]'
              }`}
            >
              {uploading ? (
                <Loader2 className="h-6 w-6 animate-spin text-[#0d9488]" />
              ) : (
                <>
                  <Upload className="h-7 w-7 text-[#94a3b8]" />
                  <span className="text-[13px] text-[#64748b] font-medium">Загрузить файл</span>
                  <span className="text-[11px] text-[#94a3b8]">CSV, XLSX</span>
                </>
              )}
            </div>
            <button
              onClick={handleDemo}
              disabled={uploading}
              className="w-[180px] h-[120px] rounded-xl border-2 border-dashed border-[#cbd5e1] hover:border-[#0f172a] hover:bg-[#f8fafc] flex flex-col items-center justify-center gap-2 transition-all group disabled:opacity-50"
            >
              <Play className="h-7 w-7 text-[#94a3b8] group-hover:text-[#0f172a] transition-colors" />
              <span className="text-[13px] text-[#64748b] group-hover:text-[#0f172a] font-medium transition-colors">Демо-данные</span>
              <span className="text-[11px] text-[#94a3b8]">50K строк</span>
            </button>
            <button
              onClick={() => setDbModalOpen(true)}
              className="w-[180px] h-[120px] rounded-xl border-2 border-dashed border-[#cbd5e1] hover:border-[#0d9488] hover:bg-[#f0fdfa] flex flex-col items-center justify-center gap-2 transition-all group"
            >
              <Database className="h-7 w-7 text-[#94a3b8] group-hover:text-[#0d9488] transition-colors" />
              <span className="text-[13px] text-[#64748b] group-hover:text-[#0d9488] font-medium transition-colors">Подключиться к БД</span>
              <span className="text-[11px] text-[#94a3b8]">PostgreSQL</span>
            </button>
          </div>
        </div>

        {/* Loading */}
        {datasetStore.datasetsLoading && datasetStore.datasets.length === 0 && (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-[#94a3b8]" />
          </div>
        )}

        {/* Empty state */}
        {!datasetStore.datasetsLoading && datasetStore.datasets.length === 0 && (
          <div className="text-center py-16">
            <BarChart3 className="h-12 w-12 text-[#cbd5e1] mx-auto mb-4" />
            <p className="text-[16px] text-[#64748b]">Нет загруженных датасетов</p>
            <p className="text-[13px] text-[#94a3b8] mt-1">Загрузите файл или попробуйте демо-данные</p>
          </div>
        )}

        {/* Dataset list */}
        {groups.map(group => (
          <div key={group.label} className="mb-6">
            <h3 className="text-[13px] font-semibold text-[#94a3b8] uppercase tracking-wider mb-2 px-1">
              {group.label}
            </h3>
            <div className="bg-white rounded-xl border border-[#e2e8f0] divide-y divide-[#f1f5f9]">
              {group.datasets.map(ds => (
                <div
                  key={ds.id}
                  className={`flex items-center gap-4 px-4 py-3 hover:bg-[#f8fafc] transition-colors cursor-pointer group ${
                    deleting === ds.id ? 'opacity-40 pointer-events-none' : ''
                  }`}
                  onClick={() => handleOpen(ds)}
                >
                  <div className="w-8 h-8 rounded-lg bg-[#0f172a] flex items-center justify-center shrink-0">
                    <Table2 className="h-4 w-4 text-[#2dd4bf]" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-[15px] font-medium text-[#0f172a] truncate">{ds.name}</p>
                  </div>
                  <span className="text-[13px] text-[#94a3b8] shrink-0 hidden sm:block">
                    {formatCount(ds.rowCount)} строк, {ds.columnCount} полей
                  </span>
                  <span className="text-[13px] text-[#94a3b8] shrink-0 w-[120px] text-right hidden sm:block">
                    {formatDate(ds.createdAt)}
                  </span>
                  <div className="relative shrink-0">
                    <button
                      onClick={e => { e.stopPropagation(); setOpenMenu(openMenu === ds.id ? null : ds.id) }}
                      className="p-1.5 rounded-lg text-[#94a3b8] hover:text-[#334155] hover:bg-[#f1f5f9] opacity-0 group-hover:opacity-100 transition-all"
                    >
                      <MoreVertical className="h-4 w-4" />
                    </button>
                    {openMenu === ds.id && (
                      <>
                        <div className="fixed inset-0 z-40" onMouseDown={e => { e.stopPropagation(); setOpenMenu(null) }} />
                        <div className="absolute right-0 top-full mt-1 z-50 bg-white rounded-xl border border-[#e2e8f0] shadow-xl py-1.5 min-w-[180px]">
                          <button
                            onMouseDown={e => e.stopPropagation()}
                            onClick={e => { e.stopPropagation(); handleDelete(ds.id) }}
                            className="w-full flex items-center gap-2.5 px-4 py-2.5 text-[14px] text-[#dc2626] hover:bg-red-50 transition-colors"
                          >
                            <Trash2 className="h-4 w-4" />
                            Удалить
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
      <ConnectDatabaseModal open={dbModalOpen} onClose={() => setDbModalOpen(false)} />
    </div>
  )
})
