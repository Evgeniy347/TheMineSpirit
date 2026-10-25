package com.theminespirit.core.board;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BoardLogEntry(
        BoardLog.Event event,
        long tick,
        String ticketId,
        BoardCase boardCase,
        int level,
        String cause,
        int basePriority,
        int effectivePriority,
        List<String> modifiers,
        @Nullable String spirit,
        String configHash,
        long worldTime) {

    public BoardLogEntry {
        Objects.requireNonNull(
                event, "Событие журнала не может быть null: без него непонятно, " + "что случилось с заданием");
        if (tick < 0 || worldTime < 0) {
            throw new IllegalArgumentException("Тик " + tick + " и мировое время " + worldTime
                    + " не бывают отрицательными: журнал идёт вперёд, а не назад");
        }
        if (ticketId == null || ticketId.isBlank()) {
            throw new IllegalArgumentException(
                    "id задания в журнале пуст: по id запись связывают " + "с заданием на доске");
        }
        Objects.requireNonNull(
                boardCase, "Кейс журнала не может быть null: без него запись " + "не говорит, какое задание закрыто");
        if (level < 1) {
            throw new IllegalArgumentException(
                    "Уровень " + level + " меньше 1: первая постройка " + "ставится уровнем 1");
        }
        if (cause == null || cause.isBlank()) {
            throw new IllegalArgumentException("Причина в журнале пуста: по ней пересборка проверяет "
                    + "актуальность, пустая причина сделала бы запись бесполезной");
        }
        if (basePriority < 0 || basePriority > BoardTuning.MAX_PRIORITY) {
            throw new IllegalArgumentException("Базовый приоритет " + basePriority + " вне [0, "
                    + BoardTuning.MAX_PRIORITY + "]: журнал хранит число, а не произвольную строку");
        }
        if (effectivePriority < 0 || effectivePriority > BoardTuning.MAX_PRIORITY) {
            throw new IllegalArgumentException("Эффективный приоритет " + effectivePriority + " вне [0, "
                    + BoardTuning.MAX_PRIORITY + "]: журнал хранит число, а не произвольную строку");
        }
        modifiers = List.copyOf(Objects.requireNonNull(
                modifiers, "Модификаторы не могут быть null: пустой список задают пустым списком"));
        for (String modifier : modifiers) {
            if (modifier == null || modifier.isBlank()) {
                throw new IllegalArgumentException(
                        "Сработавший модификатор назван пустой строкой: " + "журнал называет ключи, а не пустоту");
            }
        }
        if (configHash == null || configHash.isBlank()) {
            throw new IllegalArgumentException("configHash обязателен: по нему отличают версию "
                    + "конфигурации, по которой задание жило; без неё запись несравнима со временем");
        }
    }
}
