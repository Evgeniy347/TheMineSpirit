package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
public record PlotLeveling(List<Box> cut, List<Box> fill, String block, List<Box> voids) {
    public static final int MAX_SHIFT = 10;
    public static final String DEFAULT_BLOCK = "dirt";
    // Допуск ровного участка: колонка на блок выше или ниже уровня — ровно, ступенька в блок проходима (INC-50).
    public static final int TOLERANCE = 1;
    // Пустоты под участком (пещеры, старые ходы) засыпаются на эту глубину под уровнем; глубже — не цель (INC-67).
    public static final int VOID_DEPTH = 4;
    public static final PlotLeveling FLAT = new PlotLeveling(List.of(), List.of(), DEFAULT_BLOCK);

    /** Воздух клетки мира: по нему находятся пустоты под участком. */
    @FunctionalInterface
    public interface AirProbe {
        boolean air(int x, int y, int z);
    }

    public PlotLeveling(List<Box> cut, List<Box> fill, String block) {
        this(cut, fill, block, List.of());
    }

    public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        public int volume() {
            return (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        }
    }

    public PlotLeveling {
        cut = List.copyOf(Objects.requireNonNull(cut, "Срезка не может быть null: ровный участок — пустой список"));
        fill = List.copyOf(Objects.requireNonNull(fill, "Подсыпка не может быть null: ровный участок — пустой список"));
        Objects.requireNonNull(block, "Материал подсыпки не может быть null: яму засыпают землёй или булыжником");
        voids = List.copyOf(Objects.requireNonNull(voids, "Пустоты не могут быть null: нет пустот — пустой список"));
    }

    public PlotLeveling withVoids(List<Box> found) {
        return new PlotLeveling(cut, fill, block, found);
    }

    public static boolean topLayer(int y, int level) {
        return y >= level - 1;
    }

    public static String fillBlock(int y, int level, String deep) {
        Objects.requireNonNull(deep, "Материал глубины не может быть null: подсыпка ставится землёй или булыжником");
        return topLayer(y, level) ? DEFAULT_BLOCK : deep;
    }

    // Пустоты под землёй участка на VOID_DEPTH под уровнем anchor.y(): воздух ниже поверхности колонки (feet).
    // Колонку, которую выравнивание не трогает (спуск в шахту), feet отдаёт Integer.MIN_VALUE.
    public static List<Box> voids(BlockCoord anchor, int half, IntBinaryOperator feet, AirProbe probe) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: пустоты ищутся под ним");
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: пустота — воздух под поверхностью");
        Objects.requireNonNull(probe, "Проба воздуха не может быть null: без неё пустот не найти");
        List<Box> found = new ArrayList<>();
        int bottom = anchor.y() - VOID_DEPTH;
        for (int x = anchor.x() - half; x <= anchor.x() + half; x++) {
            for (int z = anchor.z() - half; z <= anchor.z() + half; z++) {
                int surface = Math.min(feet.applyAsInt(x, z), anchor.y());
                Integer start = null;
                for (int y = bottom; y <= surface; y++) {
                    boolean hole = y < surface && probe.air(x, y, z);
                    if (hole && start == null) {
                        start = y;
                    } else if (!hole && start != null) {
                        found.add(new Box(x, start, z, x, y - 1, z));
                        start = null;
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    public int fillVolume() {
        return fill.stream().mapToInt(Box::volume).sum();
    }

    public PlotLeveling withBlock(String material) {
        return new PlotLeveling(cut, fill, material, voids);
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

    // Участок ровный, если каждая колонка в допуске TOLERANCE от уровня {@code anchor.y()}.
    public static boolean isLevel(BlockCoord anchor, int half, IntBinaryOperator feet) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: ровность читается вокруг него");
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: без них ровность не проверить");
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (Math.abs(feet.applyAsInt(anchor.x() + dx, anchor.z() + dz) - anchor.y()) > TOLERANCE) {
                    return false;
                }
            }
        }
        return true;
    }

    // Срезка с вершин слоями в блок: сначала верхний слой всего холма, потом следующий. Ящик колонкой на всю высоту
    // рыл траншею в 1 блок шириной и 4 глубиной: дух из неё не доставал соседний ряд, двое в ней не расходились
    // (сцена 56); срезанный ряд лишал стоянки старую стенку на вершине соседнего ряда. В слое ряды сливаются по x и z.
    public List<Box> cutTopDown() {
        int top = cut.stream().mapToInt(Box::y2).max().orElse(0);
        int bottom = cut.stream().mapToInt(Box::y1).min().orElse(1);
        List<Box> out = new ArrayList<>();
        for (int y = top; y >= bottom; y--) {
            int level = y;
            List<Box> rows = new ArrayList<>();
            cut.stream()
                    .filter(box -> box.y1() <= level && level <= box.y2())
                    .sorted(Comparator.comparingInt(Box::z1).thenComparingInt(Box::x1))
                    .forEach(box -> {
                        Box last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
                        if (last != null && last.z1() == box.z1() && last.x2() + 1 == box.x1()) {
                            rows.set(rows.size() - 1, new Box(last.x1(), level, last.z1(), box.x2(), level, last.z2()));
                        } else {
                            rows.add(new Box(box.x1(), level, box.z1(), box.x2(), level, box.z2()));
                        }
                    });
            rows.sort(Comparator.comparingInt(Box::x1).thenComparingInt(Box::x2).thenComparingInt(Box::z1));
            Box open = null;
            for (Box row : rows) {
                if (open != null && open.x1() == row.x1() && open.x2() == row.x2() && open.z2() + 1 == row.z1()) {
                    open = new Box(open.x1(), level, open.z1(), open.x2(), level, row.z2());
                    continue;
                }
                if (open != null) {
                    out.add(open);
                }
                open = row;
            }
            if (open != null) {
                out.add(open);
            }
        }
        return List.copyOf(out);
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
