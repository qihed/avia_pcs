package ru.mirea.avia.domain;

import ru.mirea.avia.error.InvalidStatusTransitionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Бронирование — основная сущность системы.
 *
 * <p>Закрепляет за пассажиром место на рейсе с фиксацией класса обслуживания, стоимости
 * и статуса. Сеттера статуса нет: статус меняется только методом
 * {@link #changeStatus(BookingStatus, LocalDateTime)}, который проверяет матрицу BR-07.
 * Новая бронь создаётся в статусе CREATED, ID назначается при сохранении; полный конструктор
 * используется репозиторием при сборке брони из {@code ResultSet}.</p>
 */
public class Booking extends AuditedEntity {
    private Long id;
    private final String bookingRef;
    private final Passenger passenger;
    private final Flight flight;
    private String seatNumber;
    private FareClass fareClass;
    private boolean baggageIncluded;
    private BigDecimal price;
    private BookingStatus status;

    public Booking(Long id, String bookingRef, Passenger passenger, Flight flight, String seatNumber,
                   FareClass fareClass, boolean baggageIncluded, BigDecimal price, BookingStatus status) {
        this.id = id;
        this.bookingRef = Objects.requireNonNull(bookingRef, "bookingRef");
        this.passenger = Objects.requireNonNull(passenger, "passenger");
        this.flight = Objects.requireNonNull(flight, "flight");
        this.seatNumber = Objects.requireNonNull(seatNumber, "seatNumber");
        this.fareClass = Objects.requireNonNull(fareClass, "fareClass");
        this.baggageIncluded = baggageIncluded;
        this.price = requirePositive(price);
        this.status = Objects.requireNonNull(status, "status");
    }

    public Booking(String bookingRef, Passenger passenger, Flight flight, String seatNumber,
                   FareClass fareClass, boolean baggageIncluded, BigDecimal price) {
        this(null, bookingRef, passenger, flight, seatNumber, fareClass, baggageIncluded, price, BookingStatus.CREATED);
    }

    /** Занимает ли бронь место на рейсе: любой статус, кроме CANCELLED и EXPIRED. */
    public boolean isActive() {
        return status.occupiesSeat();
    }

    /** Отмена допускается только из статусов CREATED и PAID (BR-08). */
    public boolean canBeCancelled() {
        return status == BookingStatus.CREATED || status == BookingStatus.PAID;
    }

    /** Изменение места и тарифа допускается до регистрации на рейс (FR-08). */
    public boolean canBeModified() {
        return status == BookingStatus.CREATED || status == BookingStatus.PAID;
    }

    /** Физическое удаление допускается только для отменённых и просроченных броней (BR-10). */
    public boolean canBeDeleted() {
        return status == BookingStatus.CANCELLED || status == BookingStatus.EXPIRED;
    }

    /**
     * Переводит бронь в новый статус с проверкой матрицы переходов (BR-07).
     *
     * @throws InvalidStatusTransitionException E-302, если переход запрещён
     */
    public void changeStatus(BookingStatus target, LocalDateTime changedAt) {
        Objects.requireNonNull(target, "target");
        if (!status.canTransitionTo(target)) {
            throw InvalidStatusTransitionException.of(status, target, status.isFinal()
                    ? "Бронь находится в финальном состоянии и не может быть изменена"
                    : null);
        }
        status = target;
        onUpdate(changedAt);
    }

    /** Меняет место, класс и багаж вместе с пересчитанной стоимостью (FR-08). */
    public void modify(String newSeatNumber, FareClass newFareClass, boolean newBaggageIncluded,
                       BigDecimal newPrice, LocalDateTime changedAt) {
        seatNumber = Objects.requireNonNull(newSeatNumber, "seatNumber");
        fareClass = Objects.requireNonNull(newFareClass, "fareClass");
        baggageIncluded = newBaggageIncluded;
        price = requirePositive(newPrice);
        onUpdate(changedAt);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Booking booking && id != null && id.equals(booking.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Booking[id=" + id + ", ref=" + bookingRef + ", passenger=" + passenger.getShortName()
                + ", flight=" + flight.getFlightNumber() + ", seat=" + seatNumber + ", " + fareClass
                + ", price=" + price + ", " + status + "]";
    }

    private static BigDecimal requirePositive(BigDecimal price) {
        Objects.requireNonNull(price, "price");
        if (price.signum() <= 0) throw new IllegalArgumentException("Booking price must be positive: " + price);
        return price;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBookingRef() { return bookingRef; }
    public Passenger getPassenger() { return passenger; }
    public Flight getFlight() { return flight; }
    public String getSeatNumber() { return seatNumber; }
    public FareClass getFareClass() { return fareClass; }
    public boolean isBaggageIncluded() { return baggageIncluded; }
    public BigDecimal getPrice() { return price; }
    public BookingStatus getStatus() { return status; }
}
