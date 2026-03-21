import { useState } from 'react'
import { observer } from 'mobx-react-lite'
import { useNavigate } from 'react-router-dom'
import { useStore } from '@/stores/RootStore'
import {
  BarChart3, Upload, Download, FileSpreadsheet,
  LayoutDashboard, FilePlus, Link2, LogOut, Trash2,
} from 'lucide-react'
import sberLogo from '@/assets/sber.png'

interface MenuItem {
  label: string
  icon: typeof LayoutDashboard
  onClick: () => void
  divider?: boolean
}

function MenuDropdown({ items, onClose }: { items: MenuItem[]; onClose: () => void }) {
  return (
    <>
      <div className="fixed inset-0 z-40" onClick={onClose} />
      <div className="absolute left-0 top-full z-50 bg-white rounded-lg border border-[#e2e8f0] shadow-xl py-1 min-w-[220px]">
        {items.map((item, i) => (
          <div key={i}>
            {item.divider && <div className="h-px bg-[#e2e8f0] my-1" />}
            <button
              onClick={() => { item.onClick(); onClose() }}
              className="w-full flex items-center gap-3 px-4 py-2 text-[14px] text-[#334155] hover:bg-[#f1f5f9] transition-colors"
            >
              <item.icon className="h-4 w-4 text-[#64748b]" />
              {item.label}
            </button>
          </div>
        ))}
      </div>
    </>
  )
}

export const AppHeader = observer(function AppHeader() {
  const { datasetStore, authStore, resultStore, pivotStore } = useStore()
  const navigate = useNavigate()
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const fileItems: MenuItem[] = [
    { label: 'Открыть новый файл', icon: FilePlus, onClick: () => navigate('/dashboards') },
    { label: 'Загрузить данные', icon: Upload, onClick: () => navigate('/dashboards') },
    { label: 'Экспорт в CSV', icon: Download, onClick: () => resultStore.exportToCSV(), divider: true },
    { label: 'Экспорт в Excel', icon: FileSpreadsheet, onClick: () => resultStore.exportToExcel() },
    {
      label: copied ? 'Скопировано!' : 'Скопировать ссылку',
      icon: Link2,
      onClick: () => {
        let url = window.location.href
        const id = datasetStore.currentDatasetId
        if (id && pivotStore.isValid) {
          try {
            const config = btoa(encodeURIComponent(JSON.stringify(pivotStore.configSnapshot)))
            url = `${window.location.origin}/dashboard/${id}?config=${config}`
          } catch {}
        }
        navigator.clipboard.writeText(url)
        setCopied(true)
        setTimeout(() => setCopied(false), 1500)
      },
    },
  ]

  const navItems: MenuItem[] = [
    { label: 'Все дашборды', icon: LayoutDashboard, onClick: () => navigate('/dashboards') },
    {
      label: 'Удалить датасет', icon: Trash2, divider: true,
      onClick: async () => {
        if (!confirm('Удалить текущий датасет?')) return
        try {
          await datasetStore.deleteDataset()
          navigate('/dashboards')
        } catch (e: any) {
          alert(e.message ?? 'Ошибка удаления датасета')
        }
      },
    },
  ]

  const menus = datasetStore.dataLoaded
    ? [
        { id: 'file', label: 'Файл', items: fileItems },
        { id: 'nav', label: 'Данные', items: navItems },
      ]
    : []

  return (
    <header className="h-11 flex items-center px-3 bg-white border-b border-[#e2e8f0] shrink-0 z-20">
      {/* Logo */}
      <button
        onClick={() => navigate('/dashboards')}
        className="flex items-center gap-2 px-1.5 py-1 rounded-lg hover:bg-[#f1f5f9] transition-colors shrink-0"
      >
        <div className="w-7 h-7 rounded-md bg-[#0f172a] flex items-center justify-center">
          <BarChart3 className="h-4 w-4 text-[#2dd4bf]" />
        </div>
        <img src={sberLogo} alt="" className="w-7 h-7" />
      </button>

      {/* Dataset name */}
      {datasetStore.dataLoaded && datasetStore.currentDatasetName && (
        <span className="ml-2 text-[15px] font-medium text-[#0f172a] truncate max-w-[250px]">
          {datasetStore.currentDatasetName}
        </span>
      )}
      {!datasetStore.dataLoaded && (
        <span className="ml-2 text-[15px] font-semibold text-[#0f172a]">Prosto Analytics</span>
      )}

      {/* Separator */}
      {menus.length > 0 && (
        <div className="w-px h-5 bg-[#e2e8f0] mx-3 shrink-0" />
      )}

      {/* Menu items */}
      <nav className="flex items-center gap-0.5">
        {menus.map(menu => (
          <div key={menu.id} className="relative">
            <button
              onClick={() => setOpenMenu(openMenu === menu.id ? null : menu.id)}
              className={`px-2 py-1 rounded text-[13px] transition-colors ${
                openMenu === menu.id
                  ? 'bg-[#e2e8f0] text-[#0f172a]'
                  : 'text-[#475569] hover:bg-[#f1f5f9]'
              }`}
            >
              {menu.label}
            </button>
            {openMenu === menu.id && (
              <MenuDropdown
                items={menu.items}
                onClose={() => setOpenMenu(null)}
              />
            )}
          </div>
        ))}
      </nav>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Right side */}
      <div className="flex items-center gap-2 shrink-0">
        {authStore.email && (
          <span className="text-[12px] text-[#94a3b8]">{authStore.email}</span>
        )}
        <button
          onClick={() => { authStore.logout(); navigate('/login') }}
          className="p-1.5 rounded-lg text-[#94a3b8] hover:text-[#334155] hover:bg-[#f1f5f9] transition-colors"
          title="Выйти"
        >
          <LogOut className="h-3.5 w-3.5" />
        </button>
      </div>
    </header>
  )
})
