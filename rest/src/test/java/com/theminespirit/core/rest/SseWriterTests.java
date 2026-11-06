package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class SseWriterTests {
    private static final class ScriptedStream implements SseStream {
        private final Deque<String> frames;
        private final boolean interruptAtEnd;
        private int closes;

        ScriptedStream(List<String> frames, boolean interruptAtEnd) {
            this.frames = new ArrayDeque<>(frames);
            this.interruptAtEnd = interruptAtEnd;
        }

        @Override
        public String nextFrame() throws InterruptedException {
            if (frames.isEmpty() && interruptAtEnd) {
                throw new InterruptedException("сервер останавливается");
            }
            return frames.pollFirst();
        }

        @Override
        public void close() {
            closes++;
        }
    }

    private static final class RecordingOutput extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int failAfterWrites;
        private int writes;
        private int flushes;

        RecordingOutput(int failAfterWrites) {
            this.failAfterWrites = failAfterWrites;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            if (writes >= failAfterWrites) {
                throw new IOException("Broken pipe");
            }
            writes++;
            bytes.write(data, offset, length);
        }

        @Override
        public void flush() {
            flushes++;
        }
    }

    @Test
    void pump_FramesUntilNull_WritesEachFlushedInUtf8AndClosesOnce() {
        ScriptedStream stream = new ScriptedStream(
                List.of("id: 1\nevent: state\ndata: {\"s\":\"запущена\"}\n\n", ": keepalive\n\n"), false);
        RecordingOutput out = new RecordingOutput(Integer.MAX_VALUE);

        SseWriter.Outcome outcome = SseWriter.pump(stream, out);

        assertEquals(SseWriter.Outcome.FINISHED, outcome, "Поток, отдавший null, обязан завершаться штатно");
        assertEquals(
                "id: 1\nevent: state\ndata: {\"s\":\"запущена\"}\n\n: keepalive\n\n",
                out.bytes.toString(StandardCharsets.UTF_8),
                "Кадры обязаны уходить по порядку в UTF-8");
        assertEquals(2, out.flushes, "Каждый кадр обязан сбрасываться сразу, иначе клиент увидит событие с задержкой");
        assertEquals(1, stream.closes, "Поток обязан закрыться ровно один раз: закрытие освобождает слот SSE");
    }

    @Test
    void pump_ClientDisconnectsOnWrite_ReturnsClientGoneAndStillClosesStream() {
        ScriptedStream stream = new ScriptedStream(List.of("data: 1\n\n", "data: 2\n\n", "data: 3\n\n"), false);
        RecordingOutput out = new RecordingOutput(1);

        SseWriter.Outcome outcome = SseWriter.pump(stream, out);

        assertEquals(SseWriter.Outcome.CLIENT_GONE, outcome, "Обрыв записи не ошибка моста: клиент ушёл");
        assertEquals(
                1,
                stream.closes,
                "При IOException поток обязан закрыться и освободить слот: иначе 4 оборванных клиента навсегда "
                        + "заблокируют SSE");
    }

    @Test
    void pump_InterruptedWhileWaiting_ReturnsInterruptedKeepsFlagAndClosesStream() {
        ScriptedStream stream = new ScriptedStream(List.of("data: 1\n\n"), true);
        RecordingOutput out = new RecordingOutput(Integer.MAX_VALUE);

        SseWriter.Outcome outcome = SseWriter.pump(stream, out);

        assertEquals(SseWriter.Outcome.INTERRUPTED, outcome, "Остановка сервера прерывает ожидание кадра");
        assertTrue(Thread.interrupted(), "Флаг прерывания обязан сохраниться для пула потоков моста");
        assertEquals(1, stream.closes, "Прерванный поток обязан освободить слот");
    }
}
