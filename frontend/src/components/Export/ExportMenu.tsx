import { useState } from 'react'
import { useStore } from '@/stores/RootStore'
import { Download, FileSpreadsheet, Link2, ChevronDown } from 'lucide-react'

export function ExportMenu() {
  const { resultStore, pivotStore, datasetStore } = useStore()
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  const buildShareUrl = () => {
    const id = datasetStore.currentDatasetId
    if (!id || !pivotStore.isValid) return window.location.href
    try {
      const config = btoa(encodeURIComponent(JSON.stringify(pivotStore.configSnapshot)))
      return `${window.location.origin}/dashboard/${id}?config=${config}`
    } catch {
      return window.location.href
    }
  }

  const items = [
    {
      label: 'CSV', icon: Download, onClick: async () => {
        if (resultStore.data) {
          const { exportCSV } = await import('@/services/api/exportApi')
          await exportCSV(resultStore.data)
        }
        setOpen(false)
      }
    },
    {
      label: 'Excel', icon: FileSpreadsheet, onClick: async () => {
        if (resultStore.data) {
          const { exportExcel } = await import('@/services/api/exportApi')
          await exportExcel(resultStore.data)
        }
        setOpen(false)
      }
    },
    {
      label: copied ? 'Скопировано!' : 'Share link',
      icon: Link2,
      onClick: () => {
        navigator.clipboard.writeText(buildShareUrl())
        setCopied(true)
        setTimeout(() => { setCopied(false); setOpen(false) }, 1200)
      },
    },
  ]

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[14px] text-[#475569] hover:bg-[#f8fafc] border border-[#e2e8f0] hover:border-[#94a3b8] transition-all"
      >
        <Download className="h-4 w-4" />
        Export
        <ChevronDown className="h-3.5 w-3.5 ml-0.5 opacity-50" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-full mt-1 z-40 bg-white rounded-xl border border-[#e2e8f0] shadow-xl py-1.5 min-w-[180px] animate-fade-in-up">
            {items.map(({ label, icon: Icon, onClick }) => (
              <button
                key={label}
                onClick={onClick}
                className="w-full flex items-center gap-2.5 px-4 py-2.5 text-[15px] text-[#334155] hover:bg-[#f8fafc] transition-colors"
              >
                <Icon className="h-[18px] w-[18px] text-[#64748b]" />
                {label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
