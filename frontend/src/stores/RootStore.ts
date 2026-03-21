import { types, flow, type Instance } from 'mobx-state-tree'
import { createContext, useContext } from 'react'
import { DatasetStore } from './DatasetStore'
import { PivotStore } from './PivotStore'
import { ResultStore } from './ResultStore'
import { ChatStore } from './ChatStore'
import { AuthStore } from './AuthStore'
import { ConnectionStore } from './ConnectionStore'

export const RootStore = types
  .model('RootStore', {
    datasetStore: types.optional(DatasetStore, {}),
    pivotStore: types.optional(PivotStore, {}),
    resultStore: types.optional(ResultStore, {}),
    chatStore: types.optional(ChatStore, {}),
    authStore: types.optional(AuthStore, {}),
    connectionStore: types.optional(ConnectionStore, {}),
  })
  .actions(self => ({
    loadDemo: flow(function* () {
      yield self.datasetStore.loadDemoData()
      self.pivotStore.addField('rows', 'region', 'Регион')
      self.pivotStore.addField('rows', 'category', 'Категория')
      self.pivotStore.addField('values', 'revenue', 'Выручка', 'number')
      self.pivotStore.addField('values', 'profit', 'Прибыль', 'number')
      yield self.resultStore.executeQuery()
    }),
  }))

export type IRootStore = Instance<typeof RootStore>

const RootStoreContext = createContext<IRootStore | null>(null)

export const StoreProvider = RootStoreContext.Provider

export function useStore(): IRootStore {
  const store = useContext(RootStoreContext)
  if (!store) throw new Error('useStore must be used within StoreProvider')
  return store
}
