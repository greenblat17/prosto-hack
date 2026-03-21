import { type ReactNode, useState, useCallback, useRef } from 'react'
import { cn } from '@/lib/utils'

interface AppLayoutProps {
  left: ReactNode | null
  center: ReactNode
  right: ReactNode | null
}

function ResizeHandle({ onDrag, side }: { onDrag: (delta: number) => void; side: 'left' | 'right' }) {
  const dragging = useRef(false)
  const lastX = useRef(0)

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    dragging.current = true
    lastX.current = e.clientX
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'

    const onMouseMove = (e: MouseEvent) => {
      if (!dragging.current) return
      const delta = e.clientX - lastX.current
      lastX.current = e.clientX
      onDrag(delta)
    }

    const onMouseUp = () => {
      dragging.current = false
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
    }

    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }, [onDrag])

  return (
    <div
      onMouseDown={onMouseDown}
      className={cn(
        'w-1 shrink-0 cursor-col-resize group relative z-20 hover:bg-[#0d9488]/20 active:bg-[#0d9488]/30 transition-colors',
        side === 'left' ? 'border-r border-[#e2e8f0]' : 'border-l border-[#e2e8f0]'
      )}
    >
      <div className="absolute inset-y-0 -left-1 -right-1" />
    </div>
  )
}

export function AppLayout({ left, center, right }: AppLayoutProps) {
  const [leftWidth, setLeftWidth] = useState(250)
  const [rightWidth, setRightWidth] = useState(340)

  const handleLeftDrag = useCallback((delta: number) => {
    setLeftWidth(w => Math.max(180, Math.min(400, w + delta)))
  }, [])

  const handleRightDrag = useCallback((delta: number) => {
    setRightWidth(w => Math.max(280, Math.min(500, w - delta)))
  }, [])

  return (
    <div className="h-full flex bg-[#f8fafc] text-[#334155]">
      {left && (
        <>
          <aside
            className="flex flex-col shrink-0 min-h-0 bg-[#fafafa] shadow-[2px_0_8px_rgba(0,0,0,0.04)] z-10"
            style={{ width: leftWidth }}
          >
            {left}
          </aside>
          <ResizeHandle onDrag={handleLeftDrag} side="left" />
        </>
      )}
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden bg-white">
        {center}
      </main>
      {right && (
        <>
          <ResizeHandle onDrag={handleRightDrag} side="right" />
          <aside
            className="flex flex-col shrink-0 min-h-0 bg-[#fafafa] shadow-[-2px_0_8px_rgba(0,0,0,0.04)] z-10"
            style={{ width: rightWidth }}
          >
            {right}
          </aside>
        </>
      )}
    </div>
  )
}
