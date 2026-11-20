package com.theminespirit.gametest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

final class SseCapture {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Frame(String id, String event, String data) {
        long idNumber() {
            return Long.parseLong(id);
        }

        JsonNode json() {
            try {
                return MAPPER.readTree(data);
            } catch (IOException e) {
                throw new IllegalStateException("Данные SSE-кадра не разобраны как JSON: " + data, e);
            }
        }
    }

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final AtomicReference<Stream<String>> body = new AtomicReference<>();
    final CompletableFuture<Integer> status = new CompletableFuture<>();
    final CompletableFuture<Void> finished = new CompletableFuture<>();
    private volatile String contentType;
    volatile CompletableFuture<?> connection = CompletableFuture.completedFuture(null);

    CompletableFuture<Integer> status() {
        return status;
    }

    boolean finished() {
        return finished.isDone() || connection.isCompletedExceptionally();
    }

    CompletableFuture<Void> whenFinished() {
        return finished;
    }

    String contentType() {
        return contentType;
    }

    List<String> lines() {
        return List.copyOf(lines);
    }

    List<Frame> frames() {
        List<Frame> frames = new ArrayList<>();
        String id = null;
        String event = null;
        String data = null;
        for (String line : lines()) {
            if (line.isEmpty()) {
                if (event != null) {
                    frames.add(new Frame(id, event, data));
                }
                id = null;
                event = null;
                data = null;
            } else if (line.startsWith("id: ")) {
                id = line.substring("id: ".length());
            } else if (line.startsWith("event: ")) {
                event = line.substring("event: ".length());
            } else if (line.startsWith("data: ")) {
                data = line.substring("data: ".length());
            }
        }
        return frames;
    }

    void cancel() {
        Optional.ofNullable(body.get()).ifPresent(Stream::close);
    }

    void read(HttpResponse<Stream<String>> response) {
        contentType = response.headers().firstValue("Content-Type").orElse("");
        status.complete(response.statusCode());
        try (Stream<String> stream = response.body()) {
            body.set(stream);
            stream.forEach(lines::add);
            finished.complete(null);
        } catch (RuntimeException e) {
            finished.completeExceptionally(e);
        }
    }
}
