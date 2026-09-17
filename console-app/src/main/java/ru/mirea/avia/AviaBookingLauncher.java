package ru.mirea.avia;

import ru.mirea.avia.ui.ConsoleIo;

import java.util.logging.LogManager;

/**
 * Отдельная точка входа консольного приложения «АВИА-БРОНЬ».
 *
 * <p>Launcher только настраивает JVM и передаёт управление composition root
 * {@link AviaBookingConsoleApplication}. Все зависимости создаются там, а процесс
 * получает ненулевой код завершения, если приложение не удалось запустить.</p>
 */
public final class AviaBookingLauncher {
    private AviaBookingLauncher() {
        // Экземпляр launcher-класса создавать не требуется.
    }

    /**
     * Запускает консольное приложение в кодировке UTF-8.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        // Apache POI рассчитывает ширину столбцов Excel через AWT, графическая подсистема при этом не нужна.
        System.setProperty("java.awt.headless", "true");
        // Драйвер PostgreSQL пишет предупреждения через java.util.logging и может вывести в консоль
        // строку подключения вместе с паролем. Журнал приложения ведёт SLF4J, поэтому обработчики JUL снимаются.
        LogManager.getLogManager().reset();
        int code = new AviaBookingConsoleApplication(ConsoleIo.system()).run();
        if (code != 0) System.exit(code);
    }
}
