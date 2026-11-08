package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.MoreActions;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

final class GraphMorePost {
    private static final String SAPLINGS = "#saplings";
    private static final String APPLE = "apple";
    private static final String CHEST = "chest";
    private static final String BRIDGE = "bridge";
    private static final String COBBLESTONE = "cobblestone";
    private static final int FUEL_BATCH = 8;
    private static final int BUILD_BATCH = 16;

    private GraphMorePost() {}

    static boolean handles(GraphTickets.Ticket ticket) {
        return MoreActions.handles(ticket);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String key = MoreActions.key(ticket);
        if (GraphMoreFarmPost.handles(key)) {
            return GraphMoreFarmPost.post(post, ticket, task, key);
        }
        if (GraphFluidPost.handles(key)) {
            return GraphFluidPost.post(post, ticket, key);
        }
        return switch (key) {
            case "more5_feed_player" -> GraphMoreCarePost.feedPlayer(post, ticket);
            case "more5_light_crops" -> GraphMoreCarePost.light(post, ticket, post.scan.home());
            case "more5_light_saplings" ->
                GraphMoreCarePost.light(post, ticket, post.scan.stage().orchardOrigin());
            case "more5_fertilize_growth" -> GraphMoreCarePost.fertilize(post, ticket);
            case "more5_gather_fuel" -> gatherFuel(post, ticket);
            case "more5_make_charcoal" -> new GraphCrafting(post).charcoal(ticket, task);
            case "more5_gather_build_material" -> gatherBuild(post, ticket, false);
            case "more5_craft_build_material" -> gatherBuild(post, ticket, true);
            case "more5_restore_block" -> restoreBlock(post, ticket);
            case "more5_bridge_route" -> bridgeRoute(post, ticket);
            case "more5_place_site_chest" -> placeSiteChest(post, ticket);
            case "more5_carry_to_site" -> carryToSite(post, ticket);
            case "more5_light_mine" ->
                GraphMoreCarePost.light(post, ticket, post.scan.stage().mineEntrance());
            case "more5_craft_mine_torch" -> new GraphCrafting(post).torches(ticket, task);
            case "more5_pickup_wood_drop" -> pickupWoodDrop(post, ticket);
            case "more5_plant_sapling" -> plantSapling(post, ticket);
            default -> false;
        };
    }

    private static boolean gatherFuel(GraphPost post, GraphTickets.Ticket ticket) {
        String wood = post.scan.wood();
        Optional<BlockCoord> tree = treeSpot(post, wood);
        post.gather(
                ticket.id(),
                ticket.id(),
                ticket.task().priority(),
                wood + "_log",
                batch(ticket),
                tree.orElseGet(post::craftPoint));
        return true;
    }

    private static Optional<BlockCoord> treeSpot(GraphPost post, String wood) {
        Optional<BlockCoord> preferred = post.around.resource("tree:" + wood);
        if (preferred.isPresent()) {
            return preferred;
        }
        return post.around.nearest().keySet().stream()
                .filter(tag -> tag.startsWith("tree:"))
                .sorted()
                .map(post.around::resource)
                .flatMap(Optional::stream)
                .findFirst()
                .map(at -> GraphTargets.stand(post.level, at));
    }

    private static boolean gatherBuild(GraphPost post, GraphTickets.Ticket ticket, boolean planks) {
        String wood = post.scan.wood();
        String item = planks || !post.graph.items().containsKey(COBBLESTONE) ? wood + "_planks" : COBBLESTONE;
        post.gather(ticket.id(), ticket.id(), ticket.task().priority(), item, batch(ticket), post.craftPoint());
        return true;
    }

    private static boolean restoreBlock(GraphPost post, GraphTickets.Ticket ticket) {
        More5Store store = More5Store.get(post.level);
        BlockCoord home = post.scan.home();
        List<More5Store.Broken> broken = store.recentBreaks(post.now).stream()
                .sorted(Comparator.comparingLong(entry -> distance(home, entry.position())))
                .toList();
        for (More5Store.Broken entry : broken) {
            BlockPos pos = new BlockPos(entry.x(), entry.y(), entry.z());
            BlockState state = post.level.getBlockState(pos);
            if (!state.isAir()) {
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if (id.equals(entry.block())) {
                    store.forget(entry.position());
                }
                continue;
            }
            submit(
                    post,
                    ticket,
                    BoardCase.ACTIVITY_BUILD,
                    TicketTarget.at(entry.position()).withSupply(entry.block(), 1),
                    Map.of());
            return true;
        }
        return false;
    }

