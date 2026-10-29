package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

final class FenceParts {
    private FenceParts() {}

    static List<StageBuilds.FencePart> of(ServerLevel level, StageSite site, int count) {
        List<StageBuilds.FencePart> parts = new ArrayList<>(count);
        for (int part = 1; part <= count; part++) {
            parts.add(StageBuilds.fencePart(level, site, part));
        }
        return List.copyOf(parts);
    }

    static List<Integer> open(List<StageBuilds.FencePart> parts) {
        List<Integer> ready = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            StageBuilds.FencePart part = parts.get(index);
            if (part.work().blocks().isEmpty() && part.work().builds().isEmpty()) {
                if (part.hasPieces()) {
                    break;
                }
                continue;
            }
            ready.add(index + 1);
        }
        return List.copyOf(ready);
    }

    static boolean done(BoardService service, List<StageBuilds.FencePart> parts) {
        List<BoardStatus> statuses = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            if (!parts.get(index).hasPieces()) {
                continue;
            }
            statuses.add(service.board()
                    .statusOf(FirstStepsRunner.partId(BoardCase.BUILD_FENCE, index + 1))
                    .orElse(null));
        }
        return statuses.isEmpty() || StageAudit.allDone(statuses);
    }
}
