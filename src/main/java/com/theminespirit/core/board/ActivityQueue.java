package com.theminespirit.core.board;

import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ActivityQueue {
    public record Request(String key, BoardCase activity, String script, TaskNode task, int priority, long expiresAt) {
        public Request {
            Objects.requireNonNull(key, "Ключ занятия не может быть null: по нему убирают повтор");
            Objects.requireNonNull(activity, "Кейс занятия не может быть null: духу нужно конкретное занятие");
            Objects.requireNonNull(script, "Скрипт занятия не может быть null: по нему стартует карточка");
            Objects.requireNonNull(task, "План занятия не может быть null: духу нечего исполнять");
            if (priority < 0) {
                throw new IllegalArgumentException("Вес занятия " + priority + " отрицателен: порядок очереди сломан");
            }
        }
    }

    private final Map<String, Request> pending = new LinkedHashMap<>();

    public boolean offer(
            String key, BoardCase activity, String script, TaskNode task, int priority, long now, long ttl) {
        Objects.requireNonNull(key, "Ключ занятия не может быть null: без него очередь не отличит повтор");
        if (ttl < 1) {
            throw new IllegalArgumentException("Срок ожидания занятия " + ttl + ": он обязан быть не меньше тика");
        }
        Request request = new Request(key, activity, script, task, priority, now + ttl);
        boolean fresh = !pending.containsKey(key);
        pending.put(key, request);
        return fresh;
    }

    public List<Request> due(long now) {
        pending.values().removeIf(request -> now >= request.expiresAt());
        List<Request> ready = new ArrayList<>(pending.values());
        ready.sort(Comparator.comparingInt(Request::priority).reversed().thenComparing(Request::key));
        return List.copyOf(ready);
    }

    public void remove(String key) {
        pending.remove(key);
    }

    public int size() {
        return pending.size();
    }

    public boolean contains(String key) {
        return pending.containsKey(key);
    }
}
