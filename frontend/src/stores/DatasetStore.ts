import { types, flow } from 'mobx-state-tree'
import { fetchDatasets, fetchFields, uploadFile, deleteDataset as apiDeleteDataset, type DatasetInfo } from '@/services/api/datasetApi'

const DEMO_DATASET_ID = '00000000-0000-0000-0000-000000000001'

const DatasetFieldModel = types.model('DatasetField', {
  id: types.identifier,
  name: types.string,
  type: types.enumeration(['string', 'number', 'date', 'boolean']),
  category: types.string,
})

export const DatasetStore = types
  .model('DatasetStore', {
    fields: types.array(DatasetFieldModel),
    searchQuery: types.optional(types.string, ''),
    dataLoaded: types.optional(types.boolean, false),
    currentDatasetId: types.maybeNull(types.string),
    currentDatasetName: types.optional(types.string, ''),
  })
  .volatile(() => ({
    datasets: [] as DatasetInfo[],
    datasetsLoading: false,
  }))
  .views(self => ({
    get filteredFields() {
      const q = self.searchQuery.toLowerCase()
      if (!q) return self.fields
      return self.fields.filter(
        f => f.name.toLowerCase().includes(q) || f.category.toLowerCase().includes(q)
      )
    },
    get categories() {
      const cats = new Set(self.fields.map(f => f.category))
      return Array.from(cats)
    },
    get fieldsByCategory() {
      const q = self.searchQuery.toLowerCase()
      const map: Record<string, typeof self.fields> = {}
      for (const f of self.fields) {
        if (q && !f.name.toLowerCase().includes(q) && !f.category.toLowerCase().includes(q)) continue
        if (!map[f.category]) map[f.category] = [] as any
        map[f.category].push(f)
      }
      return map
    },
    getFieldById(id: string) {
      return self.fields.find(f => f.id === id)
    },
  }))
  .actions(self => ({
    loadDatasets: flow(function* () {
      self.datasetsLoading = true
      try {
        self.datasets = yield fetchDatasets()
      } finally {
        self.datasetsLoading = false
      }
    }),
    openDataset: flow(function* (id: string) {
      const fields: Awaited<ReturnType<typeof fetchFields>> = yield fetchFields(id)
      self.fields.replace(fields as any)
      self.currentDatasetId = id
      // Try to get name from cached datasets list
      let ds = self.datasets.find(d => d.id === id)
      if (!ds) {
        // Load datasets list to find name
        self.datasets = yield fetchDatasets()
        ds = self.datasets.find(d => d.id === id)
      }
      if (ds) self.currentDatasetName = ds.name
      self.dataLoaded = true
    }),
    loadDemoData: flow(function* () {
      const fields: Awaited<ReturnType<typeof fetchFields>> = yield fetchFields(DEMO_DATASET_ID)
      self.fields.replace(fields as any)
      self.currentDatasetId = DEMO_DATASET_ID
      self.currentDatasetName = 'Демо: Продажи'
      self.dataLoaded = true
    }),
    uploadDataset: flow(function* (file: File) {
      const info: Awaited<ReturnType<typeof uploadFile>> = yield uploadFile(file)
      const fields: Awaited<ReturnType<typeof fetchFields>> = yield fetchFields(info.id)
      self.fields.replace(fields as any)
      self.currentDatasetId = info.id
      self.currentDatasetName = info.name
      self.dataLoaded = true
    }),
    deleteDataset: flow(function* () {
      if (!self.currentDatasetId) return
      yield apiDeleteDataset(self.currentDatasetId)
      self.fields.clear()
      self.currentDatasetId = null
      self.dataLoaded = false
    }),
    deleteDatasetById: flow(function* (id: string) {
      yield apiDeleteDataset(id)
      if (self.currentDatasetId === id) {
        self.fields.clear()
        self.currentDatasetId = null
        self.dataLoaded = false
      }
      self.datasets = self.datasets.filter(d => d.id !== id)
    }),
    resetData() {
      self.dataLoaded = false
      self.currentDatasetId = null
      self.currentDatasetName = ''
    },
    setSearchQuery(q: string) {
      self.searchQuery = q
    },
    setExternalFields(fields: any[], name: string) {
      self.fields.replace(fields)
      self.currentDatasetId = null
      self.currentDatasetName = name
      self.dataLoaded = true
    },
  }))
