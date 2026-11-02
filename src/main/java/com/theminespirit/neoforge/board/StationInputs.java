package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.StationFacts;
import com.theminespirit.neoforge.crafting.FurnaceSession;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class StationInputs {
    private StationInputs() {}

    static GraphFacts.Station station(String id, GraphFacts.@Nullable Position position, ServerLevel level) {
        if (position == null) {
            return new GraphFacts.Station(id, id, null, false, null, null, null);
        }
        Optional<FurnaceSession.Contents> contents =
                FurnaceSession.contentsAt(level, new BlockPos(position.x(), position.y(), position.z()));
        if (contents.isEmpty()) {
            return new GraphFacts.Station(id, id, position, false, null, null, null);
        }
        FurnaceSession.Contents loaded = contents.orElseThrow();
        return StationFacts.withContents(id, position, loaded.input(), loaded.fuel(), loaded.result());
    }
}
