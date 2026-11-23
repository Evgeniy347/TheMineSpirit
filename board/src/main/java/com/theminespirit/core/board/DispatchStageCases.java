package com.theminespirit.core.board;

import com.theminespirit.core.board.api.BoardCase;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class DispatchStageCases {
    private DispatchStageCases() {}

    public static int open(Board board) {
        Objects.requireNonNull(board, "Доска не может быть null: открытые шаги этапа считают по ней");
        Set<BoardCase> cases = EnumSet.noneOf(BoardCase.class);
        for (BoardTicket ticket : board.tickets()) {
            if (!StageTicket.stage(ticket.id())) {
                continue;
            }
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED) {
                cases.add(ticket.boardCase());
            }
        }
        return cases.size();
    }
}
