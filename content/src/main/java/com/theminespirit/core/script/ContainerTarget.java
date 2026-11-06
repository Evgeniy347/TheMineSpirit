package com.theminespirit.core.script;

import java.util.Objects;

public sealed interface ContainerTarget
        permits ContainerTarget.Nearest,
                ContainerTarget.Position,
                ContainerTarget.Auto,
                ContainerTarget.Warehouse,
                ContainerTarget.Yard {
    ContainerTarget NEAREST = new Nearest();
    ContainerTarget AUTO = new Auto();
    ContainerTarget WAREHOUSE = new Warehouse();
    ContainerTarget YARD = new Yard();

    static ContainerTarget parse(String text) {
        Objects.requireNonNull(
                text,
                "Цель контейнера не может быть null: 'nearest', 'auto', 'warehouse', 'yard' " + "или координаты x,y,z");
        String trimmed = text.strip();
        if ("nearest".equalsIgnoreCase(trimmed)) {
            return NEAREST;
        }
        if ("auto".equalsIgnoreCase(trimmed)) {
            return AUTO;
        }
        if ("warehouse".equalsIgnoreCase(trimmed)) {
            return WAREHOUSE;
        }
        if ("yard".equalsIgnoreCase(trimmed)) {
            return YARD;
        }
        String[] parts = trimmed.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Контейнер '" + text + "' задан неверно: ожидалось 'nearest', "
                    + "'auto', 'warehouse', 'yard' или координаты блока x,y,z (например \"12,64,-7\"). Получено "
                    + parts.length + " части вместо 3");
        }
        return new Position(
                coordinate(parts[0].strip(), "x", text),
                coordinate(parts[1].strip(), "y", text),
                coordinate(parts[2].strip(), "z", text));
    }

    private static int coordinate(String text, String name, String origin) {
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Координата " + name + " контейнера '" + origin + "' не число: '" + text
                    + "'. Формат цели — 'nearest' или x,y,z (например \"12,64,-7\")");
        }
        if ("y".equals(name)) {
            if (value < CatalogLimits.WORLD_Y_MIN || value > CatalogLimits.WORLD_Y_MAX) {
                throw new IllegalArgumentException("Координата y контейнера '" + origin + "' равна " + value
                        + " и вне мира [" + CatalogLimits.WORLD_Y_MIN + ", " + CatalogLimits.WORLD_Y_MAX
                        + "]: в этом мире нет блока на такой высоте. Формат цели — 'nearest' или x,y,z "
                        + "(например \"12,64,-7\")");
            }
            return value;
        }
        if (value < -CatalogLimits.WORLD_HORIZONTAL_LIMIT || value > CatalogLimits.WORLD_HORIZONTAL_LIMIT) {
            throw new IllegalArgumentException("Координата " + name + " контейнера '" + origin + "' равна " + value
                    + " и вне мира [-" + CatalogLimits.WORLD_HORIZONTAL_LIMIT + ", "
                    + CatalogLimits.WORLD_HORIZONTAL_LIMIT + "]: границы мира не позволяют дойти до блока. "
                    + "Формат цели — 'nearest' или x,y,z (например \"12,64,-7\")");
        }
        return value;
    }

    record Nearest() implements ContainerTarget {}

    record Auto() implements ContainerTarget {}

    record Warehouse() implements ContainerTarget {}

    record Yard() implements ContainerTarget {}

    record Position(int x, int y, int z) implements ContainerTarget {}
}
