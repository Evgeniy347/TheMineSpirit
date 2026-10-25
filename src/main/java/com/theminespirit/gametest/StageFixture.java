package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.StageRules;
import java.util.Set;

final class StageFixture {
    private StageFixture() {}

    static void reach(BoardLedger ledger, int stage, BoardCase... builtByScene) {
        Set<BoardCase> skipped = Set.of(builtByScene);
        for (StageRules.Stage done : StageRules.load().stages()) {
            if (done.stage() >= stage) {
                return;
            }
            done.buildings().forEach((boardCase, level) -> {
                if (!skipped.contains(boardCase)) {
                    ledger.note(boardCase, level);
                }
            });
        }
    }
}
