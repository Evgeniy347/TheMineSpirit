package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Этапы стройки (решение владельца 20.10, QUESTIONS-2 п. 7): сначала черновые работы — пол, стены, перекрытия,
 * лестницы, крыша — строго полосами по {@value #BAND_HEIGHT} слоя снизу вверх: в следующую полосу дух не поднимается,
 * пока не застроена текущая, и лестница на этаж готова раньше самого этажа; затем отделка — мебель, свет, ковры,
 * цветы, двери и ставни — в любом порядке.
 */
public final class BuildPhase {
    public static final int BAND_HEIGHT = 4;
    private static final List<String> ROUGH_SUFFIXES = List.of("_stairs", "_slab", "_wall");
    private static final List<String> FINISH =
            List.of("chest", "trapped_chest", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "water", "lava");

    private BuildPhase() {}

    public static boolean rough(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестен этап стройки");
        String path = SchematicBlocks.path(blockId);
        // Перила балкона и галереи — черновые: без них дух выходит на открытый край балкона и застревает там.
        if (path.endsWith("_fence")) {
            return true;
        }
        if (SchematicBlocks.isEntranceBlock(blockId)
                || SchematicBlocks.isBed(blockId)
                || SchematicLevelRules.isDecorBlock(blockId)
                || SchematicLevelRules.isStation(blockId)
                || FINISH.contains(path)
                || path.endsWith("_sign")
                || path.endsWith("_banner")) {
            return false;
        }
        if (SchematicBlocks.isWindow(blockId)) {
            return true;
        }
        for (String suffix : ROUGH_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return SchematicBlocks.isFullBlock(blockId);
    }

    public static int band(int heightAboveBottom) {
        if (heightAboveBottom < 0) {
            throw new IllegalArgumentException(
                    "Высота " + heightAboveBottom + " ниже низа постройки: полоса считается от нижнего слоя схемы");
        }
        return heightAboveBottom / BAND_HEIGHT;
    }

    // Сейчас ставятся черновые клетки нижней незастроенной полосы; когда черновых не осталось — вся отделка.
    public static <T> List<T> current(List<T> unfinished, ToIntFunction<T> heightAboveBottom, Predicate<T> rough) {
        Objects.requireNonNull(unfinished, "Незаконченные клетки не могут быть null: пустой список задаётся пустым");
        Objects.requireNonNull(heightAboveBottom, "Высота клетки не может быть null: без неё нет полосы");
        Objects.requireNonNull(rough, "Признак черновой клетки не может быть null: без него нет этапа");
        int lowest = Integer.MAX_VALUE;
        for (T cell : unfinished) {
            if (rough.test(cell)) {
                lowest = Math.min(lowest, band(heightAboveBottom.applyAsInt(cell)));
            }
        }
        if (lowest == Integer.MAX_VALUE) {
            return List.copyOf(unfinished);
        }
        // Пол — первым, до стен нижней полосы: клетка пола, отложенная, пока на ней стоял дух, иначе оказывается
        // в чулане за уже поставленными стенами, и до неё не дотянуться (усадьба, особняк — 20.10).
        boolean floorLeft = false;
        for (T cell : unfinished) {
            floorLeft |= rough.test(cell) && heightAboveBottom.applyAsInt(cell) == 0;
        }
        List<T> now = new ArrayList<>();
        for (T cell : unfinished) {
            int height = heightAboveBottom.applyAsInt(cell);
            if (rough.test(cell) && band(height) == lowest && (!floorLeft || height == 0)) {
                now.add(cell);
            }
        }
        return List.copyOf(now);
    }
}
