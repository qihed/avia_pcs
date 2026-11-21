package ru.mirea.avia;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.mirea.avia.domain.Booking;
import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.domain.DocumentType;
import ru.mirea.avia.domain.FareClass;
import ru.mirea.avia.domain.Flight;
import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.domain.Passenger;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.repository.BookingRepository;
import ru.mirea.avia.repository.FlightRepository;
import ru.mirea.avia.repository.PassengerRepository;
import ru.mirea.avia.security.AuthUser;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Набор данных для тестов поиска, тегов и массовых операций. Брони создаются напрямую через репозиторий,
 * чтобы задать любые статусы, теги и признак архива.
 *
 * <pre>
 * код     пассажир  рейс (вылет)       место класс    цена   статус     теги               багаж архив срочность
 * IVA001  Иванов    SU100 (+3 ч)       1A    ECONOMY   5000  PAID       vip                да    нет   CRITICAL
 * IVA002  Иванов    SU200 (+2 сут)     2B    BUSINESS 25000  CREATED    vip, корпоратив    нет   нет   MEDIUM
 * PET003  Петрова   SU200 (+2 сут)     3C    COMFORT  12000  PAID       —                  нет   нет   MEDIUM
 * PET004  Петрова   S7300 (+10 сут)    4D    ECONOMY   7000  CANCELLED  —                  нет   нет   NONE
 * PET005  Петрова   SU100 (+3 ч)       5E    ECONOMY   4000  COMPLETED  —                  нет   да    NONE
 * </pre>
 */
final class BookingFixtures {

    final Passenger ivanov;
    final Passenger petrova;
    final Flight su100;
    final Flight su200;
    final Flight s7300;
    final Booking b1;
    final Booking b2;
    final Booking b3;
    final Booking b4;
    final Booking b5;

    BookingFixtures(PassengerRepository passengers, FlightRepository flights, BookingRepository bookings) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ivanov = passengers.saveAndFlush(passenger("Иванов", "Иван", "ivanov@test.ru", "4510123456"));
        petrova = passengers.saveAndFlush(passenger("Петрова", "Анна", "petrova@test.ru", "4510654321"));
        su100 = flights.saveAndFlush(flight("SU100", "SVO", "LED", now.plusHours(3)));
        su200 = flights.saveAndFlush(flight("SU200", "SVO", "AER", now.plusDays(2)));
        s7300 = flights.saveAndFlush(flight("S7300", "DME", "OVB", now.plusDays(10)));
        // время создания разное, чтобы сортировка по умолчанию (createdAt desc) была предсказуемой: b5 новее всех
        b1 = bookings.saveAndFlush(booking("IVA001", ivanov, su100, "1A", FareClass.ECONOMY, "5000", BookingStatus.PAID,
                List.of("vip"), true, false, now.minusMinutes(5)));
        b2 = bookings.saveAndFlush(booking("IVA002", ivanov, su200, "2B", FareClass.BUSINESS, "25000", BookingStatus.CREATED,
                List.of("vip", "корпоратив"), false, false, now.minusMinutes(4)));
        b3 = bookings.saveAndFlush(booking("PET003", petrova, su200, "3C", FareClass.COMFORT, "12000", BookingStatus.PAID,
                List.of(), false, false, now.minusMinutes(3)));
        b4 = bookings.saveAndFlush(booking("PET004", petrova, s7300, "4D", FareClass.ECONOMY, "7000", BookingStatus.CANCELLED,
                List.of(), false, false, now.minusMinutes(2)));
        b5 = bookings.saveAndFlush(booking("PET005", petrova, su100, "5E", FareClass.ECONOMY, "4000", BookingStatus.COMPLETED,
                List.of(), false, true, now.minusMinutes(1)));
    }

    static Passenger passenger(String last, String first, String email, String document) {
        Passenger p = new Passenger();
        p.lastName = last;
        p.firstName = first;
        p.birthDate = java.time.LocalDate.of(1990, 1, 1);
        p.documentType = DocumentType.PASSPORT_RF;
        p.documentNumber = document;
        p.email = email;
        p.phone = "+79990000000";
        p.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        return p;
    }

    static Flight flight(String number, String from, String to, LocalDateTime departure) {
        Flight f = new Flight();
        f.flightNumber = number;
        f.airline = "Test";
        f.departureAirport = from;
        f.arrivalAirport = to;
        f.departureTime = departure;
        f.arrivalTime = departure.plusHours(2);
        f.aircraftType = "A320";
        f.totalSeats = 180;
        f.basePrice = new BigDecimal("5000.00");
        f.status = FlightStatus.SCHEDULED;
        f.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
        return f;
    }

    static Booking booking(String code, Passenger p, Flight f, String seat, FareClass fare, String price,
                           BookingStatus status, List<String> tags, boolean baggage, boolean archived,
                           LocalDateTime createdAt) {
        Booking b = new Booking();
        b.bookingCode = code;
        b.passenger = p;
        b.flight = f;
        b.seatNumber = seat;
        b.fareClass = fare;
        b.price = new BigDecimal(price);
        b.status = status;
        b.tags.addAll(tags);
        b.extraBaggage = baggage;
        b.archived = archived;
        b.createdAt = createdAt;
        b.updatedAt = createdAt;
        return b;
    }

    /** Пользователь с ролью и всеми вложенными в неё ролями — как после JwtAuthenticationFilter. */
    static RequestPostProcessor as(Role role) {
        List<SimpleGrantedAuthority> authorities = role.withIncluded().stream()
                .map(r -> new SimpleGrantedAuthority(r.authority()))
                .toList();
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthUser(UUID.randomUUID(), "test", "Тест", false, Set.of(role)), null, authorities));
    }
}
