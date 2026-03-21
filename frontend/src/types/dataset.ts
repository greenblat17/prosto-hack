export type FieldType = 'string' | 'number' | 'date' | 'boolean'

export interface DatasetField {
  id: string
  name: string
  type: FieldType
  category: string
}
