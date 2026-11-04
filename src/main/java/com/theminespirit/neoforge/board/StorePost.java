package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StoreTransfer;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class StorePost {
    private static final String DELIVER = "carry_deliver";
    private static final String TAKE = "store_smelt_take";
    private static final String SORT = "store_sort";
    private static final int FURNACE_BATCH = 8;

    private StorePost() {}

    static boolean way(GraphPost post, GraphTickets.Ticket ticket) {
        String node = ticket.node();
        if (node.contains(DELIVER) || "store_carry_transfer".equals(node) || "transfer_item".equals(node)) {
            return transfer(post, ticket);
        }
        if (TAKE.equals(node)) {
            return takeSmelted(post);
        }
        if (SORT.equals(node)) {
            return sort(post);
        }
        return false;
    }

    static boolean event(GraphPost post, GraphTickets.Ticket ticket) {
        return switch (ticket.node()) {
            case "transfer_pending" -> transfer(post, ticket);
            case "storage_mixed" -> sort(post);
            case "furnace_ready" -> takeSmelted(post);
            case "storage_full" -> ChestSpaceWatch.post(post);
            default -> false;
        };
    }

    private static boolean transfer(GraphPost post, GraphTickets.Ticket ticket) {
        SpiritEntity spirit = idle(post).orElse(null);
        if (spirit == null) {
            return false;
        }
        if (ticket.node().contains(DELIVER)) {
            if (!StoreTransfer.hasFreeChest(post.facts.chests())
                    || post.facts.spirits().values().stream()
                            .noneMatch(fact -> !fact.inventory().isEmpty())) {
                return false;
            }
            GraphFacts.Chest to = post.facts.chests().stream()
                    .filter(chest -> chest.freeSlots() > 0)
                    .findFirst()
                    .orElseThrow();
            return launch(post, spirit, ActivityPlans.unload(at(to.position())));
        }
        Optional<StoreTransfer.Move> move = StoreTransfer.chestMove(post.facts.chests());
        if (move.isEmpty()) {
            return false;
        }
        StoreTransfer.Move known = move.orElseThrow();
        BlockCoord from = at(known.from());
        return launch(post, spirit, ActivityPlans.moveItem(from, at(known.to()), from, known.item(), known.count()));
    }

    private static boolean takeSmelted(GraphPost post) {
        Optional<GraphFacts.Station> furnace = furnace(post.level, post.facts, true);
        if (furnace.isEmpty()) {
            return false;
        }
        SpiritEntity spirit = idle(post).orElse(null);
        if (spirit == null) {
            return false;
        }
        GraphFacts.Station station = furnace.orElseThrow();
        BlockCoord point = at(station.position());
        return launch(post, spirit, ActivityPlans.withdraw(point, point, station.output(), FURNACE_BATCH));
    }

    private static boolean sort(GraphPost post) {
        Optional<GraphFacts.Chest> chest = post.facts.chests().stream().findFirst();
        if (chest.isEmpty()) {
            return false;
        }
        SpiritEntity spirit = idle(post).orElse(null);
        if (spirit == null) {
            return false;
        }
        return launch(
                post, spirit, ActivityPlans.sortChest(at(chest.orElseThrow().position())));
    }

    private static boolean launch(GraphPost post, SpiritEntity spirit, ActivityPlans.Plan plan) {
        return post.launchActivity(spirit, BoardCase.ACTIVITY_UNLOAD, plan);
    }

    private static Optional<GraphFacts.Station> furnace(ServerLevel level, GraphFacts facts, boolean withOutput) {
        return facts.stations().values().stream()
                .filter(station -> "furnace".equals(station.type()))
                .filter(station -> station.position() != null)
                .filter(station -> !withOutput || station.output() != null)
                .filter(station -> level.isLoaded(new BlockPos(
                        station.position().x(),
                        station.position().y(),
                        station.position().z())))
                .findFirst();
    }

    static Optional<SpiritEntity> idle(GraphPost post) {
        return post.spirits.stream()
                .filter(spirit -> !spirit.isRemoved())
                .filter(spirit ->
                        post.service.board().assignedTo(spirit.getUUID()).isEmpty())
                .filter(spirit ->
                        post.service.board().occupation(spirit.getUUID()).isEmpty())
                .findFirst();
    }

    static BlockCoord at(GraphFacts.Position position) {
        return new BlockCoord(position.x(), position.y(), position.z());
    }
}
