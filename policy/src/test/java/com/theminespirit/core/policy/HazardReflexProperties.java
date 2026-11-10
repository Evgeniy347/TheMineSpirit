package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class HazardReflexProperties {
    private static final Cell[] WEIGHTED = {
        Cell.SOLID,
        Cell.SOLID,
        Cell.SOLID,
        Cell.PASSABLE,
        Cell.PASSABLE,
        Cell.PASSABLE,
        Cell.WATER,
        Cell.LAVA,
        Cell.FIRE,
        Cell.HAZARD,
        Cell.FALLING
    };
    private static final Cell[] HARMLESS = {Cell.SOLID, Cell.PASSABLE, Cell.FALLING};
    private static final BlockCoord FEET = Pictures.FEET;
    private static final BlockCoord BELOW = new BlockCoord(FEET.x(), FEET.y() - 1, FEET.z());

    private static LocalSurroundings randomSnapshot(int seed, Cell[] palette, BodyFlags flags) {
        Random random = new Random(seed);
        Cell[] fixed = new Cell[LocalSurroundings.READS];
        for (int i = 0; i < fixed.length; i++) {
            fixed[i] = palette[random.nextInt(palette.length)];
        }
        int[] next = {0};
        return LocalSurroundings.read(FEET, coord -> fixed[next[0]++], flags, Optional.empty());
    }

    private static BodyFlags flags(Random random) {
        return new BodyFlags(random.nextBoolean(), random.nextBoolean(), random.nextBoolean(), random.nextBoolean());
    }

    private static LocalSurroundings leftOrigin(LocalSurroundings surroundings) {
        return LocalSurroundings.read(
                FEET,
                coord -> {
                    Cell cell = surroundings.cellAt(coord);
                    if (coord.equals(FEET)) {
                        return Cell.PASSABLE;
                    }
                    return coord.equals(BELOW) && cell != Cell.SOLID ? Cell.SOLID : cell;
                },
                BodyFlags.CALM,
                Optional.empty());
    }

    @Property(tries = 200)
    void evaluate_RandomSnapshot_FleeCellAlwaysPassesCanStand(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        LocalSurroundings surroundings = randomSnapshot(seed, WEIGHTED, flags(random));

        PolicyVerdict verdict = HazardReflex.evaluate(surroundings, PolicyExemptions.empty(), surroundings);

        if (verdict instanceof PolicyVerdict.Reflex reflex && reflex.action() instanceof ReflexAction.Flee flee) {
            assertTrue(
                    MiningSafety.canStand(flee.cell(), leftOrigin(surroundings)),
                    "Клетка бегства обязана проходить canStand (с поправкой на покидаемую клетку духа): иначе дух убежит из лавы в огонь или в воздух. Причина: "
                            + reflex.reason());
        }
    }

    @Property(tries = 200)
    void evaluate_RandomSnapshot_ExitWaterCellHasFloorHeadroomAndNoHazardsAround(
            @ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        LocalSurroundings surroundings = randomSnapshot(seed, WEIGHTED, flags(random));

        PolicyVerdict verdict = HazardReflex.evaluate(surroundings, PolicyExemptions.empty(), surroundings);

        if (verdict instanceof PolicyVerdict.Reflex reflex && reflex.action() instanceof ReflexAction.ExitWater exit) {
            BlockCoord cell = exit.cell();
            BlockCoord floor = new BlockCoord(cell.x(), cell.y() - 1, cell.z());
            BlockCoord head = new BlockCoord(cell.x(), cell.y() + 1, cell.z());
            assertEquals(
                    Cell.PASSABLE,
                    surroundings.cellAt(cell),
                    "Клетка выхода из воды обязана быть проходимой, а не водой: " + cell);
            assertEquals(
                    Cell.PASSABLE,
                    surroundings.cellAt(head),
                    "Над клеткой выхода обязано быть место для головы: " + cell);
            assertTrue(
                    MiningSafety.canStandOn(surroundings.cellAt(floor)),
                    "Под клеткой выхода обязан быть годный пол — твёрдый или сыпучий: " + cell);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (BlockCoord ring : new BlockCoord[] {
                        new BlockCoord(cell.x() + dx, cell.y(), cell.z() + dz),
                        new BlockCoord(cell.x() + dx, floor.y(), cell.z() + dz)
                    }) {
                        Cell around = ring.equals(FEET) || ring.equals(BELOW) ? Cell.SOLID : surroundings.cellAt(ring);
                        assertFalse(
                                around == Cell.LAVA || around == Cell.FIRE || around == Cell.HAZARD,
                                "Рядом с клеткой выхода из воды нет лавы, огня и опасных блоков: " + ring + " = "
                                        + around);
                    }
                }
            }
        }
    }

    @Property(tries = 200)
    void evaluate_HarmlessSurroundings_NeverGivesReflex(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        BodyFlags harmless = new BodyFlags(false, false, false, random.nextBoolean());
        LocalSurroundings surroundings = randomSnapshot(seed, HARMLESS, harmless);

        PolicyVerdict verdict = HazardReflex.evaluate(surroundings, PolicyExemptions.empty(), surroundings);

        assertEquals(
                new PolicyVerdict.Clear(),
                verdict,
                "Без лавы, огня, опасных блоков, воды и горения рефлексу нечего делать: ложное срабатывание уводит духа с работы");
    }

    @Property(tries = 200)
    void evaluate_WaterExemption_NeverExitsWater(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        LocalSurroundings surroundings = randomSnapshot(seed, WEIGHTED, flags(random));

        PolicyVerdict verdict = HazardReflex.evaluate(surroundings, PolicyExemptions.of(PolicyId.WATER), surroundings);

        if (verdict instanceof PolicyVerdict.Reflex reflex) {
            assertFalse(
                    reflex.action() instanceof ReflexAction.ExitWater,
                    "Исключение WATER обязано снимать выход из воды: " + reflex);
            assertFalse(
                    reflex.policy() == PolicyId.WATER,
                    "Исключение WATER обязано снимать рефлексы политики воды: " + reflex);
        }
    }

    @Property(tries = 200)
    void evaluate_SameSnapshotReadTwice_GivesEqualVerdicts(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        BodyFlags flags = flags(random);

        LocalSurroundings firstSnapshot = randomSnapshot(seed, WEIGHTED, flags);
        LocalSurroundings secondSnapshot = randomSnapshot(seed, WEIGHTED, flags);

        PolicyVerdict first = HazardReflex.evaluate(firstSnapshot, PolicyExemptions.empty(), firstSnapshot);
        PolicyVerdict second = HazardReflex.evaluate(secondSnapshot, PolicyExemptions.empty(), secondSnapshot);

        assertEquals(
                first,
                second,
                "Вердикт зависит только от снимка и исключений: иначе два тика подряд спорят о том, куда бежать");
    }
}
