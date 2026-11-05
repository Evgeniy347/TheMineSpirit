package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.AbstractSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HydrationTests {
    private static final BlockCoord GROUND = new BlockCoord(0, 0, 0);

    @Test
    void watered_WaterAtDistanceFourSameLevel_IsWatered() {
        Hydration hydration = Hydration.of(Set.of(new BlockCoord(4, 0, 0)));

        assertTrue(
                hydration.watered(GROUND),
                "Вода в 4 блоках увлажняет грядку по ванильному правилу: без неё till откажет на годной земле");
    }

    @Test
    void watered_WaterAtDistanceFive_IsDry() {
        Hydration hydration = Hydration.of(Set.of(new BlockCoord(5, 0, 0)));

        assertFalse(
                hydration.watered(GROUND),
                "Вода в 5 блоках грядку не достаёт: распаханная там земля пересохнет, till обязан отказать");
    }

    @Test
    void watered_WaterOneAboveGround_IsWatered() {
        Hydration hydration = Hydration.of(Set.of(new BlockCoord(3, 1, -2)));

        assertTrue(
                hydration.watered(GROUND),
                "Вода на блок выше земли (канал вровень с грядкой) увлажняет: иначе приподнятый канал сочтут сухим");
    }

    @Test
    void watered_WaterTwoAboveGround_IsDry() {
        Hydration hydration = Hydration.of(Set.of(new BlockCoord(0, 2, 0)));

        assertFalse(
                hydration.watered(GROUND),
                "Вода на два блока выше земли не увлажняет: высокий акведук грядку не спасёт от высыхания");
    }

    @Test
    void watered_WaterOneBelowGround_IsDry() {
        Hydration hydration = Hydration.of(Set.of(new BlockCoord(0, -1, 0)));

        assertFalse(
                hydration.watered(GROUND),
                "Вода под землёй не увлажняет: иначе till вспашет землю над подземной рекой, и она пересохнет");
    }

    @Test
    void watered_WaterAtBoxCorner_IsWatered() {
        Hydration hydration = Hydration.of(Set.of(new BlockCoord(4, 1, 4)));

        assertTrue(
                hydration.watered(GROUND),
                "Углы ящика 9×9×2 увлажняют: ванильное правило — ящик, а не круг, грядка в углу от воды растёт");
    }

    @Test
    void watered_NoWater_IsDry() {
        assertFalse(
                Hydration.of(Set.of()).watered(GROUND),
                "Без воды грядка сухая: till без воды обязан отказать до первой вспашки");
    }

    @Test
    void watered_TenThousandWaterCells_LookupsStayWithinBox() {
        CountingWater water = new CountingWater();
        for (int i = 0; i < 10_000; i++) {
            water.add(new BlockCoord(1000 + i, 0, 1000));
        }
        Hydration hydration = Hydration.of(water);

        assertFalse(
                hydration.watered(GROUND),
                "Далёкое озеро грядку не увлажняет: поиск обязан вернуть сухо, а не утонуть в размере озера");
        assertTrue(
                water.lookups <= Hydration.MAX_LOOKUPS,
                "Проверка обязана читать не более 9×9×2=" + Hydration.MAX_LOOKUPS + " клеток независимо от размера "
                        + "озера: иначе озеро из тысяч клеток кладёт серверный тик. Обращений: " + water.lookups);
    }

    @Test
    void countingWater_StreamTraversal_CountsElementVisits() {
        CountingWater water = new CountingWater();
        for (int i = 0; i < 5; i++) {
            water.add(new BlockCoord(i, 0, 0));
        }
        int before = water.lookups;

        List<BlockCoord> seen = water.stream().filter(cell -> cell.x() >= 0).toList();

        assertEquals(5, seen.size(), "Обход обязан вернуть все клетки: счётчик не должен терять элементы");
        assertTrue(
                water.lookups - before >= 5,
                "Обход через stream/iterator обязан считаться поэлементно: иначе перебор всего озера даст "
                        + "0 обращений и тест масштаба ослепнет. Обращений за обход: " + (water.lookups - before));
    }

    @Test
    void maxLookups_BoxVolume_Is162() {
        assertEquals(
                162,
                Hydration.MAX_LOOKUPS,
                "Предел обращений обязан равняться объёму ящика 9×9×2: иначе тест масштаба проверяет не то число");
    }

    static boolean bruteForce(Set<BlockCoord> water, BlockCoord ground) {
        for (BlockCoord cell : water) {
            if (Math.abs(cell.x() - ground.x()) <= Hydration.RADIUS
                    && cell.y() - ground.y() >= 0
                    && cell.y() - ground.y() <= 1
                    && Math.abs(cell.z() - ground.z()) <= Hydration.RADIUS) {
                return true;
            }
        }
        return false;
    }

    static final class CountingWater extends AbstractSet<BlockCoord> {
        int lookups;
        private final Set<BlockCoord> cells = new HashSet<>();

        @Override
        public boolean contains(Object cell) {
            lookups++;
            return cells.contains(cell);
        }

        @Override
        public boolean add(BlockCoord cell) {
            return cells.add(cell);
        }

        @Override
        public Iterator<BlockCoord> iterator() {
            Iterator<BlockCoord> delegate = cells.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public BlockCoord next() {
                    lookups++;
                    return delegate.next();
                }
            };
        }

        @Override
        public int size() {
            lookups++;
            return cells.size();
        }
    }

    static Set<BlockCoord> waterNearOrigin() {
        Set<BlockCoord> water = new HashSet<>();
        water.add(new BlockCoord(4, 0, 0));
        water.add(new BlockCoord(0, 1, 0));
        water.add(new BlockCoord(-4, 1, -4));
        return water;
    }

    static BlockCoord ground() {
        return GROUND;
    }
}
