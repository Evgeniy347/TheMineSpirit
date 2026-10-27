package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolLossReleaseTests {
    private static final UUID SPIRIT = new UUID(0, 1);

    private static Board boardWith(String id, BoardCase boardCase) {
        Board board = new Board(() -> 0);
        board.submit(new BoardTicket(
                id,
                boardCase,
                Optional.empty(),
                BoardRules.priorityOf(boardCase),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of()));
        board.claim(id, SPIRIT, 0);
        return board;
    }

    @Test
    void apply_MineHolderLostPickaxe_ReleasesToPending() {
        Board board = boardWith("first:build_mine", BoardCase.BUILD_MINE);

        List<ToolLossRelease.Released> released =
                ToolLossRelease.apply(board, SPIRIT, List.of("minecraft:oak_log"), false);

        assertEquals(
                List.of("first:build_mine"),
                released.stream().map(ToolLossRelease.Released::ticketId).toList(),
                "Дух без кирки бросает шахту (владелец 27.10, INC-94): иначе копает руками без булыжника и держит "
                        + "задание");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("first:build_mine"),
                "Снятая шахта ждёт на доске духа с киркой, а не проваливается");
    }

    @Test
    void apply_MineHolderWithPickaxe_Keeps() {
        Board board = boardWith("first:build_mine", BoardCase.BUILD_MINE);

        List<ToolLossRelease.Released> released =
                ToolLossRelease.apply(board, SPIRIT, List.of("minecraft:stone_pickaxe"), false);

        assertTrue(released.isEmpty(), "С киркой шахта остаётся за духом, снято " + released);
        assertEquals(
                Optional.of(BoardStatus.ASSIGNED), board.statusOf("first:build_mine"), "Дух с киркой продолжает шахту");
    }

    @Test
    void apply_WalledInWithoutPickaxe_ReleasesWithEscapeReason() {
        Board board = boardWith("first:clear", BoardCase.CLEAR_GROUNDS);

        List<ToolLossRelease.Released> released = ToolLossRelease.apply(board, SPIRIT, List.of(), true);

        assertTrue(
                released.size() == 1 && released.get(0).reason().contains("замурован"),
                "Замурованный без кирки отпускает задание с киркой и выбирается руками, снято " + released);
    }

    @Test
    void apply_BareHandsCaseWithoutTools_Keeps() {
        Board board = boardWith("first:logs", BoardCase.GATHER_MATERIALS);

        List<ToolLossRelease.Released> released = ToolLossRelease.apply(board, SPIRIT, List.of(), false);

        assertTrue(
                released.isEmpty(),
                "Сбор бревна под первые деревянные инструменты идёт голыми руками: его не снимают, снято " + released);
    }
}
