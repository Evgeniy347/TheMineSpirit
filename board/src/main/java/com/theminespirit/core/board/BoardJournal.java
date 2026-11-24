package com.theminespirit.core.board;

import com.theminespirit.core.board.api.BoardCase;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final int NEWER_SCAN_LINES = 4096;

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
        int restored = 0;
        int total = 0;
        int lastLine = -1;
        String error = "";
        boolean truncated = false;
        ledger.forgetHistory();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                total++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    Optional<BoardLogEntry> compatible = BoardLog.parseCompatible(line, total);
                    if (compatible.isEmpty()) {
                        continue;
                    }
                    BoardLogEntry entry = compatible.get();
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
                    lastLine = total;
                    error = failure.getMessage();
                    reader.mark(1);
                    truncated = reader.read() == -1;
                    if (truncated) {
                        truncateAfter(total - 1);
                    }
                    break;
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Журнал заданий '" + path + "' не прочитан: " + failure.getMessage(), failure);
        }
        return new LoadResult(total, restored, lastLine, error, truncated);
    }

    public List<BoardLogEntry> last(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit журнала " + limit + " меньше 1: " + "пустой ответ не отдаётся");
        }
        if (!Files.exists(path)) {
            return List.of();
        }
        List<String> lines = BoardJournalSearch.tailLines(path, limit * 2);
        List<BoardLogEntry> entries = new ArrayList<>(limit);
        for (int index = lines.size() - 1; index >= 0 && entries.size() < limit; index--) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            Optional<BoardLogEntry> parsed = BoardLog.parseCompatible(line, index + 1);
            if (parsed.isEmpty()) {
                continue;
            }
            entries.add(parsed.get());
        }
        return List.copyOf(entries);
    }

    public List<BoardLogEntry> newer(int limit, long sinceTick) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit журнала " + limit + " меньше 1: пустой ответ не отдаётся");
        }
        if (sinceTick < 0) {
            throw new IllegalArgumentException("sinceTick " + sinceTick + " отрицателен: время идёт вперёд");
        }
        if (!Files.exists(path)) {
            return List.of();
        }
        List<String> lines = BoardJournalSearch.tailLines(path, Math.min(NEWER_SCAN_LINES, Math.max(512, limit * 8)));
        List<BoardLogEntry> entries = new ArrayList<>(limit);
        for (int index = lines.size() - 1; index >= 0 && entries.size() < limit; index--) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            Optional<BoardLogEntry> parsed = BoardLog.parseCompatible(line, index + 1);
            if (parsed.isEmpty()) {
                continue;
            }
            BoardLogEntry entry = parsed.get();
            if (entry.tick() <= sinceTick) {
                break;
            }
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    public Optional<BoardLogEntry> findStatus(String ticketId, int maxLines) {
        return BoardJournalSearch.findStatus(path, pending, ticketId, maxLines);
    }

    private void truncateAfter(int brokenIndex) {
        Path kept = path.resolveSibling(path.getFileName() + ".truncated");
        try {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                    Writer writer = Files.newBufferedWriter(
                            kept,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                String line;
                int index = 0;
                while (index < brokenIndex && (line = reader.readLine()) != null) {
                    boolean last = index == brokenIndex - 1;
                    if (!(last && line.isBlank())) {
                        writer.write(line);
                        writer.write('\n');
                    }
                    index++;
                }
            }
            Files.move(kept, path, StandardCopyOption.REPLACE_EXISTING);
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
