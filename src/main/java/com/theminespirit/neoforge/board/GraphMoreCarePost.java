package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.intellect.FoodRation;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

final class GraphMoreCarePost {
    private static final String BONE_MEAL = "bone_meal";
    private static final String PLAYER = "player:";
    private static final String TORCH = "torch";

    private GraphMoreCarePost() {}

    static boolean defendPlayer(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<ServerPlayer> player = nearestPlayer(post);
        if (player.isEmpty()) {
            return false;
        }
        ServerPlayer target = player.orElseThrow();
        GraphMorePost.submit(
                post,
                ticket,
                BoardCase.ACTIVITY_DEFEND,
                TicketTarget.at(coord(target.blockPosition()))
                        .withBuilding(PLAYER + target.getName().getString()),
                Map.of());
        return true;
    }

    static boolean feedPlayer(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<String> food = food(post.stock);
        Optional<ServerPlayer> player = nearestPlayer(post);
        if (food.isEmpty() || player.isEmpty()) {
            return false;
        }
        ServerPlayer target = player.orElseThrow();
        String item = food.orElseThrow();
        GraphMorePost.submit(
                post,
                ticket,
                BoardCase.ACTIVITY_SOCIAL,
                TicketTarget.at(coord(target.blockPosition()))
                        .withSupply(item, 1)
                        .withCount(1)
                        .withBuilding(PLAYER + target.getName().getString()),
                Map.of(item, 1));
        return true;
    }

    static boolean light(GraphPost post, GraphTickets.Ticket ticket, BlockCoord at) {
        GraphMorePost.submit(post, ticket, BoardCase.LIGHTING, TicketTarget.at(at), Map.of(TORCH, 1));
        return true;
    }

    static boolean fertilize(GraphPost post, GraphTickets.Ticket ticket) {
        GraphMorePost.submit(
                post, ticket, BoardCase.ACTIVITY_FARM, TicketTarget.at(post.scan.home()), Map.of(BONE_MEAL, 1));
        return true;
    }

    private static Optional<String> food(StockView stock) {
        Map<String, Integer> available = new HashMap<>();
        stock.available()
                .forEach((item, count) ->
                        available.merge(item.indexOf(':') < 0 ? "minecraft:" + item : item, count, Integer::sum));
        return FoodRation.choose(available, StockCatalog.defaultTable(), false)
                .filter(choice -> !choice.needsCooking())
                .map(FoodRation.Choice::item);
    }

    private static Optional<ServerPlayer> nearestPlayer(GraphPost post) {
        BlockPos home = new BlockPos(
                post.scan.home().x(), post.scan.home().y(), post.scan.home().z());
        return post.level.players().stream()
                .min(Comparator.comparingDouble(player -> player.blockPosition().distSqr(home)));
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
