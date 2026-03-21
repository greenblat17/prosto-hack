import { types, type Instance } from 'mobx-state-tree'
import type { AggregationType, PivotConfig, PivotZone, FilterOperator } from '@/types/pivot'

const PivotFieldModel = types.model('PivotField', {
  fieldId: types.string,
  name: types.string,
})

const PivotValueFieldModel = types.model('PivotValueField', {
  fieldId: types.string,
  name: types.string,
  aggregation: types.optional(
    types.enumeration<AggregationType>([
      'original', 'count', 'count_distinct', 'list_distinct',
      'sum', 'int_sum', 'avg', 'median',
      'variance', 'stddev', 'min', 'max',
      'first', 'last', 'running_sum',
      'sum_pct_total', 'sum_pct_row', 'sum_pct_col',
      'count_pct_total', 'count_pct_row', 'count_pct_col',
    ]),
    'original'
  ),
})

const PivotFilterFieldModel = types.model('PivotFilterField', {
  fieldId: types.string,
  name: types.string,
  operator: types.optional(
    types.enumeration<FilterOperator>(['eq', 'neq', 'gt', 'lt', 'in']),
    'eq'
  ),
  filterValue: types.optional(types.union(types.string, types.array(types.string)), ''),
})

export const PivotStore = types
  .model('PivotStore', {
    rows: types.array(PivotFieldModel),
    columns: types.array(PivotFieldModel),
    values: types.array(PivotValueFieldModel),
    filters: types.array(PivotFilterFieldModel),
  })
  .views(self => ({
    get configSnapshot(): PivotConfig {
      return {
        rows: self.rows.map(f => ({ fieldId: f.fieldId, name: f.name })),
        columns: self.columns.map(f => ({ fieldId: f.fieldId, name: f.name })),
        values: self.values.map(f => ({ fieldId: f.fieldId, name: f.name, aggregation: f.aggregation })),
        filters: self.filters
          .filter(f => {
            const v = f.filterValue
            if (Array.isArray(v)) return v.length > 0
            return typeof v === 'string' && v.length > 0
          })
          .map(f => ({
            fieldId: f.fieldId,
            name: f.name,
            operator: f.operator,
            filterValue: f.filterValue as string | string[],
          })),
      }
    },
    get isValid() {
      return self.values.length > 0 && (self.rows.length > 0 || self.columns.length > 0)
    },
    isFieldUsed(fieldId: string): PivotZone | null {
      if (self.rows.some(f => f.fieldId === fieldId)) return 'rows'
      if (self.columns.some(f => f.fieldId === fieldId)) return 'columns'
      if (self.values.some(f => f.fieldId === fieldId)) return 'values'
      return null
    },
  }))
  .actions(self => ({
    addField(zone: PivotZone, fieldId: string, name: string, _fieldType?: string) {
      if (zone === 'filters') {
        if (self.filters.some(f => f.fieldId === fieldId)) return
      } else {
        if (self.isFieldUsed(fieldId)) return
      }
      switch (zone) {
        case 'rows':
          self.rows.push({ fieldId, name })
          break
        case 'columns':
          self.columns.push({ fieldId, name })
          break
        case 'values': {
          const agg: AggregationType = 'original'
          self.values.push({ fieldId, name, aggregation: agg })
          break
        }
        case 'filters':
          self.filters.push({ fieldId, name, operator: 'eq', filterValue: '' })
          break
      }
    },
    removeField(zone: PivotZone, fieldId: string) {
      switch (zone) {
        case 'rows':
          self.rows.replace(self.rows.filter(f => f.fieldId !== fieldId))
          break
        case 'columns':
          self.columns.replace(self.columns.filter(f => f.fieldId !== fieldId))
          break
        case 'values':
          self.values.replace(self.values.filter(f => f.fieldId !== fieldId))
          break
        case 'filters':
          self.filters.replace(self.filters.filter(f => f.fieldId !== fieldId))
          break
      }
    },
    moveField(fromZone: PivotZone, toZone: PivotZone, fieldId: string, fieldType?: string) {
      const getZone = (z: PivotZone) => {
        switch (z) {
          case 'rows': return self.rows
          case 'columns': return self.columns
          case 'values': return self.values
          case 'filters': return self.filters
        }
      }
      const from = getZone(fromZone)
      const field = from.find(f => f.fieldId === fieldId)
      if (!field) return
      const name = field.name
      this.removeField(fromZone, fieldId)
      this.addField(toZone, fieldId, name, fieldType)
    },
    setAggregation(fieldId: string, agg: AggregationType) {
      const field = self.values.find(f => f.fieldId === fieldId)
      if (field) field.aggregation = agg
    },
    setFilter(fieldId: string, operator: FilterOperator, value: string | string[]) {
      const field = self.filters.find(f => f.fieldId === fieldId)
      if (field) {
        field.operator = operator
        field.filterValue = typeof value === 'string' ? value : (value as any)
      }
    },
    applyConfig(config: PivotConfig) {
      self.rows.replace((config.rows ?? []) as any)
      self.columns.replace((config.columns ?? []) as any)
      const normalizedValues = (config.values ?? []).map(v => ({
        ...v,
        aggregation: v.aggregation ?? 'original',
      }))
      self.values.replace(normalizedValues as any)
      const normalizedFilters = (config.filters ?? []).map(f => ({
        ...f,
        filterValue: f.filterValue ?? '',
        operator: f.operator ?? 'eq',
      }))
      self.filters.replace(normalizedFilters as any)
    },
    clear() {
      self.rows.clear()
      self.columns.clear()
      self.values.clear()
      self.filters.clear()
    },
  }))

export type IPivotStore = Instance<typeof PivotStore>
