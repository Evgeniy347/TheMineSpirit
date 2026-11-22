package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunProgress;
import org.junit.jupiter.api.Test;

class TaskRegistryPart3Tests {
    private static final String BOT = "11111111-1111-1111-1111-111111111111";

    @Test
    void reserve_WithTitle_KeepsNameThroughProgressAndJson() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, "цель", "Поход за деревом");
        registry.observe(taskId, TaskState.RUNNING, new RunProgress(1, 2, "chop_tree", 1, 2), null);

        TaskRecord record = registry.get(taskId).orElseThrow();
        assertEquals(
                "Поход за деревом",
                record.title(),
                "Название обязано пережить обновление прогресса, иначе стратег ставит имя, а после первого "
                        + "шага пользователь видит задачу без подписи. При смене переноса title в "
                        + "TaskRegistry.observe проверь прогресс без смены состояния");
        assertTrue(
                TaskJson.toJson(record).contains("\"title\":\"Поход за деревом\""),
                "Название обязано попадать в JSON задачи: иначе клиент REST не покажет имя в списке задач, "
                        + "и стратег не найдёт поставленную задачу. При смене TaskJson проверь сериализацию "
                        + "непустого title");
    }

    @Test
    void reserve_WithoutTitle_LeavesNameNull() {
        TaskRegistry registry = new TaskRegistry();
        String taskId = registry.reserve(BOT, "цель");

        assertNull(
                registry.get(taskId).orElseThrow().title(),
                "Задача без названия обязана оставаться без title, иначе старая подпись кейса подменяется "
                        + "пустой строкой и плитка доски теряет смысл. При смене перегрузки reserve без "
                        + "title проверь значение по умолчанию");
    }
}
