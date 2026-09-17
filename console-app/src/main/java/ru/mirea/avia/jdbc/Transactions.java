package ru.mirea.avia.jdbc;

import java.util.function.Supplier;

/**
 * Граница транзакции для сервисов — аналог {@code @Transactional}.
 *
 * <p>Сервисы зависят от интерфейса, а не от {@link DatabaseManager}, поэтому бизнес-правила
 * проверяются модульными тестами без PostgreSQL.</p>
 */
public interface Transactions {
    /** Выполняет чтение; аналог {@code @Transactional(readOnly = true)}. */
    <T> T read(Supplier<T> work);

    /** Выполняет изменение в одной транзакции: commit при успехе, rollback при ошибке. */
    <T> T write(Supplier<T> work);

    /** Возвращает реализацию без транзакций — для тестов с репозиториями в памяти. */
    static Transactions direct() {
        return new Transactions() {
            @Override
            public <T> T read(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T write(Supplier<T> work) {
                return work.get();
            }
        };
    }
}
