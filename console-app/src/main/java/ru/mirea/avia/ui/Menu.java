package ru.mirea.avia.ui;

import ru.mirea.avia.ui.CommandRunner.Outcome;

import java.util.ArrayList;
import java.util.List;

/**
 * Циклическое консольное меню раздела (FR-01).
 *
 * <p>Экран описывает пункты цепочкой вызовов {@link #item} и {@link #section}, а меню
 * выводит их, читает выбор и выполняет пункт через {@link CommandRunner}. После операции
 * управление возвращается в это же меню; пункт {@code 0} закрывает его.</p>
 */
public final class Menu {
    private static final String DEFAULT_EXIT_TITLE = "Назад";

    private final String title;
    private final ConsoleIo io;
    private final InputPrompt prompt;
    private final CommandRunner runner;
    private final List<Entry> entries = new ArrayList<>();

    private Runnable header = () -> { };
    private String exitTitle = DEFAULT_EXIT_TITLE;

    private Menu(String title, ConsoleIo io, InputPrompt prompt, CommandRunner runner) {
        this.title = title;
        this.io = io;
        this.prompt = prompt;
        this.runner = runner;
    }

    /** Создаёт меню без пунктов; пункты добавляются цепочкой вызовов. */
    public static Menu of(String title, ConsoleIo io, InputPrompt prompt, CommandRunner runner) {
        return new Menu(title, io, prompt, runner);
    }

    /** Задаёт дополнительные строки, которые печатаются под заголовком при каждом показе меню. */
    public Menu header(Runnable printer) {
        this.header = printer;
        return this;
    }

    /** Добавляет операцию: после её выполнения меню ждёт Enter, если операция не отменена. */
    public Menu item(String title, String command, Runnable action) {
        entries.add(new Entry(title, command, action, true));
        return this;
    }

    /** Добавляет вложенный экран: после возврата из него меню выводится сразу. */
    public Menu section(String title, String command, Runnable show) {
        entries.add(new Entry(title, command, show, false));
        return this;
    }

    /** Задаёт подпись пункта 0; по умолчанию «Назад». */
    public Menu exitTitle(String text) {
        this.exitTitle = text;
        return this;
    }

    /** Показывает меню в цикле, пока оператор не выберет пункт 0. */
    public void show() {
        while (true) {
            print();
            int choice = prompt.readMenuChoice(entries.size());
            if (choice == 0) return;
            if (choice > 0) execute(entries.get(choice - 1));
        }
    }

    private void print() {
        Ui.title(io, title);
        header.run();
        for (int i = 0; i < entries.size(); i++) {
            io.println("  " + (i + 1) + ". " + entries.get(i).title());
        }
        io.println("  0. " + exitTitle);
        io.println(ConsoleIo.line('-'));
    }

    private void execute(Entry entry) {
        Outcome outcome = runner.execute(entry.command(), entry.action());
        if (entry.pauseAfter() && outcome != Outcome.CANCELLED) {
            prompt.pause();
        }
    }

    private record Entry(String title, String command, Runnable action, boolean pauseAfter) {
    }
}
