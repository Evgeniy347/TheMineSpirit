package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CurrentTaskTests {
    @Test
    void of_RestTaskPresent_WinsOverBoardCard() {
        assertEquals(
                "task-1",
                CurrentTask.of("task-1", "activity:abc", "BUILD", "build_schematic"),
                "REST-задача остаётся главной в current_task: стратег ставил её адресно и ждёт её id");
    }

    @Test
    void of_ActivityCardWithoutRestTask_FillsCurrentTask() {
        assertEquals(
                "activity:f58b83b6",
                CurrentTask.of(null, "activity:f58b83b6", "SMELT", "smelt"),
                "Занятие духа заполняет current_task: в наблюдении 7 при карточке занятия поле было null, "
                        + "и занятость выглядела простой");
    }

    @Test
    void of_BoardTicketWithoutRestTask_FillsCurrentTask() {
        assertEquals(
                "first:build_orchard",
                CurrentTask.of(null, "first:build_orchard", "CHOP", "chop_tree"),
                "Задание доски заполняет current_task: по нему видно, какую работу доски держит дух");
    }

    @Test
    void of_OnlyActivity_FallsBackToActivityName() {
        assertEquals(
                "BUILD",
                CurrentTask.of(null, null, "BUILD", "goto"),
                "Без карточки current_task называет занятие: пустое поле при работе духа — потеря наблюдения");
    }

    @Test
    void of_OnlyScript_FallsBackToScript() {
        assertEquals(
                "goto",
                CurrentTask.of(null, null, null, "goto"),
                "Скрипт без занятия и задания тоже работа духа: current_task не остаётся null при busy");
    }

    @Test
    void of_NothingPresent_ReturnsNull() {
        assertNull(
                CurrentTask.of(null, null, null, null),
                "Без задачи, занятия и скрипта дух простаивает: null здесь честно показывает простой");
    }
}
