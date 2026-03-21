export type AggregationType = 'raw' | 'sum' | 'avg' | 'count' | 'min' | 'max'

export type PivotZone = 'rows' | 'columns' | 'values' | 'filters'

export type FilterOperator = 'eq' | 'neq' | 'gt' | 'lt' | 'in'

export interface PivotField {
  fieldId: string
  name: string
}

export interface PivotValueField extends PivotField {
  aggregation: AggregationType
}

export interface PivotFilterField extends PivotField {
  operator: FilterOperator
  filterValue: string | string[]
}

export interface PivotConfig {
  rows: PivotField[]
  columns: PivotField[]
  values: PivotValueField[]
  filters: PivotFilterField[]
}

export interface PivotResultRow {
  keys: string[]
  values: Record<string, number>
  children?: PivotResultRow[]
}

export interface PivotResult {
  columnKeys: string[][]
  rows: PivotResultRow[]
  totals: Record<string, number>
  totalRows: number
  offset: number
  limit: number
}
