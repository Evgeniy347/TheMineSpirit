package com.theminespirit.core.incident;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IncidentAimTests {
    private static final String ID = "supply_gather:oak_log";
    private static final BlockCoord HOME = new BlockCoord(-171, 71, -489);
    private static final BlockCoord TRUNK = new BlockCoord(-240, 55, -515);

    private static BoardTicket ticket() {
        return new BoardTicket(
                ID,
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static TicketTarget target() {
        return TicketTarget.at(HOME).withSupply("oak_log", 1);
    }

    @Test
    void describe_SupplyGatherWithTree_NamesGatherTargetNotHome() {
        String aim = IncidentAim.describe(ticket(), target(), List.of(new WorkTarget(WorkKind.TREE, TRUNK)));

        assertTrue(aim.contains(TRUNK.toString()), "Цель добычи — ствол, у которого дух рубит: " + aim);
        assertFalse(aim.contains(HOME.toString()), "Точка дома не цель добычи: она сбивает разбор инцидента: " + aim);
        assertTrue(aim.contains("oak_log"), "Причина называет предмет добычи: " + aim);
    }

    @Test
    void describe_SupplyGatherWithoutReservation_DoesNotPretendHomeIsTarget() {
        String aim = IncidentAim.describe(ticket(), target(), List.of());

        assertFalse(aim.contains(HOME.toString()), "Пока ствол не выбран, дом не выдаётся за цель добычи: " + aim);
        assertTrue(aim.contains("точка ещё не выбрана"), "Строка честно говорит, что цель ещё ищется: " + aim);
    }

    @Test
    void describe_NonSupplyTicket_KeepsTargetAndCount() {
        BoardTicket build = new BoardTicket(
                "first:build_fence",
                BoardCase.BUILD_FENCE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_FENCE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());

        String aim = IncidentAim.describe(build, TicketTarget.at(HOME).withCount(2), List.of());

        assertTrue(aim.contains(HOME.toString()), "У обычного задания цель остаётся прежней: " + aim);
        assertTrue(aim.contains("часть 2"), "Счётная часть остаётся в тексте: " + aim);
    }
}
