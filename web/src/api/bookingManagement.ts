import { api } from './client'

export interface BookingManagementRow {
  id: string; bookingCode: string; passenger: string; flightNumber: string
  status: string; price: number; archived: boolean
}
export interface BookingCsvPreview {
  totalRows: number; validRows: number; invalidRows: number
  rows: { line: number; bookingCode: string; status: string; archived?: boolean; valid: boolean; error?: string }[]
}
interface CsvExport { filename: string; csv: string }

export const bookingManagementApi = {
  list: () => api<BookingManagementRow[]>('/api/bookings/management'),
  bulkUpdate: (bookingIds: string[], status?: string, archived?: boolean) =>
    api<{updated:number}>('/api/bookings/management/bulk-update', { method: 'POST', body: { bookingIds, status, archived } }),
  previewCsv: (csv: string) => api<BookingCsvPreview>('/api/bookings/management/csv/preview', { method: 'POST', body: { csv } }),
  applyCsv: (csv: string) => api<{updated:number}>('/api/bookings/management/csv/apply', { method: 'POST', body: { csv } }),
  async exportCsv() {
    const result = await api<CsvExport>('/api/bookings/management/csv')
    const url = URL.createObjectURL(new Blob([result.csv], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a'); link.href = url; link.download = result.filename; link.click(); URL.revokeObjectURL(url)
  },
}
