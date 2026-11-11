package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphSiteFacts;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseCell;
import com.theminespirit.core.board.lease.LeaseUndo;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.lease.LeaseData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

final class LeaseInputs {
    private LeaseInputs() {}

    static List<GraphSiteFacts.Lease> of(ServerLevel level, StageSite site) {
        List<GraphSiteFacts.Lease> facts = new ArrayList<>();
        for (Lease lease : LeaseData.get(level).book().orphaned()) {
            if (lease.cells().isEmpty()) {
                continue;
            }
            BlockCoord top = lease.cells().stream()
                    .map(LeaseCell::pos)
                    .max(Comparator.comparingInt(BlockCoord::y)
                            .thenComparingInt(BlockCoord::x)
                            .thenComparingInt(BlockCoord::z))
                    .orElseThrow();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.x(), top.z());
            int depth = Math.max(0, ground - top.y());
            boolean inside = lease.cells().stream()
                    .anyMatch(cell ->
                            site.zone().contains(cell.pos().x(), cell.pos().z()));
            boolean street = lease.cells().stream()
                    .anyMatch(cell ->
                            site.streets().contains(cell.pos().x(), cell.pos().z()));
            int dx = top.x() - site.anchor().x();
            int dz = top.z() - site.anchor().z();
            facts.add(new GraphSiteFacts.Lease(
                    lease.id(),
                    lease.purpose(),
                    lease.spirit() == null ? null : lease.spirit().toString(),
                    new GraphFacts.Position(top.x(), top.y(), top.z()),
                    Math.max(0, top.y() - ground),
                    inside,
                    street,
                    lease.cells().size(),
                    (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz)),
                    depth,
                    LeaseUndo.key(lease)));
        }
        return List.copyOf(facts);
    }
}
