import { types, type Instance, getRoot, flow } from 'mobx-state-tree'
import type { PivotResult } from '@/types/pivot'
import { executePivot, executeExternalPivot } from '@/services/api/pivotApi'
import { exportCSV, exportExcel } from '@/services/api/exportApi'

const PIVOT_TIMEOUT_MS = 30_000

export const ResultStore = types
  .model('ResultStore', {
    loading: types.optional(types.boolean, false),
    error: types.maybeNull(types.string),
    viewMode: types.optional(
      types.enumeration('ViewMode', ['table', 'bar', 'line']),
      'table'
    ),
    offset: types.optional(types.number, 0),
    limit: types.optional(types.number, 100),
  })
  .volatile(() => ({
    data: null as PivotResult | null,
    _queryToken: 0,
    _abortController: null as AbortController | null,
  }))
  .views(self => ({
    get totalRows(): number {
      return self.data?.totalRows ?? 0
    },
    get currentPage(): number {
      return Math.floor(self.offset / self.limit) + 1
    },
    get totalPages(): number {
      if (!self.data) return 0
      return Math.ceil(self.data.totalRows / self.limit)
    },
    get hasNextPage(): boolean {
      return self.data ? self.offset + self.limit < self.data.totalRows : false
    },
    get hasPrevPage(): boolean {
      return self.offset > 0
    },
  }))
  .actions(self => {
    const executeQueryInternal = flow(function* (keepOffset: boolean) {
      const root = getRoot(self) as any
      const config = root.pivotStore.configSnapshot
      const datasetId = root.datasetStore.currentDatasetId
      const conn = root.connectionStore

      const isExternal = conn?.isConnected && conn.connectionId && conn.selectedSchema && conn.selectedTable

      if (!root.pivotStore.isValid || (!datasetId && !isExternal)) {
        self.data = null
        return
      }

      if (!keepOffset) self.offset = 0

      self._abortController?.abort()
      const ac = new AbortController()
      self._abortController = ac

      const token = ++self._queryToken
      self.loading = true
      self.error = null

      try {
        let result: PivotResult
        const opts = { signal: ac.signal, timeoutMs: PIVOT_TIMEOUT_MS }

        if (isExternal) {
          result = yield executeExternalPivot(conn.connectionId, conn.selectedSchema, conn.selectedTable, config, self.offset, self.limit, opts)
        } else {
          result = yield executePivot(datasetId, config, self.offset, self.limit, opts)
        }

        if (token !== self._queryToken) return
        self.data = result
      } catch (e: any) {
        if (token !== self._queryToken) return
        self.error = e.message ?? 'Ошибка выполнения запроса'
      } finally {
        if (token === self._queryToken) {
          self.loading = false
          self._abortController = null
        }
      }
    })

    return {
      setData(data: PivotResult | null) {
        self.data = data
      },
      setLoading(loading: boolean) {
        self.loading = loading
      },
      setError(error: string | null) {
        self.error = error
      },
      setViewMode(mode: 'table' | 'bar' | 'line') {
        self.viewMode = mode
      },
      cancelQuery() {
        if (self._abortController) {
          self._abortController.abort()
          self._abortController = null
          self._queryToken++
          self.loading = false
          self.error = 'Запрос отменён'
        }
      },
      exportToCSV: flow(function* () {
        if (!self.data) return
        try { yield exportCSV(self.data) }
        catch (e: any) { self.error = e.message ?? 'Ошибка экспорта' }
      }),
      exportToExcel: flow(function* () {
        if (!self.data) return
        try { yield exportExcel(self.data) }
        catch (e: any) { self.error = e.message ?? 'Ошибка экспорта' }
      }),
      executeQuery() {
        return executeQueryInternal(false)
      },
      nextPage() {
        if (!self.hasNextPage) return
        self.offset += self.limit
        return executeQueryInternal(true)
      },
      prevPage() {
        if (!self.hasPrevPage) return
        self.offset = Math.max(0, self.offset - self.limit)
        return executeQueryInternal(true)
      },
      setPageSize(size: number) {
        self.limit = size
        self.offset = 0
        return executeQueryInternal(false)
      },
    }
  })

export type IResultStore = Instance<typeof ResultStore>
