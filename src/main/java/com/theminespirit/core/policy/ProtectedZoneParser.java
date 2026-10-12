package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.ProtectedZones.ProtectedZone;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ProtectedZoneParser {
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final int PARTS = 7;

    private ProtectedZoneParser() {
    }

    public static ProtectedZones parse(List<String> lines) {
        Objects.requireNonNull(lines, "Список строк защищённых зон не может быть null: пустой список задаётся List.of()");
        List<ProtectedZone> zones = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            zones.add(parseLine(index, lines.get(index)));
        }
        return ProtectedZones.of(zones);
    }

    private static ProtectedZone parseLine(int index, String line) {
        Objects.requireNonNull(line, "Строка защищённой зоны №" + (index + 1) + " пуста (null)");
        String[] parts = line.trim().split("\\s+");
        if (parts.length != PARTS) {
            throw bad(index, line, "ожидается 7 частей «измерение x1 y1 z1 x2 y2 z2», найдено " + parts.length);
        }
        if (!DIMENSION.matcher(parts[0]).matches()) {
            throw bad(index, line, "измерение '" + parts[0] + "' записано неверно, пример: minecraft:overworld");
        }
        int[] numbers = new int[6];
        for (int i = 0; i < numbers.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i + 1]);
            } catch (NumberFormatException error) {
                throw bad(index, line, "координата '" + parts[i + 1] + "' не целое число");
            }
        }
        BlockCoord low = new BlockCoord(Math.min(numbers[0], numbers[3]), Math.min(numbers[1], numbers[4]), Math.min(numbers[2], numbers[5]));
        BlockCoord high = new BlockCoord(Math.max(numbers[0], numbers[3]), Math.max(numbers[1], numbers[4]), Math.max(numbers[2], numbers[5]));
        return new ProtectedZone(parts[0], new BlockBox(low, high));
    }

    private static IllegalArgumentException bad(int index, String line, String problem) {
        return new IllegalArgumentException("protectedZones, строка №" + (index + 1) + " «" + line + "»: " + problem
                + ". Исправьте theminespirit-policy.toml: формат «minecraft:overworld x1 y1 z1 x2 y2 z2»");
    }
}
