package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FenceDemolitionTests {
    private static final FenceLine RING = new FenceLine(
            List.of(new ColumnMask.Cell(0, 0), new ColumnMask.Cell(1, 0), new ColumnMask.Cell(2, 0)), List.of());
    private static final ColumnMask STRIP = FenceStrip.of(RING, 1);

    @Test
    void allowed_FenceAndGateInsideStrip_True() {
        assertTrue(
                FenceDemolition.allowed("minecraft:oak_fence", STRIP, 1, 0),
                "Забор в полосе ограды сносится: старую ограду ставят заново на y+1 (решение владельца 28.10)"
                        + " Иначе тест allowed_FenceAndGateInsideStrip_True перестанет ловить эту поломку. При смене allowed_FenceAndGateInsideStrip_True проверь это утверждение.");
        assertTrue(
                FenceDemolition.allowed("minecraft:oak_fence_gate", STRIP, 1, 1),
                "Калитка в полосе ограды сносится вместе с забором: это та же старая ограда"
                        + " Иначе тест allowed_FenceAndGateInsideStrip_True перестанет ловить эту поломку. При смене allowed_FenceAndGateInsideStrip_True проверь это утверждение.");
    }

    @Test
    void allowed_FenceOutsideStrip_False() {
        assertFalse(
                FenceDemolition.allowed("minecraft:oak_fence", STRIP, 1, 2),
                "Забор вне полосы ограды защищён как прежде: это постройка игрока (protection.json → deny)"
                        + " Иначе тест allowed_FenceOutsideStrip_False перестанет ловить эту поломку. При смене allowed_FenceOutsideStrip_False проверь это утверждение.");
    }

    @Test
    void allowed_NotFenceInsideStrip_False() {
        assertFalse(
                FenceDemolition.allowed("minecraft:oak_door", STRIP, 1, 0),
                "Дверь в полосе ограды не трогается: исключение только для заборов и калиток"
                        + " Иначе тест allowed_NotFenceInsideStrip_False перестанет ловить эту поломку. При смене allowed_NotFenceInsideStrip_False проверь это утверждение.");
        assertFalse(
                FenceDemolition.allowed("minecraft:oak_sign", STRIP, 1, 0),
                "Табличка в полосе ограды не трогается: исключение только для заборов и калиток"
                        + " Иначе тест allowed_NotFenceInsideStrip_False перестанет ловить эту поломку. При смене allowed_NotFenceInsideStrip_False проверь это утверждение.");
    }

    @Test
    void stub_SpiritFenceInOrchard_Removed() {
        assertTrue(
                FenceDemolition.stub("minecraft:jungle_fence", stub(false, true, true, false, false, true, false)),
                "Столб-заглушка в саду, поставленный духом, уходит в уборку: иначе лишний забор стоит вечно"
                        + " При смене stub_SpiritFenceInOrchard_Removed проверь это утверждение.");
    }

    @Test
    void stub_PlayerFence_NotRemoved() {
        assertFalse(
                FenceDemolition.stub("minecraft:jungle_fence", stub(false, true, true, false, false, false, true)),
                "Забор игрока не убирается даже в саду: уборка не трогает постройки игрока"
                        + " Иначе тест stub_PlayerFence_NotRemoved перестанет ловить эту поломку. При смене stub_PlayerFence_NotRemoved проверь это утверждение.");
    }

    @Test
    void stub_PlannedStripOrRingFence_NotRemoved() {
        assertFalse(
                FenceDemolition.stub("minecraft:jungle_fence", stub(true, true, true, false, false, true, false)),
                "Забор из схемы постройки — не заглушка: его ставит и снимает только свой план"
                        + " Иначе тест stub_PlannedStripOrRingFence_NotRemoved перестанет ловить эту поломку. При смене stub_PlannedStripOrRingFence_NotRemoved проверь это утверждение.");
        assertFalse(
                FenceDemolition.stub("minecraft:jungle_fence", stub(false, false, false, true, false, true, false)),
                "Забор в полосе ограды — плановая ограда, а не заглушка"
                        + " Иначе тест stub_PlannedStripOrRingFence_NotRemoved перестанет ловить эту поломку. При смене stub_PlannedStripOrRingFence_NotRemoved проверь это утверждение.");
        assertFalse(
                FenceDemolition.stub("minecraft:jungle_fence", stub(false, false, false, false, true, true, false)),
                "Столб кольца плана — ограда поселения, а не заглушка"
                        + " Иначе тест stub_PlannedStripOrRingFence_NotRemoved перестанет ловить эту поломку. При смене stub_PlannedStripOrRingFence_NotRemoved проверь это утверждение.");
    }

    @Test
    void stub_ForeignPlacement_NotRemoved() {
        assertFalse(
                FenceDemolition.stub("minecraft:jungle_fence", stub(false, true, false, false, false, true, false)),
                "Забор в чужой площадке (двор, загон) не убирается: там ограда может быть частью постройки"
                        + " Иначе тест stub_ForeignPlacement_NotRemoved перестанет ловить эту поломку. При смене stub_ForeignPlacement_NotRemoved проверь это утверждение.");
    }

    @Test
    void stub_NotFence_NotRemoved() {
        assertFalse(
                FenceDemolition.stub("minecraft:jungle_planks", stub(false, true, true, false, false, true, false)),
                "Уборка заглушек касается только заборов и калиток"
                        + " Иначе тест stub_NotFence_NotRemoved перестанет ловить эту поломку. При смене stub_NotFence_NotRemoved проверь это утверждение.");
    }

    private static FenceDemolition.StubFacts stub(
            boolean planned,
            boolean inPlacement,
            boolean inOrchard,
            boolean inFenceStrip,
            boolean inRing,
            boolean spiritMarked,
            boolean playerMarked) {
        return new FenceDemolition.StubFacts(
                planned, inPlacement, inOrchard, inFenceStrip, inRing, spiritMarked, playerMarked);
    }
}
