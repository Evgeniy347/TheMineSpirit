package com.theminespirit.core.board;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

final class BoardJournalSearch {
    private BoardJournalSearch() {}

    static Optional<BoardLogEntry> findStatus(Path path, Deque<String> pending, String ticketId, int maxLines) {
        Objects.requireNonNull(path, "Путь журнала не может быть null: по нему идёт поиск задания");
        Objects.requireNonNull(pending, "Очередь журнала не может быть null: в ней записи текущего тика");
        Objects.requireNonNull(ticketId, "id задания не может быть null: журнал ищут по нему");
        if (maxLines < 1) {
            throw new IllegalArgumentException(
                    "Предел строк поиска " + maxLines + " меньше 1: " + "пустой ответ не отдаётся");
        }
        Optional<BoardLogEntry> queued = pendingStatus(pending, ticketId, maxLines);
        if (queued.isPresent()) {
            return queued;
        }
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
                ByteArrayOutputStream carry = new ByteArrayOutputStream()) {
            long position = file.length();
            byte[] chunk = new byte[64 * 1024];
            int scanned = 0;
            while (position > 0 && scanned < maxLines) {
                int size = (int) Math.min(chunk.length, position);
                position -= size;
                file.seek(position);
                file.readFully(chunk, 0, size);
                byte[] carryBytes = carry.toByteArray();
                byte[] all = new byte[size + carryBytes.length];
                System.arraycopy(chunk, 0, all, 0, size);
                System.arraycopy(carryBytes, 0, all, size, carryBytes.length);
                carry.reset();
                int end = all.length;
                for (int index = all.length - 1; index >= 0; index--) {
                    if (all[index] != '\n') {
                        continue;
                    }
                    if (end > index + 1) {
                        scanned++;
                        Optional<BoardLogEntry> found = status(all, index + 1, end, ticketId);
                        if (found.isPresent()) {
                            return found;
                        }
                        if (scanned >= maxLines) {
                            return Optional.empty();
                        }
                    }
                    end = index;
                }
                carry.write(all, 0, end);
            }
            byte[] first = carry.toByteArray();
            return first.length == 0 ? Optional.empty() : status(first, 0, first.length, ticketId);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Журнал заданий '" + path + "' не прочитан для поиска '" + ticketId + "': " + error.getMessage(),
                    error);
        }
    }

    private static Optional<BoardLogEntry> pendingStatus(Deque<String> pending, String ticketId, int maxLines) {
        Iterator<String> lines = pending.descendingIterator();
        int scanned = 0;
        while (lines.hasNext() && scanned < maxLines) {
            scanned++;
            byte[] line = lines.next().getBytes(StandardCharsets.UTF_8);
            Optional<BoardLogEntry> found = status(line, 0, line.length, ticketId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<BoardLogEntry> status(byte[] bytes, int from, int to, String ticketId) {
        String line = new String(bytes, from, to - from, StandardCharsets.UTF_8);
        if (!line.contains("\"" + ticketId + "\"")) {
            return Optional.empty();
        }
        try {
            BoardLogEntry entry = BoardLog.parse(line);
            if (entry.event() == BoardLog.Event.NOTE || !entry.ticketId().equals(ticketId)) {
                return Optional.empty();
            }
            return Optional.of(entry);
        } catch (RuntimeException broken) {
            return Optional.empty();
        }
    }
}
