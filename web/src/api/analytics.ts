import { api } from './client'

export interface AnalyticsGroup { name: string; count: number }
export interface AnalyticsSummary {
  totalBookings: number; activeBookings: number; archivedBookings: number
  totalPassengers: number; totalFlights: number; revenue: number
  bookingsByStatus: AnalyticsGroup[]; bookingsByFareClass: AnalyticsGroup[]
}

export const analyticsApi = { summary: () => api<AnalyticsSummary>('/api/analytics') }
