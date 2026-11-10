package com.theminespirit.core.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventLogTests {
    @TempDir
    Path directory;

    @Test
    void append_BlockedWriter_DoesNotBlockCaller() throws Exception {
        BlockingSink sink = new BlockingSink();
        try (EventLog log = new EventLog(sink, "1.0", "live", 2)) {
            assertTimeoutPreemptively(
                    Duration.ofMillis(700),
                    () -> {
                        for (int index = 0; index < 1_000; index++) {
                            log.append(validBlock(index));
                        }
                    },
                    "Поток сервера не должен ждать записи файла: переполнение очереди считается потерями, а тик идёт");

            sink.gate.countDown();
        }
    }

    @Test
    void close_DrainsQueue_WritesEveryRecord() {
        BlockingSink sink = new BlockingSink(0);
        try (EventLog log = new EventLog(sink, "1.0", "live", 1024)) {
            for (int index = 0; index < 50; index++) {
                log.append(validBlock(index));
            }
        }

        assertEquals(50, sink.records().size(), "Закрытие обязано дописать очередь: иначе хвост событий теряется");
    }

    @Test
    void overflow_WhenWriterBlocked_ReportsLostCountAsMessage() {
        BlockingSink sink = new BlockingSink(1);
        try (EventLog log = new EventLog(sink, "1.0", "live", 1)) {
            for (int index = 0; index < 10; index++) {
                log.append(validBlock(index));
            }

            sink.gate.countDown();
        }

        boolean reported = sink.records().stream()
                .anyMatch(record -> record.toJson().contains("\"type\":\"message\"")
                        && record.toJson().contains("\"tag\":\"logqueue\""));
        assertTrue(
                reported,
                "Переполнение очереди обязано оставить отдельное событие: иначе потеря строк не видна в базе");
    }

    @Test
    void jsonlSink_SmallLimit_RotatesFiles() throws Exception {
        try (JsonlSink sink = new JsonlSink(directory, 250)) {
            for (int index = 0; index < 20; index++) {
                sink.write(validBlock(index));
            }
        }

        List<Path> files;
        try (Stream<Path> list = Files.list(directory)) {
            files = list.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .toList();
        }
        assertTrue(
                files.size() > 1,
                "Ротация по размеру обязана создавать новые файлы: иначе один JSONL растёт без предела");
        for (Path file : files) {
            assertTrue(
                    Files.readString(file, StandardCharsets.UTF_8).contains("\"type\""),
                    "Каждый файл ротации обязан содержать JSON-записи с полем type");
        }
    }

    @Test
    void jsonlSink_Reopen_ContinuesSequence() throws Exception {
        try (JsonlSink first = new JsonlSink(directory, 10_000)) {
            first.write(validBlock(1));
        }
        try (JsonlSink second = new JsonlSink(directory, 10_000)) {
            second.write(validBlock(2));
        }

        try (Stream<Path> list = Files.list(directory)) {
            assertEquals(
                    2,
                    list.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                            .count(),
                    "Новый запуск обязан продолжить нумерацию файлов, а не перезаписать прошлый");
        }
    }

    private static LogRecord validBlock(int index) {
        return LogRecord.builder(LogType.BLOCK_CHANGE, 1_700_000_000_000L + index, index, "1.0", "live")
                .put("actor_type", "spirit")
                .put("actor", "дух-1")
                .put("action", "break")
                .put("block_before", "Block{minecraft:stone}")
                .put("block_after", "Block{minecraft:air}")
                .at(index, 64, index)
                .cause(LogCause.BREAK)
                .build();
    }

    private static final class BlockingSink implements EventLog.Sink {
        private final CountDownLatch gate = new CountDownLatch(1);
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        private BlockingSink() {
            this(1);
        }

        private BlockingSink(long gateCount) {
            if (gateCount == 0) {
                gate.countDown();
            }
        }

        @Override
        public void write(LogRecord record) throws IOException {
            try {
                gate.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Поток записи прерван", error);
            }
            records.add(record);
        }

        private List<LogRecord> records() {
            return records;
        }

        @Override
        public void close() {}
    }
}
