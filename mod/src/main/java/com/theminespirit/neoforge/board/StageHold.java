package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStages;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class StageHold {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StageHold() {}

    static int release(ServerLevel level, BoardService service) {
        Board board = service.board();
        List<String> held = BoardStages.held(board);
        if (held.isEmpty()) {
            return 0;
        }
        String reason = "этап " + board.ledger().stage() + " не закрыт целиком: в мире не готово "
                + board.ledger().stageGate().missing();
        int released = 0;
        for (String id : held) {
            board.noteWait(id, reason);
            if (service.cancel(level, id)) {
                released++;
                LOGGER.info("[stage] задание {} снято: {} — вернётся после постройки этапа", id, reason);
            }
        }
        return released;
    }
}
