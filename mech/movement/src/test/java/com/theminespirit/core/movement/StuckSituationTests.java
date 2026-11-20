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

        assertEquals(
                StuckSituation.Kind.FREE,
                verdict.kind(),
                "два блока воздуха сбоку — проход есть, дух не застрял"
                        + " Иначе тест detect_TwoAirBlocksToSide_IsFree перестанет ловить эту поломку. При смене detect_TwoAirBlocksToSide_IsFree проверь это утверждение.");
        assertEquals(
                new BlockCoord(11, 64, 10),
                verdict.passage(),
                "свободный вид обязан назвать сторону прохода"
                        + " Иначе тест detect_TwoAirBlocksToSide_IsFree перестанет ловить эту поломку. При смене detect_TwoAirBlocksToSide_IsFree проверь это утверждение.");
        assertFalse(
                verdict.stuck(),
                "свободный дух не требует спасения"
                        + " Иначе тест detect_TwoAirBlocksToSide_IsFree перестанет ловить эту поломку. При смене detect_TwoAirBlocksToSide_IsFree проверь это утверждение.");
    }

    @Test
    void detect_OnlyHeadHighGap_IsWalled() {
        Set<BlockCoord> air = Set.of(new BlockCoord(11, 64, 10));

        StuckSituation.Verdict verdict = StuckSituation.detect(FEET, grid(air, (x, z) -> 64));

        assertEquals(
                StuckSituation.Kind.WALLED,
                verdict.kind(),
                "проход в один блок высотой не проход: дух ростом в два блока не пролезет, его надо спасать"
                        + " Иначе тест detect_OnlyHeadHighGap_IsWalled перестанет ловить эту поломку. При смене detect_OnlyHeadHighGap_IsWalled проверь это утверждение.");
        assertTrue(
                verdict.stuck(),
                "замурованный дух требует спасения"
                        + " Иначе тест detect_OnlyHeadHighGap_IsWalled перестанет ловить эту поломку. При смене detect_OnlyHeadHighGap_IsWalled проверь это утверждение.");
        assertNull(
                verdict.pit(),
                "у стены ямы нет: выход из ямы не подсказывается"
                        + " Иначе тест detect_OnlyHeadHighGap_IsWalled перестанет ловить эту поломку. При смене detect_OnlyHeadHighGap_IsWalled проверь это утверждение.");
    }

    @Test
    void detect_NoPassageAnySide_IsWalled() {
        StuckSituation.Verdict verdict = StuckSituation.detect(FEET, grid(Set.of(), (x, z) -> 64));

        assertEquals(
                StuckSituation.Kind.WALLED,
                verdict.kind(),
                "воздуха по два блока ни в одну сторону нет — дух замурован, даже если рядом ровный пол"
                        + " Иначе тест detect_NoPassageAnySide_IsWalled перестанет ловить эту поломку. При смене detect_NoPassageAnySide_IsWalled проверь это утверждение.");
    }

    @Test
    void detect_SurroundedByHigherGround_IsDeepPit() {
        StuckSituation.Verdict verdict =
                StuckSituation.detect(FEET, grid(Set.of(), (x, z) -> x == 10 && z == 10 ? 64 : 67));

        assertEquals(
                StuckSituation.Kind.DEEP_PIT,
                verdict.kind(),
                "яма глубже шага без ступеней — отдельный вид: дух сам не выйдет, нужен подъём"
                        + " Иначе тест detect_SurroundedByHigherGround_IsDeepPit перестанет ловить эту поломку. При смене detect_SurroundedByHigherGround_IsDeepPit проверь это утверждение.");
        assertEquals(
                2,
                verdict.pit().rise(),
                "от дна до края ямы в три блока подсыпка на два блока"
                        + " Иначе тест detect_SurroundedByHigherGround_IsDeepPit перестанет ловить эту поломку. При смене detect_SurroundedByHigherGround_IsDeepPit проверь это утверждение.");
    }

    @Test
    void detect_PitWithSidePassage_IsFree() {
        Set<BlockCoord> air = Set.of(new BlockCoord(11, 64, 10), new BlockCoord(11, 65, 10));

        StuckSituation.Verdict verdict = StuckSituation.detect(FEET, grid(air, (x, z) -> x == 10 && z == 10 ? 64 : 67));

        assertEquals(
                StuckSituation.Kind.FREE,
                verdict.kind(),
                "из ямы есть боковой проход — дух выйдет сам, спасение не требуется"
                        + " Иначе тест detect_PitWithSidePassage_IsFree перестанет ловить эту поломку. При смене detect_PitWithSidePassage_IsFree проверь это утверждение.");
        assertNull(
                verdict.pit(),
                "при свободном проходе выход из ямы не нужен"
                        + " Иначе тест detect_PitWithSidePassage_IsFree перестанет ловить эту поломку. При смене detect_PitWithSidePassage_IsFree проверь это утверждение.");
    }

    @Test
    void detect_WaterOnSide_IsFree() {
        Set<BlockCoord> water = Set.of(new BlockCoord(11, 64, 10), new BlockCoord(11, 65, 10));
        StuckSituation.Grid grid = new StuckSituation.Grid() {
            @Override
            public boolean passable(BlockCoord cell) {
                return false;
            }

            @Override
            public boolean swimmable(BlockCoord cell) {
                return water.contains(cell);
            }

            @Override
            public int floorY(int x, int z) {
                return 64;
            }
        };

        StuckSituation.Verdict verdict = StuckSituation.detect(FEET, grid);

        assertEquals(
                StuckSituation.Kind.FREE,
                verdict.kind(),
                "Вода сбоку — дорога, а не стена: дух в пруду не замурован, спасение не требуется"
                        + " Иначе тест detect_WaterOnSide_IsFree перестанет ловить эту поломку. При смене detect_WaterOnSide_IsFree проверь это утверждение.");
        assertEquals(
                new BlockCoord(11, 64, 10),
                verdict.passage(),
                "Свободный вид обязан назвать водную сторону прохода, иначе спасение снова сочтёт духа замурованным"
                        + " При смене detect_WaterOnSide_IsFree проверь это утверждение.");
    }

    @Test
    void verdict_FreeWithoutPassage_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StuckSituation.Verdict(StuckSituation.Kind.FREE, null, null),
                "свободный вид без названного прохода обязан отклоняться: иначе спасение пропустит духа"
                        + " При смене verdict_FreeWithoutPassage_IsRejected проверь это утверждение.");
    }

    @Test
    void verdict_DeepPitWithoutExit_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StuckSituation.Verdict(StuckSituation.Kind.DEEP_PIT, null, null),
                "яма без выхода обязана отклоняться: правилу подъёма некуда ставить ступени"
                        + " Иначе тест verdict_DeepPitWithoutExit_IsRejected перестанет ловить эту поломку. При смене verdict_DeepPitWithoutExit_IsRejected проверь это утверждение.");
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
