package ru.mirea.avia.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Рейс — выполнение перевозки по маршруту в заданную дату и время. */
public class Flight {
    private long id;
    private String flightNumber;
    private String airline;
    private String departureAirport;
    private String arrivalAirport;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String aircraftType;
    private int totalSeats;
    private BigDecimal basePrice;
    private FlightStatus status;

    public Flight(long id, String flightNumber, String airline, String departureAirport, String arrivalAirport,
                  LocalDateTime departureTime, LocalDateTime arrivalTime, String aircraftType,
                  int totalSeats, BigDecimal basePrice, FlightStatus status) {
        this.id = id;
        this.flightNumber = flightNumber;
        this.airline = airline;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.aircraftType = aircraftType;
        this.totalSeats = totalSeats;
        this.basePrice = basePrice;
        this.status = status;
    }

    public Flight(String flightNumber, String airline, String departureAirport, String arrivalAirport,
                  LocalDateTime departureTime, LocalDateTime arrivalTime, String aircraftType,
                  int totalSeats, BigDecimal basePrice) {
        this(0, flightNumber, airline, departureAirport, arrivalAirport, departureTime, arrivalTime,
                aircraftType, totalSeats, basePrice, FlightStatus.SCHEDULED);
    }

    /** Возвращает маршрут в виде «SVO -> LED». */
    public String getRoute() {
        return departureAirport + " -> " + arrivalAirport;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
    public String getAirline() { return airline; }
    public void setAirline(String airline) { this.airline = airline; }
    public String getDepartureAirport() { return departureAirport; }
    public void setDepartureAirport(String departureAirport) { this.departureAirport = departureAirport; }
    public String getArrivalAirport() { return arrivalAirport; }
    public void setArrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; }
    public LocalDateTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalDateTime departureTime) { this.departureTime = departureTime; }
    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(LocalDateTime arrivalTime) { this.arrivalTime = arrivalTime; }
    public String getAircraftType() { return aircraftType; }
    public void setAircraftType(String aircraftType) { this.aircraftType = aircraftType; }
    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public FlightStatus getStatus() { return status; }
    public void setStatus(FlightStatus status) { this.status = status; }

    @Override
    public String toString() {
        return id + " | " + flightNumber + " | " + airline + " | " + getRoute()
                + " | " + departureTime + " -> " + arrivalTime + " | " + aircraftType
                + " | мест: " + totalSeats + " | тариф: " + basePrice + " | " + status.getTitle();
    }
}
