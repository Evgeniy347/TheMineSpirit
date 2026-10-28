package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class StuckSituationTests {
    private static final BlockCoord FEET = new BlockCoord(10, 64, 10);

    @Test
    void detect_TwoAirBlocksToSide_IsFree() {
        Set<BlockCoord> air = Set.of(new BlockCoord(11, 64, 10), new BlockCoord(11, 65, 10));

        StuckSituation.Verdict verdict = StuckSituation.detect(FEET, grid(air, (x, z) -> 64));

        assertEquals(StuckSituation.Kind.FREE, verdict.kind(), "два блока воздуха сбоку — проход есть, дух не застрял");
        assertEquals(new BlockCoord(11, 64, 10), verdict.passage(), "свободный вид обязан назвать сторону прохода");
        assertFalse(verdict.stuck(), "свободный дух не требует спасения");
    }

    @Test
    void detect_OnlyHeadHighGap_IsWalled() {
        Set<BlockCoord> air = Set.of(new BlockCoord(11, 64, 10));

        StuckSituation.Verdict verdict = StuckSituation.detect(FEET, grid(air, (x, z) -> 64));

        assertEquals(
                StuckSituation.Kind.WALLED,
                verdict.kind(),
                "проход в один блок высотой не проход: дух ростом в два блока не пролезет, его надо спасать");
        assertTrue(verdict.stuck(), "замурованный дух требует спасения");
        assertNull(verdict.pit(), "у стены ямы нет: выход из ямы не подсказывается");
    }

    @Test
    void detect_NoPassageAnySide_IsWalled() {
        StuckSituation.Verdict verdict = StuckSituation.detect(FEET, grid(Set.of(), (x, z) -> 64));

        assertEquals(
                StuckSituation.Kind.WALLED,
                verdict.kind(),
                "воздуха по два блока ни в одну сторону нет — дух замурован, даже если рядом ровный пол");
    }

    @Test
    void detect_SurroundedByHigherGround_IsDeepPit() {
        StuckSituation.Verdict verdict =
                StuckSituation.detect(FEET, grid(Set.of(), (x, z) -> x == 10 && z == 10 ? 64 : 67));

        assertEquals(
                StuckSituation.Kind.DEEP_PIT,
                verdict.kind(),
                "яма глубже шага без ступеней — отдельный вид: дух сам не выйдет, нужен подъём");
        assertEquals(2, verdict.pit().rise(), "от дна до края ямы в три блока подсыпка на два блока");
    }

    @Test
    void detect_PitWithSidePassage_IsFree() {
        Set<BlockCoord> air = Set.of(new BlockCoord(11, 64, 10), new BlockCoord(11, 65, 10));

        StuckSituation.Verdict verdict = StuckSituation.detect(FEET, grid(air, (x, z) -> x == 10 && z == 10 ? 64 : 67));

        assertEquals(
                StuckSituation.Kind.FREE,
                verdict.kind(),
                "из ямы есть боковой проход — дух выйдет сам, спасение не требуется");
        assertNull(verdict.pit(), "при свободном проходе выход из ямы не нужен");
    }

    @Test
    void verdict_FreeWithoutPassage_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StuckSituation.Verdict(StuckSituation.Kind.FREE, null, null),
                "свободный вид без названного прохода обязан отклоняться: иначе спасение пропустит духа");
    }

    @Test
    void verdict_DeepPitWithoutExit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StuckSituation.Verdict(StuckSituation.Kind.DEEP_PIT, null, null),
                "яма без выхода обязана отклоняться: правилу подъёма некуда ставить ступени");
    }

    private static StuckSituation.Grid grid(Set<BlockCoord> air, IntBinaryOperator floor) {
        Set<BlockCoord> cells = new HashSet<>(air);
        return new StuckSituation.Grid() {
            @Override
            public boolean passable(BlockCoord cell) {
                return cells.contains(cell);
            }

            @Override
            public int floorY(int x, int z) {
                return floor.applyAsInt(x, z);
            }
        };
    }
}
