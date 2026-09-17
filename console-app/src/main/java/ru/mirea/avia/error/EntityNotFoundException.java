package ru.mirea.avia.error;

/** Сигнал отсутствия запрошенной сущности (коды E-2xx). */
public class EntityNotFoundException extends AppException {
    public EntityNotFoundException(ErrorCode code, String message) { super(code, message); }

    public static EntityNotFoundException booking(long id) {
        return new EntityNotFoundException(ErrorCode.E_201, "Бронирование с ID " + id + " не найдено");
    }

    public static EntityNotFoundException bookingRef(String bookingRef) {
        return new EntityNotFoundException(ErrorCode.E_201, "Бронирование с номером " + bookingRef + " не найдено");
    }

    public static EntityNotFoundException passenger(long id) {
        return new EntityNotFoundException(ErrorCode.E_202, "Пассажир с ID " + id + " не найден");
    }

    public static EntityNotFoundException flight(long id) {
        return new EntityNotFoundException(ErrorCode.E_202, "Рейс с ID " + id + " не найден");
    }
}
