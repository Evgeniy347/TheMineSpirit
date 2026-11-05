package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.GatherDemand;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class GatherPost {
    private final GraphPost post;
    private final Map<String, GatherDemand.Need> needs = new LinkedHashMap<>();
    private final Set<String> suppressed = new HashSet<>();

    GatherPost(GraphPost post) {
        this.post = post;
    }

    void need(String id, String cause, int priority, String item, int count, BlockCoord at) {
        String canonical = canonicalId(item);
        if (canonical.startsWith(GraphPost.MAKE)
                && id.startsWith(GraphTickets.PREFIX)
                && !id.startsWith(GraphPost.MAKE)) {
            canonical = id;
        }
        needs.merge(
                canonical, new GatherDemand.Need(canonical, item, cause, count, priority, at), GatherDemand.Need::plus);
    }

    void flush() {
        List<GatherDemand.Need> merged = GatherDemand.merge(new ArrayList<>(needs.values()));
        List<GatherDemand.Need> chosen = GatherDemand.onePerWood(merged);
        Set<String> keep = new HashSet<>();
        chosen.forEach(need -> keep.add(need.id()));
        suppressed.clear();
        for (GatherDemand.Need need : merged) {
            if (GatherDemand.wood(need.item()) != null && !keep.contains(need.id())) {
                suppressed.add(need.id());
                suppressed.add(need.cause());
            }
        }
        needs.clear();
        chosen.forEach(this::submit);
    }

    Set<String> live(Set<String> live) {
        if (suppressed.isEmpty()) {
            return live;
        }
        Set<String> out = new HashSet<>(live);
        out.removeAll(suppressed);
        return Set.copyOf(out);
    }

    private void submit(GatherDemand.Need need) {
        int output = post.source.outputCount(need.item());
        int helpers = GraphPost.MINED.contains(need.item()) ? GraphCrew.miners(post.spirits) : post.spirits.size();
        List<Integer> portions = GatherBatch.split(GatherBatch.round(need.count(), output), output, helpers);
        for (int part = 0; part < portions.size(); part++) {
            String partId = part == 0 ? need.id() : need.id() + GraphPost.PART;
            if (post.service.board().statusOf(partId).orElse(null) == BoardStatus.DONE) {
                post.service.reopen(partId);
            }
            post.submit(
                    ToolGate.withTool(
                            post.ticket(
                                    partId,
                                    BoardCase.GATHER_MATERIALS,
                                    Optional.empty(),
                                    need.priority(),
                                    need.cause(),
                                    Map.of()),
                            need.item()),
                    TicketTarget.at(need.at()).withSupply(need.item(), portions.get(part)));
        }
    }

    private String canonicalId(String item) {
        for (SupplyGraph.Way way : post.graph.producers(item)) {
            if (way.world() != null) {
                return GraphTickets.PREFIX + way.id();
            }
        }
        for (SupplyGraph.Way way : post.graph.producers(item)) {
            return GraphTickets.PREFIX + way.id();
        }
        return GraphPost.MAKE + item;
    }
}
