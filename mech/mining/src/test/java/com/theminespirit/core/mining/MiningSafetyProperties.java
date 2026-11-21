package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.BreakSafety;
import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class MiningSafetyProperties {
    private static final int GRID = 5;
    private static final BlockCoord TARGET = new BlockCoord(2, 2, 2);
    private static final int[][] FACES = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final Cell[] CELLS = Cell.values();

    @Property
    void canBreak_RandomWorld_UnsafeExactlyWhenFluidOnFace(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        CellWorld world = randomWorld(new Random(seed));

        BreakSafety safety = MiningSafety.canBreak(TARGET, world);

        assertEquals(
                fluidOnFace(world),
                safety instanceof BreakSafety.Unsafe,
                "Запрет ломки обязан совпадать с наличием жидкости на грани: иначе либо дух тонет, "
                        + "либо безопасная руда остаётся недобытой"
                        + " При смене canBreak_RandomWorld_UnsafeExactlyWhenFluidOnFace проверь это утверждение.");
        assertTrue(
                world.reads() <= 6,
                "Проверка ломки читает не более 6 граней. Чтений: " + world.reads()
                        + " Иначе тест canBreak_RandomWorld_UnsafeExactlyWhenFluidOnFace перестанет ловить эту поломку. При смене canBreak_RandomWorld_UnsafeExactlyWhenFluidOnFace проверь это утверждение.");
    }

    @Property
    void of_RandomWorld_EverySpotPassesCanStandAndReadsBounded(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        CellWorld world = randomWorld(new Random(seed));

        List<BlockCoord> spots = StandSpots.of(TARGET, world, new BlockCoord(0, 0, 0));
        int reads = world.reads();

        for (BlockCoord spot : spots) {
            assertTrue(
                    MiningSafety.canStand(spot, world),
                    "Каждая выданная стоянка обязана проходить canStand: иначе дух встаёт в воду, огонь или без пола"
                            + " При смене of_RandomWorld_EverySpotPassesCanStandAndReadsBounded проверь это утверждение.");
        }
        assertTrue(
                reads <= 440,
                "Поиск стоянок обязан укладываться в предел чтений " + 440
                        + ": иначе планирование цели кладёт серверный тик. Чтений: " + reads
                        + " При смене of_RandomWorld_EverySpotPassesCanStandAndReadsBounded проверь это утверждение.");
    }

    @Property
    void of_RandomWorld_NoStandUnderFallingAboveTarget(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        CellWorld world = randomWorld(new Random(seed));
        if (world.peek(new BlockCoord(TARGET.x(), TARGET.y() + 1, TARGET.z())) != Cell.FALLING) {
            return;
        }

        List<BlockCoord> spots = StandSpots.of(TARGET, world, new BlockCoord(0, 0, 0));

        for (BlockCoord spot : spots) {
            assertTrue(
                    spot.x() != TARGET.x() || spot.z() != TARGET.z() || spot.y() >= TARGET.y(),
                    "Под гравием над целью стоянка в столбце цели ниже её запрещена — гравий падает после ломки. "
                            + "Нарушающая стоянка: " + spot
                            + " Иначе тест of_RandomWorld_NoStandUnderFallingAboveTarget перестанет ловить эту поломку. При смене of_RandomWorld_NoStandUnderFallingAboveTarget проверь это утверждение.");
        }
    }

    private static boolean fluidOnFace(CellWorld world) {
        for (int[] face : FACES) {
            Cell cell = world.peek(new BlockCoord(TARGET.x() + face[0], TARGET.y() + face[1], TARGET.z() + face[2]));
            if (cell == Cell.WATER || cell == Cell.LAVA) {
                return true;
            }
        }
        return false;
    }

    private static CellWorld randomWorld(Random random) {
        CellWorld world = new CellWorld();
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                for (int z = 0; z < GRID; z++) {
                    world.set(x, y, z, CELLS[random.nextInt(CELLS.length)]);
                }
            }
        }
        world.set(TARGET.x(), TARGET.y(), TARGET.z(), Cell.SOLID);
        return world;
    }
}
