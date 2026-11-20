import { api } from "./client";
export interface Passenger {
  id: string;
  lastName: string;
  firstName: string;
  middleName?: string;
  birthDate: string;
  documentType: string;
  documentNumber: string;
  email: string;
  phone: string;
}
export interface Flight {
  id: string;
  flightNumber: string;
  airline: string;
  departureAirport: string;
  arrivalAirport: string;
  departureTime: string;
  arrivalTime: string;
  aircraftType: string;
  totalSeats: number;
  basePrice: number;
  status: string;
  active: boolean;
  version: number;
}
export type BookingStatus =
  "CREATED" | "PAID" | "CHECKED_IN" | "COMPLETED" | "CANCELLED" | "EXPIRED";
export interface Booking {
  id: string;
  bookingCode: string;
  passenger: Passenger;
  flight: Flight;
  seatNumber: string;
  fareClass: string;
  price: number;
  extraBaggage: boolean;
  archived: boolean;
  version: number;
  status: BookingStatus;
  urgencyLevel: string;
  createdAt: string;
  updatedAt: string;
}
export interface Note {
  id: string;
  authorId?: string;
  authorName: string;
  text: string;
  pinned: boolean;
  createdAt: string;
  updatedAt: string;
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
export const statusNames: Record<BookingStatus, string> = {
  CREATED: "Ожидает оплаты",
  PAID: "Оплачено",
  CHECKED_IN: "Регистрация пройдена",
  COMPLETED: "Выполнено",
  CANCELLED: "Отменено",
  EXPIRED: "Просрочено",
};
export const targets: Record<BookingStatus, BookingStatus[]> = {
  CREATED: ["PAID", "CANCELLED"],
  PAID: ["CHECKED_IN", "CANCELLED"],
  CHECKED_IN: ["COMPLETED"],
  COMPLETED: [],
  CANCELLED: [],
  EXPIRED: [],
};
export const urgencyNames: Record<string, string> = {
  NONE: "Нет",
  LOW: "Низкая",
  MEDIUM: "Средняя",
  HIGH: "Высокая",
  CRITICAL: "Критическая",
};
export const refs = {
  passengers: () => api<Passenger[]>("/api/references/passengers"),
  flights: () => api<Flight[]>("/api/references/flights"),
};
export function date(value: string) {
  return value.replace("T", " ").slice(0, 16);
}
