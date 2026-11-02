package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.DispatchRound;
import java.util.Objects;

final class StageOpen {
    private StageOpen() {}

    static boolean full(Board board, int spirits) {
        Objects.requireNonNull(board, "Доска не может быть null: предел открытых шагов считается по ней");
        if (board.ledger().stage() > 1) {
            return false;
        }
        return DispatchRound.stageCasesOpen(board) >= BoardTuning.stageCaseLimit(spirits);
    }
}
