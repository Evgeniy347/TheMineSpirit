package com.theminespirit.core.husbandry;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Стоянки для блока кольца: клетки, где дух может стоять и откуда блок в досягаемости {@link SpiritReach}.
 * Первыми идут стоянки, откуда достаётся больше всего недостроенных блоков, из равных — ближние к духу: досягаемость
 * короче кольца, и со стоянки у ближней стены дальняя не достаётся — дух сразу идёт туда, откуда ставит кольцо
 * подряд, а попутные блоки ставит на ходу. Стоянка отстоит от края досягаемости
 * на {@link #ARRIVAL_MARGIN} клетку: подход считается законченным, не дойдя до центра стоянки, и дух не должен
 * остаться за краем. Тело духа (ноги и голова) не занимает ни блок кольца, ни внутренность: иначе он загородил бы
 * стенку собой или запер бы себя с животным. На стенку дух не встаёт: без лестницы на неё не забраться.
 */
public final class RingStands {
    public static final int ARRIVAL_MARGIN = 1;
    private static final int SCAN_BELOW = 3;
    private static final int SIDE = SpiritReach.HORIZONTAL - ARRIVAL_MARGIN;

    private RingStands() {
    }

    public static List<BlockCoord> of(BlockCoord block, RingPlan.Ring ring, Collection<BlockCoord> remaining,
            SafetyView view, BlockCoord from) {
        Objects.requireNonNull(block, "Блок кольца не может быть null: без него неизвестно, к чему подходить");
        Objects.requireNonNull(ring, "Кольцо не может быть null: без него неизвестно, где стоять нельзя");
        Objects.requireNonNull(remaining, "Остаток кольца не может быть null: пустой задают пустым списком");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется стоянка");
        Objects.requireNonNull(from, "Точка отсчёта не может быть null: по ней сортируются стоянки");
        Set<BlockCoord> body = new HashSet<>(ring.blocks());
        List<BlockCoord> stands = new ArrayList<>();
        for (int dx = -SIDE; dx <= SIDE; dx++) {
            for (int dz = -SIDE; dz <= SIDE; dz++) {
                if (ring.interior().contains(new RingGround.Cell(block.x() + dx, block.z() + dz))) {
                    continue;
                }
                for (int dy = SpiritReach.DOWN - ARRIVAL_MARGIN; dy >= -SCAN_BELOW; dy--) {
                    BlockCoord stand = new BlockCoord(block.x() + dx, block.y() + dy, block.z() + dz);
                    if (fits(stand, block, body, view)) {
                        stands.add(stand);
                    }
                }
            }
        }
        Map<BlockCoord, Integer> coverage = new HashMap<>();
        for (BlockCoord stand : stands) {
            coverage.put(stand, coverage(stand, remaining));
        }
        Comparator<BlockCoord> widest = Comparator.comparingInt(stand -> -coverage.get(stand));
        stands.sort(widest.thenComparingLong(stand -> distanceSquared(from, stand))
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(stands);
    }

    public static int coverage(BlockCoord stand, Collection<BlockCoord> remaining) {
        int reached = 0;
        for (BlockCoord block : remaining) {
            if (SpiritReach.reachable(stand, block) && !SpiritReach.forbiddenStand(stand, block)) {
                reached++;
            }
        }
        return reached;
    }

    private static boolean fits(BlockCoord stand, BlockCoord block, Set<BlockCoord> body, SafetyView view) {
        BlockCoord head = new BlockCoord(stand.x(), stand.y() + 1, stand.z());
        BlockCoord floor = new BlockCoord(stand.x(), stand.y() - 1, stand.z());
        return SpiritReach.reachable(stand, block) && !SpiritReach.forbiddenStand(stand, block)
                && !body.contains(stand) && !body.contains(head) && !body.contains(floor)
                && MiningSafety.canStand(stand, view);
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
