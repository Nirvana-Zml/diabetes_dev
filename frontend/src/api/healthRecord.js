import { get, put } from '@/utils/request'
import { toSnakeCase, toCamelCase } from '@/utils/normalize'
import { mockHealthRecord } from '@/mock/data'

/** GET /api/v1/health-records/latest */
export function getHealthRecord() {
  return get('/health-records/latest', {
    mockFn: async () => mockHealthRecord,
  }).then((data) => toSnakeCase(data))
}

/** PUT /api/v1/health-records */
export function updateHealthRecord(data) {
  return put('/health-records', toCamelCase(data), {
    mockFn: async () => {
      const bmi = data.height && data.weight
        ? Math.round((data.weight / ((data.height / 100) ** 2)) * 10) / 10
        : mockHealthRecord.bmi
      return { ...mockHealthRecord, ...data, bmi, recorded_at: new Date().toISOString() }
    },
  }).then((res) => toSnakeCase(res))
}
