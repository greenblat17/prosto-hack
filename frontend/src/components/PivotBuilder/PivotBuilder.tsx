import { observer } from 'mobx-react-lite'
import { DropZone } from './DropZone'
import { Rows3, Columns3, Calculator, Filter } from 'lucide-react'

export const PivotBuilder = observer(function PivotBuilder() {
  return (
    <div className="border-b border-[#e5e5e5] p-2">
      <div className="flex flex-col gap-1">
        <DropZone
          zone="rows"
          label="Строки"
          icon={<Rows3 className="h-3.5 w-3.5" />}
          color="blue"
        />
        <DropZone
          zone="columns"
          label="Колонки"
          icon={<Columns3 className="h-3.5 w-3.5" />}
          color="green"
        />
        <DropZone
          zone="values"
          label="Значения"
          icon={<Calculator className="h-3.5 w-3.5" />}
          color="purple"
        />
        <DropZone
          zone="filters"
          label="Фильтры"
          icon={<Filter className="h-3.5 w-3.5" />}
          color="orange"
        />
      </div>
    </div>
  )
})
