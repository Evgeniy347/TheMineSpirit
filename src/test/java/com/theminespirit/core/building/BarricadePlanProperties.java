package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class BarricadePlanProperties {
    @Provide
    Arbitrary<BlockCoord> coords() {
        return Combinators.combine(Arbitraries.integers().between(-20, 20),
                Arbitraries.integers().between(0, 200), Arbitraries.integers().between(-20, 20))
                .as(BlockCoord::new);
    }

    @Provide
    Arbitrary<Integer> heights() {
        return Arbitraries.integers().between(1, 4);
    }

    private static boolean sameLine(BlockCoord left, BlockCoord right) {
        return left.x() == right.x() && left.z() == right.z();
    }

    @Property
    void seal_AnyBanks_NoSpiritCellNoDuplicates(@ForAll("coords") BlockCoord left,
            @ForAll("coords") BlockCoord right, @ForAll("heights") int height,
            @ForAll("coords") BlockCoord spirit) {
        if (sameLine(left, right)) {
            return;
        }
        List<BlockCoord> seam = BarricadePlan.seal(left, right, height, spirit);

        assertTrue(!seam.contains(spirit),
                "Шов обязан обходить клетку духа: иначе строитель замуровывает себя");
        assertEquals(seam.size(), new HashSet<>(seam).size(),
                "Дубли — двойной расход блоков: каждая клетка ставится один раз");
    }

    @Property
    void seal_AnyBanks_CellsBetweenBanks(@ForAll("coords") BlockCoord left,
            @ForAll("coords") BlockCoord right, @ForAll("heights") int height,
            @ForAll("coords") BlockCoord spirit) {
        if (sameLine(left, right)) {
            return;
        }
        List<BlockCoord> seam = BarricadePlan.seal(left, right, height, spirit);

        int minX = Math.min(left.x(), right.x());
        int maxX = Math.max(left.x(), right.x());
        int minZ = Math.min(left.z(), right.z());
        int maxZ = Math.max(left.z(), right.z());
        int baseY = Math.min(left.y(), right.y());
        for (BlockCoord cell : seam) {
            assertTrue(cell.x() >= minX && cell.x() <= maxX && cell.z() >= minZ && cell.z() <= maxZ
                    && cell.y() >= baseY && cell.y() < baseY + height,
                    "Клетка шва обязана лежать в сечении между берегами: иначе баррикада расползается из прохода");
        }
        assertTrue(!seam.isEmpty(),
                "Шов между разными берегами обязан быть непустым: иначе проход остаётся открытым");
    }

    @Property
    void seal_SameInput_TwiceSameResult(@ForAll("coords") BlockCoord left, @ForAll("coords") BlockCoord right,
            @ForAll("heights") int height, @ForAll("coords") BlockCoord spirit) {
        if (sameLine(left, right)) {
            return;
        }
        assertEquals(BarricadePlan.seal(left, right, height, spirit),
                BarricadePlan.seal(left, right, height, spirit),
                "Тот же вход даёт тот же шов: иначе бронь клеток не сходится с постановкой");
    }
}
