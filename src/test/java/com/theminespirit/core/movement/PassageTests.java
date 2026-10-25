package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PassageTests {
    private static final BlockCoord FEET = new BlockCoord(0, 0, 0);
    private static final BlockCoord TARGET = new BlockCoord(6, 0, 0);

    @Test
    void passable_ClosedAndOpenDoorAndGate_ClosedBlocksOpenLetsThrough() {
        for (Passage.Kind kind : Passage.Kind.values()) {
            Passage.Cell closed = new Passage.Cell(FEET, kind, false);
            Passage.Cell open = new Passage.Cell(FEET, kind, true);

            assertFalse(
                    closed.passable(),
                    "Закрытая " + kind + " не проходима: дух обязан сначала открыть створку, иначе упрётся и "
                            + "телепортируется сквозь неё");
            assertTrue(open.passable(), "Открытая " + kind + " проходима: створка не мешает шагу");
            assertTrue(
                    closed.routePassable(),
                    "Закрытая " + kind + " — законный проход для планирования: дух подойдёт и откроет её, а не обойдёт "
                            + "стену телепортом");
        }
    }

    @Test
    void next_ClosedGate_ApproachOpenPassCloseDone() {
        boolean open = false;
        boolean withinReach = false;
        boolean passed = false;
        List<Passage.Step> steps = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Passage.Step step = Passage.next(new Passage.State(open, withinReach, passed));
            steps.add(step);
            switch (step) {
                case APPROACH -> withinReach = true;
                case OPEN -> open = true;
                case PASS -> passed = true;
                case CLOSE -> open = false;
                case DONE -> {}
            }
        }

        assertEquals(
                List.of(
                        Passage.Step.APPROACH,
                        Passage.Step.OPEN,
                        Passage.Step.PASS,
                        Passage.Step.CLOSE,
                        Passage.Step.DONE),
                steps,
                "План закрытой калитки: подойти, открыть, пройти, закрыть за собой; иначе калитка остаётся открытой "
                        + "или дух телепортируется вместо прохода");
    }

    @Test
    void towardTarget_ClosedGateInFence_ChosenInsteadOfTeleport() {
        Passage.Cell gate = new Passage.Cell(new BlockCoord(3, 0, 1), Passage.Kind.FENCE_GATE, false);
        Passage.Cell doorBehind = new Passage.Cell(new BlockCoord(-2, 0, 0), Passage.Kind.DOOR, false);
        Passage.Cell openGate = new Passage.Cell(new BlockCoord(2, 0, 0), Passage.Kind.FENCE_GATE, true);

        Optional<Passage.Cell> pick = Passage.towardTarget(FEET, TARGET, List.of(doorBehind, openGate, gate));

        assertEquals(
                Optional.of(gate),
                pick,
                "Калитка в ограде — ближайший законный проход к цели: выбирается она, а не перенос сквозь забор; "
                        + "дверь позади и уже открытая калитка не годятся");
    }

    @Test
    void blockedBetween_ClosedPassageOnTeleportLine_StopsTheTransfer() {
        BlockCoord to = new BlockCoord(3, 0, 0);
        Passage.Cell closedGate = new Passage.Cell(new BlockCoord(1, 0, 0), Passage.Kind.FENCE_GATE, false);
        Passage.Cell openGate = new Passage.Cell(new BlockCoord(1, 0, 0), Passage.Kind.FENCE_GATE, true);
        Passage.Cell aside = new Passage.Cell(new BlockCoord(1, 0, 2), Passage.Kind.DOOR, false);

        assertTrue(
                Passage.blockedBetween(FEET, to, List.of(closedGate)),
                "Закрытая калитка на отрезке переноса запрещает перенос: дух обязан подойти и открыть её, иначе "
                        + "проходит сквозь ограду");
        assertFalse(
                Passage.blockedBetween(FEET, to, List.of(openGate)),
                "Открытая калитка на отрезке переноса не запрещает перенос: створка не мешает");
        assertFalse(
                Passage.blockedBetween(FEET, to, List.of(aside)),
                "Закрытая дверь в стороне от отрезка переноса не мешает: запрет только на путь сквозь створку");
    }
}
