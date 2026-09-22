package ru.mirea.avia.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Бронирование — основная сущность системы: место пассажира на рейсе. */
public class Booking {
    private long id;
    private Passenger passenger;
    private Flight flight;
    private String seatNumber;
    private FareClass fareClass;
    private BigDecimal price;
    private BookingStatus status;
    private LocalDateTime createdAt;

    public Booking(long id, Passenger passenger, Flight flight, String seatNumber, FareClass fareClass,
                   BigDecimal price, BookingStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.passenger = passenger;
        this.flight = flight;
        this.seatNumber = seatNumber;
        this.fareClass = fareClass;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Booking(Passenger passenger, Flight flight, String seatNumber, FareClass fareClass, BigDecimal price) {
        this(0, passenger, flight, seatNumber, fareClass, price, BookingStatus.CREATED, null);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public Passenger getPassenger() { return passenger; }
    public void setPassenger(Passenger passenger) { this.passenger = passenger; }
    public Flight getFlight() { return flight; }
    public void setFlight(Flight flight) { this.flight = flight; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public FareClass getFareClass() { return fareClass; }
    public void setFareClass(FareClass fareClass) { this.fareClass = fareClass; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return id + " | " + passenger.getShortName() + " | " + flight.getFlightNumber()
                + " " + flight.getRoute() + " " + flight.getDepartureTime()
                + " | место " + seatNumber + " | " + fareClass.getTitle()
                + " | " + price + " | " + status.getTitle();
    }
}
