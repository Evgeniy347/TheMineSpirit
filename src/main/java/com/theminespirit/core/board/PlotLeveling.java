package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Выравнивание участка поселения (решение владельца 16, INC-36; 54b п.3): участок приводится к средней высоте —
 * выступ срезается, яма подсыпается, но не больше чем на {@link #MAX_SHIFT} блоков в колонке. Чистая функция по
 * высотам колонок: «высота» — уровень ног над землёй колонки. Средняя — медиана: постройки на уже выровненном
 * участке (забор, дом — меньше половины колонок) её не сдвигают, и якорь участка не плывёт после стройки.
 *
 * @param cut  ящики срезки (всё твёрдое выше уровня ног участка) — для {@code clear_area}
 * @param fill ящики подсыпки (воздух ниже уровня ног) — для {@code fill} материалом {@code block}
 */
public record PlotLeveling(List<Box> cut, List<Box> fill, String block) {
    public static final int MAX_SHIFT = 10;
    public static final String DEFAULT_BLOCK = "dirt";
    public static final PlotLeveling FLAT = new PlotLeveling(List.of(), List.of(), DEFAULT_BLOCK);

    public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        public int volume() {
            return (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        }
    }

    public PlotLeveling {
        cut = List.copyOf(Objects.requireNonNull(cut, "Срезка не может быть null: ровный участок — пустой список"));
        fill = List.copyOf(Objects.requireNonNull(fill, "Подсыпка не может быть null: ровный участок — пустой список"));
        Objects.requireNonNull(block, "Материал подсыпки не может быть null: яму засыпают землёй или булыжником");
    }

    public int fillVolume() {
        return fill.stream().mapToInt(Box::volume).sum();
    }

    public PlotLeveling withBlock(String material) {
        return new PlotLeveling(cut, fill, material);
    }

    // Средняя высота участка {@code half} вокруг якоря — медиана уровней ног колонок.
    public static int level(BlockCoord anchor, int half, IntBinaryOperator feet) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: высоты читаются вокруг него");
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: без них средней высоты нет");
        int side = 2 * half + 1;
        int[] heights = new int[side * side];
        int index = 0;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                heights[index++] = feet.applyAsInt(anchor.x() + dx, anchor.z() + dz);
            }
        }
        Arrays.sort(heights);
        return heights[heights.length / 2];
    }

    // План для участка {@code half} вокруг якоря; уровень ног участка — {@code anchor.y()}.
    public static PlotLeveling plan(BlockCoord anchor, int half, IntBinaryOperator feet) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него выравнивается участок");
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: без них нечего выравнивать");
        int level = anchor.y();
        List<Box> cut = new ArrayList<>();
        List<Box> fill = new ArrayList<>();
        for (int z = anchor.z() - half; z <= anchor.z() + half; z++) {
            @Nullable Run cutRun = null;
            @Nullable Run fillRun = null;
            for (int x = anchor.x() - half; x <= anchor.x() + half + 1; x++) {
                boolean inside = x <= anchor.x() + half;
                int height = inside ? feet.applyAsInt(x, z) : level;
                @Nullable
                Run cutHere = height > level ? new Run(x, level, Math.min(height, level + MAX_SHIFT) - 1) : null;
                @Nullable
                Run fillHere = height < level ? new Run(x, Math.max(height, level - MAX_SHIFT), level - 1) : null;
                cutRun = extend(cutRun, cutHere, x, z, cut);
                fillRun = extend(fillRun, fillHere, x, z, fill);
            }
        }
        return new PlotLeveling(cut, fill, DEFAULT_BLOCK);
    }

    // Колонки ряда с одинаковой срезкой (подсыпкой) сливаются в один ящик: меньше вызовов скрипта.
    private static @Nullable Run extend(@Nullable Run open, @Nullable Run here, int x, int z, List<Box> out) {
        if (open != null && here != null && open.y1() == here.y1() && open.y2() == here.y2()) {
            return open;
        }
        if (open != null) {
            out.add(new Box(open.x(), open.y1(), z, x - 1, open.y2(), z));
        }
        return here;
    }

    private record Run(int x, int y1, int y2) {}
}
