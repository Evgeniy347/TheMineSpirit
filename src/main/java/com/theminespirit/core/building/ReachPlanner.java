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
 * делать нечего. Стоянка выбирается так, чтобы с неё дотянуться до наибольшего числа готовых клеток: дух не бегает
 * к каждому блоку, а обходит постройку несколькими стоянками. Общее правило досягаемости — {@link SpiritReach}.
 */
public final class ReachPlanner {
    public static final int BODY_HEIGHT = 2;
    static final int STAND_SCAN_BELOW = 6;

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
        BlockCoord best = null;
        int bestCount = 0;
        long bestDistance = Long.MAX_VALUE;
        for (BlockCoord stand : candidates(reach, cover, world)) {
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
            long distance = distanceSquared(feet, stand);
            if (best == null
                    || count > bestCount
                    || (count == bestCount && distance < bestDistance)
                    || (count == bestCount && distance == bestDistance && stand.compareTo(best) < 0)) {
                best = stand;
                bestCount = count;
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

    private static List<BlockCoord> candidates(SpiritReach reach, List<BlockCoord> cover, World world) {
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
        int bottom = minY - Math.min(reach.up(), STAND_SCAN_BELOW);
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
