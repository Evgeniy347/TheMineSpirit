package com.theminespirit.core.ui;

import com.theminespirit.core.rest.BoardTask;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class BoardVersion {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private BoardVersion() {}

    public static long ofTasks(List<BoardTask> tasks) {
        Objects.requireNonNull(tasks, "tasks не может быть null: версия доски считается по её заданиям");
        long hash = FNV_OFFSET;
        for (BoardTask task : tasks) {
            hash = mix(hash, task.id());
            hash = mix(hash, task.boardCase());
            hash = mix(hash, task.status());
            hash = mix(hash, task.assignee() == null ? "-" : task.assignee());
            hash = mix(hash, Long.toString(task.createdAt()));
            hash = mix(hash, Boolean.toString(task.done()));
            hash = mix(hash, Integer.toString(task.level()));
            hash = mix(hash, Integer.toString(task.effectivePriority()));
            for (String dependency : task.dependsOn()) {
                hash = mix(hash, "d:" + dependency);
            }
            for (Map.Entry<String, Integer> entry : new TreeMap<>(task.missing()).entrySet()) {
                hash = mix(hash, "m:" + entry.getKey() + "=" + entry.getValue());
            }
        }
        return hash;
    }

    private static long mix(long hash, String value) {
        long next = hash;
        for (int index = 0; index < value.length(); index++) {
            next ^= value.charAt(index);
            next *= FNV_PRIME;
        }
        return (next ^ 0x1fL) * FNV_PRIME;
    }
}
