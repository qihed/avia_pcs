package ru.mirea.avia.domain;

import ru.mirea.avia.error.InvalidStatusTransitionException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Рейс — выполнение перевозки по маршруту в заданную дату и время.
 *
 * <p>Рейс существует независимо от бронирований: у него собственные вместимость,
 * базовый тариф и статус, от которых зависят правила BR-01 и BR-02.</p>
 */
public class Flight {
    /** Номер места содержит не более двух цифр ряда (CON-09). */
    public static final int MAX_ROW = 99;
    private static final int SEATS_PER_ROW = 6;

    private Long id;
    private String flightNumber;
    private String airline;
    private String departureAirport;
    private String arrivalAirport;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String aircraftType;
    private int totalSeats;
    private BigDecimal basePrice;
    private FlightStatus status = FlightStatus.SCHEDULED;

    public Flight(String flightNumber, String airline, String departureAirport, String arrivalAirport,
                  LocalDateTime departureTime, LocalDateTime arrivalTime, String aircraftType,
                  int totalSeats, BigDecimal basePrice) {
        this.flightNumber = Objects.requireNonNull(flightNumber, "flightNumber");
        this.airline = Objects.requireNonNull(airline, "airline");
        this.departureAirport = Objects.requireNonNull(departureAirport, "departureAirport");
        this.arrivalAirport = Objects.requireNonNull(arrivalAirport, "arrivalAirport");
        this.departureTime = Objects.requireNonNull(departureTime, "departureTime");
        this.arrivalTime = Objects.requireNonNull(arrivalTime, "arrivalTime");
        this.aircraftType = Objects.requireNonNull(aircraftType, "aircraftType");
        this.totalSeats = totalSeats;
        this.basePrice = Objects.requireNonNull(basePrice, "basePrice");
    }

    /** Возвращает маршрут в виде «SVO -> LED». */
    public String getRoute() {
        return departureAirport + " -> " + arrivalAirport;
    }

    public Duration getDuration() {
        return Duration.between(departureTime, arrivalTime);
    }

    /**
     * Проверяет, открыт ли рейс для продажи (BR-01).
     *
     * <p>Статус должен быть SCHEDULED или DELAYED, а до вылета — не меньше {@code saleCloseBefore}.</p>
     */
    public boolean isOpenForSale(LocalDateTime now, Duration saleCloseBefore) {
        return status.isOpenForSale() && !now.plus(saleCloseBefore).isAfter(departureTime);
    }

    /** Рейс уже вылетел: статус DEPARTED или ARRIVED либо наступило время вылета. */
    public boolean hasDeparted(LocalDateTime now) {
        return status.hasDeparted() || !now.isBefore(departureTime);
    }

    /** Возвращает число рядов при раскладке «6 мест в ряду, A–F», но не больше {@link #MAX_ROW}. */
    public int getRowCount() {
        return Math.min((totalSeats + SEATS_PER_ROW - 1) / SEATS_PER_ROW, MAX_ROW);
    }

    /** Меняет статус рейса с проверкой допустимости перехода (E-302). */
    public void changeStatus(FlightStatus target) {
        if (!status.canTransitionTo(target)) {
            throw InvalidStatusTransitionException.of(status, target, status.isFinal()
                    ? "Рейс находится в финальном состоянии и не может быть изменён"
                    : null);
        }
        status = target;
    }

    /** Меняет аэропорты вылета и назначения одновременно. */
    public void setRoute(String departureAirport, String arrivalAirport) {
        this.departureAirport = Objects.requireNonNull(departureAirport, "departureAirport");
        this.arrivalAirport = Objects.requireNonNull(arrivalAirport, "arrivalAirport");
    }

    /** Меняет время вылета и прилёта одновременно. */
    public void setSchedule(LocalDateTime departureTime, LocalDateTime arrivalTime) {
        this.departureTime = Objects.requireNonNull(departureTime, "departureTime");
        this.arrivalTime = Objects.requireNonNull(arrivalTime, "arrivalTime");
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Flight flight && id != null && id.equals(flight.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Flight[id=" + id + ", " + flightNumber + ", " + getRoute() + ", " + departureTime + ", " + status + "]";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String value) { this.flightNumber = Objects.requireNonNull(value, "flightNumber"); }
    public String getAirline() { return airline; }
    public void setAirline(String airline) { this.airline = Objects.requireNonNull(airline, "airline"); }
    public String getDepartureAirport() { return departureAirport; }
    public String getArrivalAirport() { return arrivalAirport; }
    public LocalDateTime getDepartureTime() { return departureTime; }
    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public String getAircraftType() { return aircraftType; }
    public void setAircraftType(String value) { this.aircraftType = Objects.requireNonNull(value, "aircraftType"); }
    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }
    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = Objects.requireNonNull(basePrice, "basePrice"); }
    public FlightStatus getStatus() { return status; }
    public void setStatus(FlightStatus status) { this.status = Objects.requireNonNull(status, "status"); }
}
