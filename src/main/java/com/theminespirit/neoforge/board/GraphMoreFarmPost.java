package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StoreMinimums;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class GraphMoreFarmPost {
    private static final String MORE5_FUEL = "#more5_fuel";
    private static final int FUEL_BATCH = 8;
    private static final int BUILD_BATCH = 16;
    private static final int STATION_SCAN_RADIUS = 12;
    private static final int STATION_SCAN_DOWN = 3;
    private static final int STATION_SCAN_UP = 2;
    private static final Set<String> KEYS = Set.of(
            "more6_refuel_station",
            "more6_place_station",
            "more6_pickup_items",
            "more6_deposit_materials",
            "more6_maintain_stock",
            "more6_assign_idle",
            "more7_harvest_early",
            "more7_expand_pen",
            "more7_return_home",
            "more7_order_to_chest");

    private GraphMoreFarmPost() {}

    static boolean handles(String key) {
        return KEYS.contains(key);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task, String key) {
        return switch (key) {
            case "more6_refuel_station" -> refuelStation(post, ticket);
            case "more6_place_station" -> placeStation(post, ticket);
            case "more6_pickup_items" -> pickupItems(post, ticket);
            case "more6_deposit_materials" -> unload(post, ticket);
            case "more6_maintain_stock" -> maintainStock(post, ticket);
            case "more6_assign_idle" -> sense(post, ticket);
            case "more7_harvest_early" -> new GraphHarvestPost(post).orchard(ticket, task);
            case "more7_expand_pen" -> expandPen(post, ticket);
            case "more7_return_home" -> returnHome(post, ticket);
            case "more7_order_to_chest" -> unload(post, ticket);
            default -> false;
        };
    }

    private static boolean refuelStation(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<BlockCoord> furnace = station(post);
        Optional<String> fuel = fuel(post);
        if (furnace.isEmpty() || fuel.isEmpty()) {
            return false;
        }
        String item = fuel.orElseThrow();
        GraphMorePost.submit(
                post,
                ticket,
                BoardCase.ACTIVITY_SMELT,
                TicketTarget.at(furnace.orElseThrow())
                        .withSupply(item, FUEL_BATCH)
                        .withCount(FUEL_BATCH),
                Map.of(item, 1));
        return true;
    }

    private static Optional<String> fuel(GraphPost post) {
        Map<String, Integer> available = post.stock.available();
        return post.graph.members(MORE5_FUEL).stream()
                .filter(item -> available.getOrDefault(item, 0) > 0)
                .sorted()
                .findFirst();
    }

    private static Optional<BlockCoord> station(GraphPost post) {
        BlockCoord yard = post.scan.stage().workYard();
        BlockCoord best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int dx = -STATION_SCAN_RADIUS; dx <= STATION_SCAN_RADIUS; dx++) {
            for (int dz = -STATION_SCAN_RADIUS; dz <= STATION_SCAN_RADIUS; dz++) {
                for (int dy = -STATION_SCAN_DOWN; dy <= STATION_SCAN_UP; dy++) {
                    BlockPos at = new BlockPos(yard.x() + dx, yard.y() + dy, yard.z() + dz);
                    if (!post.level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = post.level.getBlockState(at);
                    if (!state.is(Blocks.FURNACE) && !state.is(Blocks.SMOKER)) {
                        continue;
                    }
                    long distance = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new BlockCoord(at.getX(), at.getY(), at.getZ());
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean placeStation(GraphPost post, GraphTickets.Ticket ticket) {
        BlockCoord yard = post.scan.stage().workYard();
        if (craftingTable(post, yard)) {
            return true;
        }
        GraphMorePost.submit(post, ticket, BoardCase.CRAFTING_STATION, TicketTarget.at(yard), Map.of());
        return true;
    }

    private static boolean craftingTable(GraphPost post, BlockCoord yard) {
        for (int dx = -STATION_SCAN_RADIUS; dx <= STATION_SCAN_RADIUS; dx++) {
            for (int dz = -STATION_SCAN_RADIUS; dz <= STATION_SCAN_RADIUS; dz++) {
                for (int dy = -STATION_SCAN_DOWN; dy <= STATION_SCAN_UP; dy++) {
                    BlockPos at = new BlockPos(yard.x() + dx, yard.y() + dy, yard.z() + dz);
                    if (post.level.isLoaded(at) && post.level.getBlockState(at).is(Blocks.CRAFTING_TABLE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean pickupItems(GraphPost post, GraphTickets.Ticket ticket) {
        Map<String, BlockCoord> drops = post.around.drops();
        if (drops.isEmpty()) {
            return false;
        }
        String item = drops.keySet().stream().sorted().findFirst().orElseThrow();
        GraphMorePost.submit(
                post,
                ticket,
                BoardCase.COLLECT_DROPS,
                TicketTarget.at(drops.get(item)).withDrops(List.of(item)),
                Map.of());
        return true;
    }

    private static boolean unload(GraphPost post, GraphTickets.Ticket ticket) {
        GraphMorePost.submit(post, ticket, BoardCase.ACTIVITY_UNLOAD, TicketTarget.at(post.scan.home()), Map.of());
        return true;
    }

    private static boolean maintainStock(GraphPost post, GraphTickets.Ticket ticket) {
        Map<String, Integer> available = post.stock.available();
        StoreMinimums minimums = StoreMinimums.loadDefault();
        for (Map.Entry<String, StoreMinimums.Entry> entry : minimums.items().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            String item = entry.getKey();
            if (item.startsWith("#")) {
                continue;
            }
            int have = available.getOrDefault(item, available.getOrDefault("minecraft:" + item, 0));
            if (have >= entry.getValue().target()) {
                continue;
            }
            int count = Math.max(1, Math.min(entry.getValue().target() - have, BUILD_BATCH));
            post.gather(ticket.id(), ticket.id(), ticket.task().priority(), item, count, post.craftPoint());
            return true;
        }
        return false;
    }

    private static boolean sense(GraphPost post, GraphTickets.Ticket ticket) {
        GraphMorePost.submit(post, ticket, BoardCase.ACTIVITY_SENSE, TicketTarget.at(post.scan.home()), Map.of());
        return true;
    }

    private static boolean expandPen(GraphPost post, GraphTickets.Ticket ticket) {
        GraphMorePost.submit(post, ticket, BoardCase.BUILD_PEN, TicketTarget.at(post.scan.home()), Map.of());
        return true;
    }

    private static boolean returnHome(GraphPost post, GraphTickets.Ticket ticket) {
        GraphMorePost.submit(post, ticket, BoardCase.ACTIVITY_RETURN, TicketTarget.at(post.scan.home()), Map.of());
        return true;
    }
}
