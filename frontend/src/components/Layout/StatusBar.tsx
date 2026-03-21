import { observer } from 'mobx-react-lite'
import { useStore } from '@/stores/RootStore'

export const StatusBar = observer(function StatusBar() {
  const { datasetStore, resultStore, pivotStore } = useStore()

  const fieldCount = datasetStore.fields.length
  const rowCount = resultStore.data?.rows.length ?? 0
  const ds = datasetStore.datasets.find((d: { id: string }) => d.id === datasetStore.currentDatasetId)
  const totalRecords = ds?.rowCount ?? resultStore.totalRows
  const name = datasetStore.currentDatasetName

  const valuesCount = pivotStore.values.length
  const filtersCount = pivotStore.filters.length

  return (
    <footer className="h-8 flex items-center px-5 bg-[#f8fafc] border-t border-[#e2e8f0] text-[12px] text-[#94a3b8] gap-3 shrink-0">
      {name && (
        <>
          <span className="text-[#64748b] font-medium truncate max-w-[200px]">{name}</span>
          <span className="text-[#cbd5e1]">·</span>
        </>
      )}
      <span>{fieldCount} полей</span>
      <span className="text-[#cbd5e1]">·</span>
      <span>{totalRecords.toLocaleString('ru-RU')} записей</span>
      {pivotStore.isValid && (
        <>
          <span className="text-[#cbd5e1]">|</span>
          {valuesCount > 0 && <span>{valuesCount} метрик</span>}
          {filtersCount > 0 && (
            <>
              <span className="text-[#cbd5e1]">·</span>
              <span>{filtersCount} фильтров</span>
            </>
          )}
          <span className="text-[#cbd5e1]">·</span>
          <span>{rowCount} строк</span>
        </>
      )}
    </footer>
  )
})
