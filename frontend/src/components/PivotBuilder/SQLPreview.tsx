import { useState } from 'react'
import { observer } from 'mobx-react-lite'
import { useStore } from '@/stores/RootStore'
import { Code, ChevronDown, ChevronRight, Copy, Check } from 'lucide-react'

export const SQLPreview = observer(function SQLPreview() {
  const { pivotStore, resultStore } = useStore()
  const [open, setOpen] = useState(true)
  const [copied, setCopied] = useState(false)

  if (!pivotStore.isValid) return null

  const sql = resultStore.sql

  const handleCopy = () => {
    if (!sql) return
    navigator.clipboard.writeText(sql)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div className="border-b border-[#e2e8f0]">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2 px-4 py-2.5 text-[14px] text-[#64748b] hover:text-[#334155] transition-colors w-full"
      >
        <Code className="h-4 w-4" />
        <span>SQL Preview</span>
        {open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
      </button>
      {open && sql && (
        <div className="relative mx-4 mb-3">
          <pre className="text-[14px] font-mono bg-[#f8fafc] border border-[#e2e8f0] rounded-lg p-3.5 overflow-x-auto text-[#334155] leading-relaxed">
            {sql}
          </pre>
          <button
            onClick={handleCopy}
            className="absolute top-2.5 right-2.5 p-1.5 rounded-md hover:bg-[#e2e8f0] text-[#94a3b8] hover:text-[#475569] transition-colors"
          >
            {copied ? <Check className="h-4 w-4 text-[#0d9488]" /> : <Copy className="h-4 w-4" />}
          </button>
        </div>
      )}
    </div>
  )
})