    private static boolean bridgeRoute(GraphPost post, GraphTickets.Ticket ticket) {
        String planks = post.scan.wood() + "_planks";
        submit(
                post,
                ticket,
                BoardCase.ACTIVITY_BUILD,
                TicketTarget.at(workSite(post)).withSupply(planks, FUEL_BATCH).withBuilding(BRIDGE),
                Map.of(planks, FUEL_BATCH));
        return true;
    }

    private static boolean placeSiteChest(GraphPost post, GraphTickets.Ticket ticket) {
        if (WarehouseRules.builtLevel(post.service.board().ledger()) > 0) {
            return true;
        }
        submit(
                post,
                ticket,
                BoardCase.ACTIVITY_BUILD,
                TicketTarget.at(workSite(post)).withSupply(CHEST, 1),
                Map.of());
        return true;
    }

    private static boolean carryToSite(GraphPost post, GraphTickets.Ticket ticket) {
        String wood = post.scan.wood();
        String item = post.graph.items().containsKey(COBBLESTONE) ? COBBLESTONE : wood + "_planks";
        BlockCoord site = workSite(post);
        submit(
                post,
                ticket,
                BoardCase.SUPPLY_SITE,
                TicketTarget.at(site).withSupply(item, BUILD_BATCH).withDeposit(site),
                Map.of(item, BUILD_BATCH));
        return true;
    }

    private static BlockCoord workSite(GraphPost post) {
        return post.scan.stage().mineEntrance();
    }

    private static boolean pickupWoodDrop(GraphPost post, GraphTickets.Ticket ticket) {
        Map<String, BlockCoord> drops = post.around.drops();
        for (String item : drops.keySet().stream().sorted().toList()) {
            if (!saplings(post).contains(item) && !APPLE.equals(item)) {
                continue;
            }
            submit(
                    post,
                    ticket,
                    BoardCase.COLLECT_DROPS,
                    TicketTarget.at(drops.get(item)).withDrops(List.of(item)),
                    Map.of());
            return true;
        }
        return false;
    }

    private static boolean plantSapling(GraphPost post, GraphTickets.Ticket ticket) {
        String sapling = sapling(post);
        for (BlockCoord spot : post.scan.stage().allOrchardSaplings()) {
            BlockPos pos = new BlockPos(spot.x(), spot.y(), spot.z());
            if (!post.level.isLoaded(pos) || !post.level.getBlockState(pos).isAir()) {
                continue;
            }
            submit(post, ticket, BoardCase.ACTIVITY_BUILD, TicketTarget.at(spot).withSupply(sapling, 1), Map.of());
            return true;
        }
        return false;
    }

    private static String sapling(GraphPost post) {
        String wood = post.scan.wood();
        Set<String> saplings = saplings(post);
        String preferred = wood + "_sapling";
        if (saplings.contains(preferred)) {
            return GraphNames.item(post.graph, preferred, wood);
        }
        return saplings.stream()
                .sorted()
                .map(member -> GraphNames.item(post.graph, member, wood))
                .findFirst()
                .orElse(GraphNames.item(post.graph, preferred, wood));
    }

    private static Set<String> saplings(GraphPost post) {
        return post.graph.isGroup(SAPLINGS) ? Set.copyOf(post.graph.members(SAPLINGS)) : Set.of();
    }

    static void submit(
            GraphPost post,
            GraphTickets.Ticket ticket,
            BoardCase boardCase,
            TicketTarget target,
            Map<String, Integer> required) {
        post.submit(
                post.ticket(
                        ticket.id(), boardCase, Optional.empty(), ticket.task().priority(), ticket.id(), required),
                target);
    }

    static int batch(GraphTickets.Ticket ticket) {
        return Math.max(1, Math.min(ticket.count(), BUILD_BATCH));
    }

    private static long distance(BlockCoord first, BlockCoord second) {
        long dx = (long) first.x() - second.x();
        long dy = (long) first.y() - second.y();
        long dz = (long) first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
