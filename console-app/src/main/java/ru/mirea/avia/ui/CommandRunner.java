package ru.mirea.avia.ui;

import ru.mirea.avia.error.ConsoleErrorHandler;
import ru.mirea.avia.error.CorrelationId;

/**
 * Выполняет одну команду меню в отдельном контексте журнала.
 *
 * <p>Correlation ID создаётся на каждую команду и попадает в журнал и в текст ошибки,
 * поэтому оператор может сообщить его администратору. Любая ошибка команды превращается
 * в безопасный отчёт, после чего управление возвращается в меню (FR-01, FR-02).</p>
 */
public final class CommandRunner {
    private final ConsoleErrorHandler errors;
    private final ConsoleIo io;

    public CommandRunner(ConsoleErrorHandler errors, ConsoleIo io) {
        this.errors = errors;
        this.io = io;
    }

    /** Выполняет команду и возвращает {@code true}, если она завершилась без ошибок и без отмены. */
    public boolean run(String command, Runnable action) {
        return execute(command, action) == Outcome.COMPLETED;
    }

    Outcome execute(String command, Runnable action) {
        // Команда раздела запускает вложенные команды, поэтому после них восстанавливается внешний ID.
        String outer = CorrelationId.open();
        try {
            action.run();
            return Outcome.COMPLETED;
        } catch (InputCancelledException ex) {
            Ui.info(io, "Операция отменена");
            return Outcome.CANCELLED;
        } catch (InputClosedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Ui.error(io, errors.handle(ex, command));
            return Outcome.FAILED;
        } finally {
            CorrelationId.restore(outer);
        }
    }

    /** Итог выполнения команды: меню не делает паузу после отменённой операции. */
    enum Outcome { COMPLETED, CANCELLED, FAILED }
}
