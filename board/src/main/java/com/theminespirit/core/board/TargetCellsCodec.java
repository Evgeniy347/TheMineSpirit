package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TargetCellsCodec {
    private static final String CELLS = "Cells";

    private TargetCellsCodec() {}

    static void put(Map<String, Object> map, List<BlockCoord> cells) {
        if (cells.isEmpty()) {
            return;
        }
        List<Integer> flat = new ArrayList<>(cells.size() * 3);
        for (BlockCoord cell : cells) {
            flat.add(cell.x());
            flat.add(cell.y());
            flat.add(cell.z());
        }
        map.put(CELLS, List.copyOf(flat));
    }

    static List<BlockCoord> decode(Map<String, Object> map) {
        Object value = map.get(CELLS);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> flat)) {
            throw new IllegalArgumentException(
                    "Запись " + CELLS + " не список: " + value.getClass().getSimpleName() + ", проверьте NBT доски");
        }
        if (flat.size() % 3 != 0) {
            throw new IllegalArgumentException(
                    "Запись " + CELLS + " длины " + flat.size() + ": клетка ограды это 3 числа, проверьте NBT доски");
        }
        List<BlockCoord> cells = new ArrayList<>(flat.size() / 3);
        for (int index = 0; index < flat.size(); index += 3) {
            cells.add(
                    new BlockCoord(number(flat.get(index)), number(flat.get(index + 1)), number(flat.get(index + 2))));
        }
        return List.copyOf(cells);
    }

    private static int number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Запись " + CELLS + " содержит не число: " + value + ", проверьте NBT доски");
        }
        return number.intValue();
    }
}
