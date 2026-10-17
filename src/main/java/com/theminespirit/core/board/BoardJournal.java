package com.theminespirit.core.board;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Журнал заданий на диске: append-only JSONL, одна запись на строку. Запись идёт через
 * очередь и сбрасывается бюджетом (не больше N строк за вызов), поэтому переходы тика
 * не пишут файл синхронно. При загрузке «выполнено» возвращается в ledger; оборванная
 * хвостовая строка отрезается с ошибкой в результате, а не тихо.
 */
public final class BoardJournal {
    private final Path path;
    private final Deque<String> pending = new ArrayDeque<>();
    private int enqueued;
    private int written;

    public BoardJournal(Path path) {
        this.path = Objects.requireNonNull(path,
                "Путь журнала не может быть null: без него запись некуда класть");
    }

    public Path path() {
        return path;
    }

    public void enqueue(BoardLogEntry entry) {
        pending.addLast(BoardLog.write(entry));
        enqueued++;
    }

    public int pending() {
        return pending.size();
    }

    /** Сколько записей поставлено в журнал: каждая запись — один переход статуса задания. */
    public int enqueued() {
        return enqueued;
    }

    /** Сколько записей уже на диске: тест масштаба сверяет число строк с переходами. */
    public int written() {
        return written;
    }

    /** Сбрасывает не больше {@code maxLines} строк: остальное дочитается следующим тиком. */
    public int flush(int maxLines) {
        if (pending.isEmpty()) {
            return 0;
        }
        if (maxLines < 1) {
            throw new IllegalArgumentException("Бюджет сброса " + maxLines + " меньше 1: "
                    + "журнал не продвинется и память будет расти");
        }
        createParent();
        int flushed = 0;
        try {
            StringBuilder block = new StringBuilder();
            while (flushed < maxLines && !pending.isEmpty()) {
                block.append(pending.removeFirst()).append('\n');
                flushed++;
            }
            Files.writeString(path, block, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Журнал заданий не записан в '" + path + "': "
                    + error.getMessage(), error);
        }
        written += flushed;
        return flushed;
    }

    public int flushAll() {
        return flush(pending.size());
    }

    /**
     * Один проход по журналу при старте: каждое {@code DONE} возвращает отметку
     * «постройка стоит». Оборванная последняя строка обрезается, ошибка возвращается
     * вызывающему — адаптер пишет её в лог с причиной.
     */
    public LoadResult load(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Ledger не может быть null: восстанавливать некуда");
        if (!Files.exists(path)) {
            return new LoadResult(0, 0, -1, "", false);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Журнал заданий '" + path + "' не прочитан: "
                    + error.getMessage(), error);
        }
        int restored = 0;
        int lastLine = -1;
        String error = "";
        boolean truncated = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            try {
                BoardLogEntry entry = BoardLog.parse(line, index + 1);
                if (entry.event() == BoardLog.Event.DONE) {
                    ledger.note(entry.boardCase(), entry.level());
                    restored++;
                }
            } catch (RuntimeException failure) {
                lastLine = index + 1;
                error = failure.getMessage();
                truncated = index == lines.size() - 1;
                if (truncated) {
                    truncateAfter(index);
                }
                break;
            }
        }
        return new LoadResult(lines.size(), restored, lastLine, error, truncated);
    }

    /** Последние {@code limit} записей от свежих к старым: для GET /v1/board/log. */
    public List<BoardLogEntry> last(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit журнала " + limit + " меньше 1: "
                    + "пустой ответ не отдаётся");
        }
        if (!Files.exists(path)) {
            return List.of();
        }
        List<String> lines = tailLines(limit * 2);
        List<BoardLogEntry> entries = new ArrayList<>(limit);
        for (int index = lines.size() - 1; index >= 0 && entries.size() < limit; index--) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            entries.add(BoardLog.parse(line, index + 1));
        }
        return List.copyOf(entries);
    }

    private List<String> tailLines(int wanted) {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long length = file.length();
            if (length == 0) {
                return List.of();
            }
            long position = length - 1;
            long start = 0;
            int newlines = 0;
            while (position >= 0) {
                file.seek(position);
                if (file.read() == '\n') {
                    newlines++;
                    if (newlines > wanted) {
                        start = position + 1;
                        break;
                    }
                }
                position--;
            }
            file.seek(start);
            List<String> lines = new ArrayList<>();
            String raw;
            while ((raw = file.readLine()) != null) {
                lines.add(new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
            }
            return lines;
        } catch (IOException error) {
            throw new IllegalStateException("Хвост журнала '" + path + "' не прочитан: "
                    + error.getMessage(), error);
        }
    }

    private void truncateAfter(int brokenIndex) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            StringBuilder kept = new StringBuilder();
            for (int index = 0; index < brokenIndex; index++) {
                String line = lines.get(index);
                if (line.isBlank() && index == brokenIndex - 1) {
                    continue;
                }
                kept.append(line).append('\n');
            }
            Files.writeString(path, kept, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Оборванный хвост журнала '" + path + "' не отрезан: "
                    + error.getMessage(), error);
        }
    }

    private void createParent() {
        Path parent = path.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException error) {
            throw new IllegalStateException("Каталог журнала '" + parent + "' не создан: "
                    + error.getMessage(), error);
        }
    }

    public record LoadResult(int lines, int restored, int failedLine, String error, boolean truncated) {
        public LoadResult {
            if (lines < 0 || restored < 0) {
                throw new IllegalArgumentException("Счётчики загрузки не бывают отрицательными: lines="
                        + lines + ", restored=" + restored);
            }
        }

        public boolean failed() {
            return failedLine >= 0;
        }
    }
}
