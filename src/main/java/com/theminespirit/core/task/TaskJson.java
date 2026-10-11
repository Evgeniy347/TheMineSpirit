package com.theminespirit.core.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.engine.RunProgress;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TaskJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TaskJson() {
    }

    public static String toJson(TaskRecord task) {
        try {
            return MAPPER.writeValueAsString(toMap(task));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать задачу в JSON: проверьте типы полей записи", e);
        }
    }

    public static Map<String, Object> toMap(TaskRecord task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("task_id", task.taskId());
        map.put("bot", task.bot());
        map.put("state", task.state().wire());
        map.put("goal", task.goal());
        map.put("progress", task.progress() == null ? null : progressMap(task.progress()));
        map.put("result", task.result());
        map.put("error", task.error());
        return map;
    }

    static Map<String, Object> progressMap(RunProgress progress) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("step", progress.step());
        map.put("total_steps", progress.totalSteps());
        map.put("current_script", progress.currentScript());
        map.put("done", progress.done());
        map.put("target", progress.target());
        return map;
    }
}
