package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphFeedback;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;

final class GraphWorldFeedbackInputs {
    private static final int PLAYER_RADIUS = 16;

    private GraphWorldFeedbackInputs() {}

    static GraphFeedback of(ServerLevel level, List<SpiritEntity> spirits, BlockCoord home, BoardService service) {
        return new GraphFeedback(
                GraphFeedback.Clock.of(level.getDayTime()),
                player(level, spirits, home),
                service.sources
                        .graphFailures()
                        .facts(
                                service.board().now(),
                                DataTables.INSTANCE
                                        .graph()
                                        .current()
                                        .costs()
                                        .choice()
                                        .failureHalfLife()));
    }

    private static GraphFeedback.Player player(ServerLevel level, List<SpiritEntity> spirits, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        long limit = (long) PLAYER_RADIUS * PLAYER_RADIUS;
        for (ServerPlayer player : level.players()) {
            BlockPos at = player.blockPosition();
            boolean near = spirits.isEmpty()
                    ? center.distSqr(at) <= limit
                    : spirits.stream().anyMatch(spirit -> spirit.blockPosition().distSqr(at) <= limit);
            if (!near) {
                continue;
            }
            ItemStack hand = player.getMainHandItem();
            boolean holdingBlock = !hand.isEmpty() && hand.getItem() instanceof BlockItem;
            boolean usingItem = player.isUsingItem();
            boolean digging = player.swinging && !hand.isEmpty() && hand.getItem() instanceof DiggerItem;
            return new GraphFeedback.Player(
                    new GraphFacts.Position(at.getX(), at.getY(), at.getZ()),
                    true,
                    holdingBlock,
                    usingItem,
                    digging,
                    player.getHealth(),
                    player.getMaxHealth(),
                    player.getFoodData().getFoodLevel());
        }
        return GraphFeedback.Player.none();
    }
}
