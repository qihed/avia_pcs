import { api } from './client'

export interface AuditEvent {
  id: string; userId?: string; username?: string; eventType: string; entityType?: string
  entityId?: string; description: string; correlationId?: string; createdAt: string
}
export const auditApi = {
  list: (q = '', eventType = '') => api<AuditEvent[]>(`/api/admin/audit?q=${encodeURIComponent(q)}&eventType=${encodeURIComponent(eventType)}`),
}
