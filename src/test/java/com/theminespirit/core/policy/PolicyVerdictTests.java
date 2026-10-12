package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PolicyVerdictTests {
    @Test
    void clear_ClearVerdict_IsClear() {
        PolicyVerdict v = new PolicyVerdict.Clear();

        assertTrue(v instanceof PolicyVerdict.Clear, "Clear — это вердикт без рефлекса");
    }

    @Test
    void reflex_Flee_HasCorrectFields() {
        BlockCoord cell = new BlockCoord(1, 2, 3);
        PolicyVerdict.Reflex r = PolicyVerdict.Reflex.flee(PolicyId.LAVA_FIRE, cell, "в лаве");

        assertEquals(PolicyId.LAVA_FIRE, r.policy(), "политика вердикта — LAVA_FIRE");
        assertEquals(ReflexAction.FLEE, r.action(), "действие — FLEE");
        assertEquals("в лаве", r.reason(), "причина — в лаве");
        assertTrue(r.targetCell().isPresent(), "целевая клетка присутствует");
        assertEquals(cell, r.targetCell().get(), "целевая клетка совпадает с переданной");
    }

    @Test
    void reflex_ExitWater_HasCorrectFields() {
        BlockCoord cell = new BlockCoord(5, 6, 7);
        PolicyVerdict.Reflex r = PolicyVerdict.Reflex.exitWater(PolicyId.WATER, cell, "в воде");

        assertEquals(PolicyId.WATER, r.policy(), "политика вердикта — WATER");
        assertEquals(ReflexAction.EXIT_WATER, r.action(), "действие — EXIT_WATER");
        assertEquals("в воде", r.reason(), "причина — в воде");
        assertEquals(cell, r.targetCell().get(), "целевая клетка совпадает с переданной");
    }

    @Test
    void reflex_Surface_HasEmptyTarget() {
        PolicyVerdict.Reflex r = PolicyVerdict.Reflex.surface(PolicyId.AIR, "мало воздуха");

        assertEquals(PolicyId.AIR, r.policy(), "политика вердикта — AIR");
        assertEquals(ReflexAction.SURFACE, r.action(), "действие — SURFACE");
        assertEquals("мало воздуха", r.reason(), "причина — мало воздуха");
        assertTrue(r.targetCell().isEmpty(), "SURFACE не имеет целевой клетки");
    }

    @Test
    void reflex_StepOutOfPortal_HasTarget() {
        BlockCoord cell = new BlockCoord(0, 0, 0);
        PolicyVerdict.Reflex r = PolicyVerdict.Reflex.stepOutOfPortal(PolicyId.PORTAL, cell, "в портале");

        assertEquals(PolicyId.PORTAL, r.policy(), "политика вердикта — PORTAL");
        assertEquals(ReflexAction.STEP_OUT_OF_PORTAL, r.action(), "действие — STEP_OUT_OF_PORTAL");
        assertEquals(cell, r.targetCell().get(), "целевая клетка — выход из портала");
    }

    @Test
    void reflex_ReturnToZone_HasTarget() {
        BlockCoord cell = new BlockCoord(10, 64, 10);
        PolicyVerdict.Reflex r = PolicyVerdict.Reflex.returnToZone(PolicyId.WORK_ZONE, cell, "вышел за зону");

        assertEquals(PolicyId.WORK_ZONE, r.policy(), "политика вердикта — WORK_ZONE");
        assertEquals(ReflexAction.RETURN_TO_ZONE, r.action(), "действие — RETURN_TO_ZONE");
        assertEquals(cell, r.targetCell().get(), "целевая клетка — возврат в зону");
    }

    @Test
    void reflex_StopMoving_HasEmptyTarget() {
        PolicyVerdict.Reflex r = PolicyVerdict.Reflex.stopMoving(PolicyId.DROP, "нет безопасной клетки");

        assertEquals(PolicyId.DROP, r.policy(), "политика вердикта — DROP");
        assertEquals(ReflexAction.STOP_MOVING, r.action(), "действие — STOP_MOVING");
        assertTrue(r.targetCell().isEmpty(), "STOP_MOVING не имеет целевой клетки");
    }

    @Test
    void reflex_NullPolicy_ThrowsException() {
        assertThrows(NullPointerException.class, () -> PolicyVerdict.Reflex.flee(null, new BlockCoord(0,0,0), "причина"),
                "null-политика обязана вызывать NPE в фабричном методе");
    }

    @Test
    void reflex_NullAction_ThrowsException() {
        assertThrows(NullPointerException.class, () -> new PolicyVerdict.Reflex(PolicyId.LAVA_FIRE, null, "причина", Optional.empty()),
                "null-действие обязано вызывать NPE в конструкторе record");
    }

    @Test
    void reflex_NullReason_ThrowsException() {
        assertThrows(NullPointerException.class, () -> PolicyVerdict.Reflex.flee(PolicyId.LAVA_FIRE, new BlockCoord(0,0,0), null),
                "null-причина обязана вызывать NPE в фабричном методе");
    }
}