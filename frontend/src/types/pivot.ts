export type AggregationType =
  | 'original' | 'count' | 'count_distinct' | 'list_distinct'
  | 'sum' | 'int_sum' | 'avg' | 'median'
  | 'variance' | 'stddev' | 'min' | 'max'
  | 'first' | 'last' | 'running_sum'
  | 'sum_pct_total' | 'sum_pct_row' | 'sum_pct_col'
  | 'count_pct_total' | 'count_pct_row' | 'count_pct_col'

export const aggregationLabels: Record<AggregationType, string> = {
  original: 'Оригинал',
  count: 'Количество',
  count_distinct: 'Кол-во уникальных',
  list_distinct: 'Список уникальных',
  sum: 'Сумма',
  int_sum: 'Целочисл. сумма',
  avg: 'Среднее',
  median: 'Медиана',
  variance: 'Дисперсия',
  stddev: 'Ст. отклонение',
  min: 'Минимум',
  max: 'Максимум',
  first: 'Первое',
  last: 'Последнее',
  running_sum: 'Нарастающий итог',
  sum_pct_total: '% от итога (сумма)',
  sum_pct_row: '% от строк (сумма)',
  sum_pct_col: '% от колонок (сумма)',
  count_pct_total: '% от итога (кол-во)',
  count_pct_row: '% от строк (кол-во)',
  count_pct_col: '% от колонок (кол-во)',
}

export type PivotZone = 'rows' | 'columns' | 'values' | 'filters'

export type FilterOperator = 'eq' | 'neq' | 'gt' | 'gte' | 'lt' | 'lte' | 'in'

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
  values: Record<string, number | string>
  children?: PivotResultRow[]
}

export interface PivotResult {
  columnKeys: string[][]
  rows: PivotResultRow[]
  totals: Record<string, number | string>
  totalRows: number
  offset: number
  limit: number
}
