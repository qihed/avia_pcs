package ru.mirea.avia.error;

/**
 * Коды ошибок системы (п. 6.10.1 ТЗ).
 *
 * <p>Первая цифра кода определяет класс исключения: 1xx — {@link ValidationException},
 * 2xx — {@link EntityNotFoundException}, 3xx — {@link BusinessRuleException} и наследники,
 * 5xx — {@link DataAccessException}, 6xx — {@link ExportException}.</p>
 */
public enum ErrorCode {
    E_101("E-101", "Введён текст вместо числа"),
    E_102("E-102", "Некорректная дата или время"),
    E_103("E-103", "Нарушен формат поля"),
    E_104("E-104", "Обязательное поле не заполнено"),
    E_105("E-105", "Выбран несуществующий пункт меню"),
    E_201("E-201", "Бронирование не найдено"),
    E_202("E-202", "Пассажир или рейс не найден"),
    E_300("E-300", "Рейс закрыт для продажи (BR-01)"),
    E_301("E-301", "Место занято или мест нет (BR-02, BR-03)"),
    E_302("E-302", "Запрещённый переход статуса (BR-07)"),
    E_303("E-303", "Дубль брони пассажира на рейс (BR-04)"),
    E_304("E-304", "Отмена позже установленного срока (BR-08)"),
    E_305("E-305", "Удаление активной записи (BR-10)"),
    E_306("E-306", "Операция недоступна в текущем состоянии брони или рейса"),
    E_307("E-307", "Дубликат записи справочника"),
    E_500("E-500", "Непредвиденная внутренняя ошибка"),
    E_501("E-501", "Нет соединения с базой данных"),
    E_502("E-502", "Ошибка выполнения SQL-запроса"),
    E_503("E-503", "Нарушение ограничения целостности СУБД"),
    E_601("E-601", "Ошибка записи файла экспорта");

    private final String code;
    private final String description;

    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String toString() {
        return code;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
}
