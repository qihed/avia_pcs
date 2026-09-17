package ru.mirea.avia.ui;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Консольный ввод-вывод в кодировке UTF-8 (NFR-19).
 *
 * <p>Единственное место приложения, где читается стандартный ввод и пишется стандартный
 * вывод (NFR-24). Экраны получают экземпляр через конструктор, поэтому потоки можно
 * подменить, например байтовыми массивами.</p>
 */
public final class ConsoleIo {
    public static final int WIDTH = 70;

    private final BufferedReader reader;
    private final PrintStream out;

    public ConsoleIo(InputStream in, PrintStream out) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
    }

    /** Возвращает консоль процесса с принудительной кодировкой UTF-8. */
    public static ConsoleIo system() {
        // Кодировка задаётся явно, так как консоль Windows по умолчанию работает не в UTF-8.
        PrintStream utf8 = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        return new ConsoleIo(System.in, utf8);
    }

    /** Возвращает линию во всю ширину экрана: {@code =} для разделов, {@code -} для подразделов. */
    public static String line(char symbol) {
        return String.valueOf(symbol).repeat(WIDTH);
    }

    /** Возвращает заголовок, центрированный внутри линии: {@code ===== СТАТИСТИКА =====}. */
    public static String titledLine(String title, char symbol) {
        String text = " " + title + " ";
        int left = Math.max(2, (WIDTH - text.length()) / 2);
        int right = Math.max(2, WIDTH - text.length() - left);
        return String.valueOf(symbol).repeat(left) + text + String.valueOf(symbol).repeat(right);
    }

    public void println(String text) {
        out.println(text);
    }

    public void println() {
        out.println();
    }

    public void print(String text) {
        out.print(text);
        out.flush();
    }

    /** Возвращает строку ввода без перевода строки; в конце потока бросает {@link InputClosedException}. */
    public String readLine() {
        String line;
        try {
            line = reader.readLine();
        } catch (IOException ex) {
            // Сбой чтения стандартного ввода неустраним, поэтому приложение завершается как при закрытом потоке.
            throw new InputClosedException();
        }
        if (line == null) throw new InputClosedException();
        return line;
    }
}
