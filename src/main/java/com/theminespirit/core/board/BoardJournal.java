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
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class BoardJournal {
    public static final int FIND_LINES = 65_536;
    private static final int TAIL_CHUNK = 64 * 1024;

    private final Path path;
    private final Deque<String> pending = new ArrayDeque<>();
    private int enqueued;
    private int written;

    public BoardJournal(Path path) {
        this.path = Objects.requireNonNull(path, "Путь журнала не может быть null: без него запись некуда класть");
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

    public int enqueued() {
        return enqueued;
    }

    public int written() {
        return written;
    }

    public int flush(int maxLines) {
        if (pending.isEmpty()) {
            return 0;
        }
        if (maxLines < 1) {
            throw new IllegalArgumentException(
                    "Бюджет сброса " + maxLines + " меньше 1: " + "журнал не продвинется и память будет расти");
        }
        createParent();
        int flushed = 0;
        try {
            StringBuilder block = new StringBuilder();
            while (flushed < maxLines && !pending.isEmpty()) {
                block.append(pending.removeFirst()).append('\n');
                flushed++;
            }
            Files.writeString(
                    path, block, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Журнал заданий не записан в '" + path + "': " + error.getMessage(), error);
        }
        written += flushed;
        return flushed;
    }

    public int flushAll() {
        return flush(pending.size());
    }

    public LoadResult load(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Ledger не может быть null: восстанавливать некуда");
        if (!Files.exists(path)) {
            return new LoadResult(0, 0, -1, "", false);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Журнал заданий '" + path + "' не прочитан: " + error.getMessage(), error);
        }
        int restored = 0;
        int lastLine = -1;
        String error = "";
        boolean truncated = false;
        ledger.forgetHistory();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            try {
                BoardLogEntry entry = BoardLog.parse(line, index + 1);
                if (entry.event() == BoardLog.Event.DONE) {
                    ledger.complete(entry.boardCase(), entry.level());
                    if (entry.boardCase() == BoardCase.BUILD_PLACED) {
                        StageCatalog.planId(entry.ticketId()).ifPresent(id -> ledger.notePlan(id, entry.level()));
                    }
                    restored++;
                } else if (entry.event() == BoardLog.Event.REOPENED) {
                    ledger.reopen(entry.boardCase(), entry.level());
                    if (entry.boardCase() == BoardCase.BUILD_PLACED) {
                        StageCatalog.planId(entry.ticketId()).ifPresent(id -> ledger.forgetPlan(id, entry.level()));
                    }
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

    public List<BoardLogEntry> last(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit журнала " + limit + " меньше 1: " + "пустой ответ не отдаётся");
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

    public Optional<BoardLogEntry> findStatus(String ticketId, int maxLines) {
        return BoardJournalSearch.findStatus(path, pending, ticketId, maxLines);
    }

    private List<String> tailLines(int wanted) {
        if (wanted < 1) {
            throw new IllegalArgumentException("Хвост журнала " + wanted + " меньше 1 строки: нечего читать");
        }
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long length = file.length();
            if (length == 0) {
                return List.of();
            }
            long position = length;
            int newlines = 0;
            List<byte[]> chunks = new ArrayList<>();
            while (position > 0 && newlines <= wanted) {
                int read = (int) Math.min(TAIL_CHUNK, position);
                position -= read;
                byte[] chunk = new byte[read];
                file.seek(position);
                file.readFully(chunk);
                chunks.add(chunk);
                for (byte value : chunk) {
                    if (value == '\n') {
                        newlines++;
                    }
                }
            }
            String text = new String(joinReverse(chunks), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (String raw : text.split("\n", -1)) {
                String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            int from = Math.max(0, lines.size() - wanted);
            return List.copyOf(lines.subList(from, lines.size()));
        } catch (IOException error) {
            throw new IllegalStateException("Хвост журнала '" + path + "' не прочитан: " + error.getMessage(), error);
        }
    }

    private static byte[] joinReverse(List<byte[]> chunks) {
        int size = 0;
        for (byte[] chunk : chunks) {
            size += chunk.length;
        }
        byte[] joined = new byte[size];
        int offset = 0;
        for (int index = chunks.size() - 1; index >= 0; index--) {
            byte[] chunk = chunks.get(index);
            System.arraycopy(chunk, 0, joined, offset, chunk.length);
            offset += chunk.length;
        }
        return joined;
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
            throw new IllegalStateException(
                    "Оборванный хвост журнала '" + path + "' не отрезан: " + error.getMessage(), error);
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
            throw new IllegalStateException("Каталог журнала '" + parent + "' не создан: " + error.getMessage(), error);
        }
    }

    public record LoadResult(
            int lines,
            int restored,
            int failedLine,
            @Nullable String error,
            boolean truncated) {
        public LoadResult {
            if (lines < 0 || restored < 0) {
                throw new IllegalArgumentException(
                        "Счётчики загрузки не бывают отрицательными: lines=" + lines + ", restored=" + restored);
            }
        }

        public boolean failed() {
            return failedLine >= 0;
        }
    }
}
