package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Работа с места: что дух делает прямо сейчас, не сходя с места, и куда шагнуть, когда с места
 * делать нечего. Стоянка — ближайшая полезная: с неё дотягиваются хотя бы до половины лучшего охвата готовых клеток,
 * а среди таких выигрывает охват за вычетом штрафа за дальность: дух не бегает к каждому блоку и не скачет через дом. Общее правило досягаемости — {@link SpiritReach}.
 */
public final class ReachPlanner {
    public static final int BODY_HEIGHT = 2;
    static final int STAND_SCAN_BELOW = 6;
    // Решение владельца 21.10 «ближайшая полезная стоянка»: из стоянок, с которых ставится не меньше порога клеток
    // (доля лучшего покрытия, но не меньше минимума), берётся лучшая по счёту «клетки минус штраф за расстояние» —
    // дух идёт волной по слою, а не скачет через дом к стоянке с наибольшим покрытием. Замер по всем снапшотам планов
    // (4 поворота, 21.10): путь между стоянками 33751 → 28346 (−16 %), стоянок 4121 → 4112, заходов и «не поставлено»
    // без изменений; штраф 2 на блок уже даёт «не поставлено» 555, порог «половина лучшего» — лучше 30 % и 70 %.
    static final int USEFUL_MIN = 4;
    static final int USEFUL_PERCENT = 50;
    // Штраф в сотых клетки за блок расстояния: 70 — 0,7 клетки.
    static final int DISTANCE_PENALTY_HUNDREDTHS = 70;

    public interface World {
        boolean ready(BlockCoord cell);

        boolean standable(BlockCoord feet);
    }

    private ReachPlanner() {}

    public static List<BlockCoord> workableNow(
            SpiritReach reach, BlockCoord feet, List<BlockCoord> targets, World world) {
        Objects.requireNonNull(reach, "Досягаемость не может быть null: без неё неизвестно, до чего дотянуться");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, до чего дотягиваться");
        Objects.requireNonNull(targets, "Клетки не могут быть null: без них неизвестно, что делать");
        Objects.requireNonNull(world, "Мир не может быть null: без него неизвестно, что уже держится");
        List<BlockCoord> workable = new ArrayList<>();
        for (BlockCoord cell : targets) {
            if (reach.reaches(feet, cell) && !inBody(feet, cell) && world.ready(cell)) {
                workable.add(cell);
            }
        }
        return List.copyOf(workable);
    }

    public static Optional<BlockCoord> nextStand(
            SpiritReach reach,
            BlockCoord feet,
            List<BlockCoord> targets,
            World world,
            Set<BlockCoord> excluded,
            Set<BlockCoord> blocked) {
        return nextStand(reach, feet, targets, world, excluded, blocked, false);
    }

    // Последний шаг снаружи (двери, ставни второго этажа): стоянки только на земле, на 8–9 блоков ниже верхних
    // ставен. Внутри стройки дальний поиск оставлял духа на мебели, и заходы к сундукам не удавались (усадьба, 20.10).
    public static Optional<BlockCoord> nextStandFromAfar(
            SpiritReach reach,
            BlockCoord feet,
            List<BlockCoord> targets,
            World world,
            Set<BlockCoord> excluded,
            Set<BlockCoord> blocked) {
        return nextStand(reach, feet, targets, world, excluded, blocked, true);
    }

    private static Optional<BlockCoord> nextStand(
            SpiritReach reach,
            BlockCoord feet,
            List<BlockCoord> targets,
            World world,
            Set<BlockCoord> excluded,
            Set<BlockCoord> blocked,
            boolean fromAfar) {
        Objects.requireNonNull(reach, "Досягаемость не может быть null: без неё стоянку не выбрать");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, откуда шагать");
        Objects.requireNonNull(targets, "Клетки не могут быть null: без них неизвестно, к чему шагать");
        Objects.requireNonNull(world, "Мир не может быть null: без него неизвестно, где стоять");
        Objects.requireNonNull(excluded, "Исключённые стоянки не могут быть null: пустое множество задаётся пустым");
        Objects.requireNonNull(blocked, "Занятые схемой клетки не могут быть null: пустое множество задаётся пустым");
        List<BlockCoord> cover = cover(targets, world);
        if (cover.isEmpty()) {
            return Optional.empty();
        }
        Optional<BlockCoord> near =
                bestStand(reach, feet, cover, candidates(reach, cover, world, STAND_SCAN_BELOW), excluded, blocked);
        if (near.isPresent() || !fromAfar || reach.up() <= STAND_SCAN_BELOW) {
            return near;
        }
        return bestStand(reach, feet, cover, candidates(reach, cover, world, reach.up()), excluded, blocked);
    }

