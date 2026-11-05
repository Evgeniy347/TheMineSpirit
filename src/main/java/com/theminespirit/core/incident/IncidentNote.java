package com.theminespirit.core.incident;

import com.theminespirit.core.observation.BlockCoord;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record IncidentNote(String spirit, String what, BlockCoord at, String task, Map<String, Integer> inventory) {
    public static final int RADIUS = 12;
    public static final int SIZE = 2 * RADIUS + 1;
    public static final String FOLDER = "theminespirit/incidents";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    public IncidentNote {
        Objects.requireNonNull(spirit, "Имя духа инцидента не может быть null: без него не найти, с кем было");
        Objects.requireNonNull(what, "Что случилось, не может быть null: инцидент без причины не повторить");
        Objects.requireNonNull(at, "Позиция духа не может быть null: от неё снимается окрестность");
        Objects.requireNonNull(task, "Задача не может быть null: без задачи пишется «-»");
        inventory = Map.copyOf(inventory);
    }

    public static String fileName(Instant time, String spirit) {
        Objects.requireNonNull(time, "Время инцидента не может быть null: по нему файлы снимков не путаются");
        return STAMP.format(time) + "-" + spirit.replaceAll("[^A-Za-z0-9_-]", "_") + ".nbt";
    }

    public BlockCoord origin() {
        return new BlockCoord(at.x() - RADIUS, at.y() - RADIUS, at.z() - RADIUS);
    }

    public static BlockCoord spiritOffset() {
        return new BlockCoord(RADIUS, RADIUS, RADIUS);
    }

    public String line() {
        return head() + what + "; задача " + task + "; инвентарь " + new TreeMap<>(inventory);
    }

    public String line(Path snapshot) {
        Objects.requireNonNull(snapshot, "Путь снимка не может быть null: инцидент без него не восстановить");
        return line() + "; снимок " + snapshot;
    }

    public String brief() {
        return head() + "задача " + task + "; инвентарь " + new TreeMap<>(inventory);
    }

    public String brief(Path snapshot) {
        Objects.requireNonNull(snapshot, "Путь снимка не может быть null: инцидент без него не восстановить");
        return brief() + "; снимок " + snapshot;
    }

    private String head() {
        return "дух " + spirit + " в (" + at.x() + ", " + at.y() + ", " + at.z() + "): ";
    }
}
