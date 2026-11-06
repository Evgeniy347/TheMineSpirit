package com.theminespirit.neoforge.board;

import com.theminespirit.core.mining.UnreachableCells;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StrayGroups;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

final class PlanStrays {
    record Result(List<BlockCoord> remove, List<String> notes, int unreachable, int protectedCount) {
        Result {
            remove = List.copyOf(remove);
            notes = List.copyOf(notes);
        }

        static Result empty() {
            return new Result(List.of(), List.of(), 0, 0);
        }
    }

    private PlanStrays() {}

    static Result collect(ServerLevel level, UnreachableCells unreachableCells, int shown) {
        if (!WorldAreaScan.demolishStrays()) {
            return Result.empty();
        }
        List<StrayGroups.Cell> strays =
                WorldAreaScan.last(level).map(SettlementScan.Strays::strays).orElse(List.of());
        List<BlockCoord> remove = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int unreachable = 0;
        int guarded = 0;
        for (StrayGroups.Cell cell : strays) {
            BlockCoord at = cell.at();
            if (unreachableCells.excluded(at) || !FloatingScan.reachable(level, at)) {
                if (unreachable++ < shown) {
                    notes.add(at + " — лишнее вне плана, но рядом нет стоянки");
                }
                continue;
            }
            if (DemolitionGuard.protectedStray(level, at, cell.blockId())) {
                if (guarded++ < shown) {
                    notes.add(at + " — постройка игрока, не трогаем: " + cell.blockId());
                }
                continue;
            }
            if (notes.size() < shown) {
                notes.add(at + " — " + cell.blockId() + " вне плана внутри ограды: в уборку");
            }
            remove.add(at);
            DemolitionGuard.claimStray(level, at);
        }
        return new Result(remove, notes, unreachable, guarded);
    }
}