    private static Optional<BlockCoord> bestStand(
            SpiritReach reach,
            BlockCoord feet,
            List<BlockCoord> cover,
            List<BlockCoord> candidates,
            Set<BlockCoord> excluded,
            Set<BlockCoord> blocked) {
        List<BlockCoord> stands = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        int most = 0;
        for (BlockCoord stand : candidates) {
            if (stand.equals(feet)
                    || excluded.contains(stand)
                    || blocked.contains(stand)
                    || blocked.contains(new BlockCoord(stand.x(), stand.y() + 1, stand.z()))) {
                continue;
            }
            int count = covered(reach, stand, cover);
            if (count == 0) {
                continue;
            }
            stands.add(stand);
            counts.add(count);
            most = Math.max(most, count);
        }
        int threshold = Math.min(most, Math.max(USEFUL_MIN, (most * USEFUL_PERCENT + 99) / 100));
        BlockCoord best = null;
        long bestScore = Long.MIN_VALUE;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < stands.size(); i++) {
            int count = counts.get(i);
            if (count < threshold) {
                continue;
            }
            BlockCoord stand = stands.get(i);
            long distance = distanceSquared(feet, stand);
            long score = count * 100L - Math.round(DISTANCE_PENALTY_HUNDREDTHS * Math.sqrt((double) distance));
            if (best == null
                    || score > bestScore
                    || (score == bestScore && distance < bestDistance)
                    || (score == bestScore && distance == bestDistance && stand.compareTo(best) < 0)) {
                best = stand;
                bestScore = score;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static List<BlockCoord> cover(List<BlockCoord> targets, World world) {
        List<BlockCoord> ready = new ArrayList<>();
        int lowest = Integer.MAX_VALUE;
        for (BlockCoord cell : targets) {
            lowest = Math.min(lowest, cell.y());
            if (world.ready(cell)) {
                ready.add(cell);
            }
        }
        if (!ready.isEmpty()) {
            return ready;
        }
        List<BlockCoord> layer = new ArrayList<>();
        for (BlockCoord cell : targets) {
            if (cell.y() == lowest) {
                layer.add(cell);
            }
        }
        return layer;
    }

    private static List<BlockCoord> candidates(SpiritReach reach, List<BlockCoord> cover, World world, int scanBelow) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        Set<Long> columns = new LinkedHashSet<>();
        List<int[]> columnList = new ArrayList<>();
        for (BlockCoord cell : cover) {
            minY = Math.min(minY, cell.y());
            maxY = Math.max(maxY, cell.y());
            for (int dx = -reach.horizontal(); dx <= reach.horizontal(); dx++) {
                for (int dz = -reach.horizontal(); dz <= reach.horizontal(); dz++) {
                    int x = cell.x() + dx;
                    int z = cell.z() + dz;
                    if (columns.add(((long) x << 32) ^ (z & 0xffffffffL))) {
                        columnList.add(new int[] {x, z});
                    }
                }
            }
        }
        int top = maxY + reach.down();
        int bottom = minY - Math.min(reach.up(), scanBelow);
        List<BlockCoord> stands = new ArrayList<>();
        for (int[] column : columnList) {
            for (int y = top; y >= bottom; y--) {
                BlockCoord stand = new BlockCoord(column[0], y, column[1]);
                if (world.standable(stand)) {
                    stands.add(stand);
                }
            }
        }
        return stands;
    }

    private static int covered(SpiritReach reach, BlockCoord stand, List<BlockCoord> cover) {
        int count = 0;
        for (BlockCoord cell : cover) {
            if (reach.reaches(stand, cell) && !SpiritReach.forbiddenStand(stand, cell) && !inBody(stand, cell)) {
                count++;
            }
        }
        return count;
    }

    public static boolean inBody(BlockCoord feet, BlockCoord cell) {
        return cell.x() == feet.x()
                && cell.z() == feet.z()
                && cell.y() >= feet.y()
                && cell.y() < feet.y() + BODY_HEIGHT;
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
