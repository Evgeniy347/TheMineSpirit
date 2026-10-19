package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.task.TaskEvent;
import com.theminespirit.core.task.TaskEventLog;
import com.theminespirit.core.task.TaskRecord;
import com.theminespirit.core.task.TaskRegistry;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TaskEventStream implements SseStream {
    public static final Duration KEEPALIVE = Duration.ofSeconds(15);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TaskEventLog log;
    private final Duration keepalive;
    private final Runnable onClose;
    private final Deque<String> pending = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private long lastId;

    public TaskEventStream(TaskRegistry.Snapshot snapshot, Duration keepalive, Runnable onClose) {
        this.keepalive =
                Objects.requireNonNull(keepalive, "keepalive не может быть null: интервал кипалайва задают явно");
        this.onClose = Objects.requireNonNull(onClose, "onClose не может быть null: закрытие обязано освобождать слот");
        Objects.requireNonNull(
                snapshot, "snapshot не может быть null: первый кадр несёт согласованное состояние задачи");
        this.log = snapshot.log();
        long lastEventId = snapshot.lastEventId();
        long position = snapshot.position();
        TaskEventLog.Catchup catchup = snapshot.catchup();
        if (lastEventId > 0 && lastEventId <= position) {
            if (catchup.gap()) {
                long stateFrameId = catchup.events().isEmpty() ? position : lastEventId;
                pending.add(frame(stateFrameId, TaskEvent.TYPE_STATE, stateData(snapshot.record())));
            }
            this.lastId = lastEventId;
            for (TaskEvent event : catchup.events()) {
                pending.add(frame(event.id(), event.type(), event.data()));
                this.lastId = event.id();
            }
        } else {
            this.lastId = position;
            pending.add(frame(this.lastId, TaskEvent.TYPE_STATE, stateData(snapshot.record())));
        }
    }

    @Override
    public String nextFrame() throws InterruptedException {
        String queued = pending.pollFirst();
        if (queued != null) {
            return queued;
        }
        if (closed.get()) {
            return null;
        }
        TaskEventLog.ReadResult result = log.awaitAfter(lastId, keepalive);
        if (result.kind() == TaskEventLog.Kind.EVENT) {
            TaskEvent event = result.event();
            lastId = event.id();
            return frame(event.id(), event.type(), event.data());
        }
        if (result.kind() == TaskEventLog.Kind.TIMEOUT) {
            return SseFormat.keepalive();
        }
        return null;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            onClose.run();
        }
    }

    private static Map<String, Object> stateData(TaskRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_id", record.taskId());
        data.put("bot", record.bot());
        data.put("state", record.state().wire());
        data.put("goal", record.goal());
        data.put("progress", record.progress() == null ? null : progressData(record));
        data.put("result", record.result());
        data.put("error", record.error());
        return data;
    }

    private static Map<String, Object> progressData(TaskRecord record) {
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("step", record.progress().step());
        progress.put("total_steps", record.progress().totalSteps());
        progress.put("current_script", record.progress().currentScript());
        progress.put("done", record.progress().done());
        progress.put("target", record.progress().target());
        return progress;
    }

    private static String frame(long id, String type, Map<String, Object> data) {
        try {
            return SseFormat.frame(id, type, MAPPER.writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Данные события задачи не сериализуются в JSON SSE-кадра", e);
        }
    }
}
