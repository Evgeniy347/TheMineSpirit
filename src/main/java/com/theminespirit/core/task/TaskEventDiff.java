package com.theminespirit.core.task;

import com.theminespirit.core.engine.RunProgress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TaskEventDiff {
    private TaskEventDiff() {}

    public static List<TaskEvent> between(TaskRecord previous, TaskRecord current) {
        Objects.requireNonNull(previous, "previous не может быть null: дифф считают между двумя кадрами задачи");
        Objects.requireNonNull(current, "current не может быть null: дифф считают между двумя кадрами задачи");
        List<TaskEvent> events = new ArrayList<>(2);
        RunProgress now = current.progress();
        if (now != null && !now.equals(previous.progress())) {
            events.add(new TaskEvent(0, TaskEvent.TYPE_PROGRESS, progressData(now)));
        }
        if (current.state() == TaskState.FAILED && previous.state() != TaskState.FAILED) {
            Map<String, Object> data = new LinkedHashMap<>();
            RunProgress failedAt = current.progress() != null ? current.progress() : previous.progress();
            data.put("script", failedAt == null ? null : failedAt.currentScript());
            data.put("reason", current.error());
            events.add(new TaskEvent(0, TaskEvent.TYPE_SCRIPT_FAILED, data));
        }
        if (previous.state() != current.state()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("state", current.state().wire());
            if (current.state() == TaskState.SUCCEEDED) {
                data.put("result", current.result());
            }
            events.add(new TaskEvent(0, TaskEvent.TYPE_STATE, data));
        }
        return List.copyOf(events);
    }

    private static Map<String, Object> progressData(RunProgress progress) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", progress.step());
        data.put("total_steps", progress.totalSteps());
        data.put("current_script", progress.currentScript());
        data.put("done", progress.done());
        data.put("target", progress.target());
        return data;
    }
}
