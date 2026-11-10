package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.LevelNavWorld;
import com.theminespirit.neoforge.movement.RescueReach;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class GraphRescuePost {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String RESCUE = "rescue:";

    record HelperChoice(Optional<UUID> id, String why, boolean pending) {}

    private GraphRescuePost() {}

    static boolean rescue(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        return RescueGroupPost.rescue(post, task);
    }

    static String refusal(RescueTicket.Escape escape, HelperChoice helper, int failures) {
        if (!escape.self()) {
            return escape.why();
        }
        if (helper.id().isPresent() && failures > 0) {
            return "выход сам не удался (" + escape.why() + ")";
        }
        return null;
    }

    static String lifecycle(
            RescuePlans.Assignment assignment,
            BlockCoord at,
            long since,
            RescueTicket.Escape escape,
            HelperChoice helper) {
        String mode = switch (assignment.mode()) {
            case SELF -> "выходит сам: " + escape.why();
            case HELPER -> "помощник: " + helper.why();
            case IMPOSSIBLE -> "невозможен: " + assignment.why() + "; " + helper.why();
        };
        return "в " + at + ": " + mode + "; метка с тика " + since;
    }

    static HelperChoice helper(GraphPost post, String rescuee, BlockCoord trapped) {
        RescueReach.Cache cache = post.service.sources.rescueReach();
        LevelNavWorld world = new LevelNavWorld(post.level);
        Predicate<BlockCoord> water = cell -> post.level
                .getFluidState(new BlockPos(cell.x(), cell.y(), cell.z()))
                .is(FluidTags.WATER);
        @Nullable String firstRefusal = null;
        boolean pending = false;
        for (SpiritEntity other : post.spirits) {
            if (other.isRemoved() || other.getUUID().toString().equals(rescuee)) {
                continue;
            }
            GraphFacts.Spirit fact = post.facts.spirits().get(other.getUUID().toString());
            if (fact == null || !fact.stuck()) {
                BlockCoord from = at(other);
                RescueReach.Verdict verdict = cache.verdict(other.getUUID(), from, trapped, post.now);
                if (verdict == null) {
                    cache.request(world, water, other.getUUID(), from, trapped, post.now);
                    pending = true;
                    continue;
                }
                if (verdict.reachable()) {
                    return new HelperChoice(
                            Optional.of(other.getUUID()), other.displayName() + " — " + verdict.why(), false);
                }
                if (firstRefusal == null) {
                    firstRefusal = other.displayName() + " — " + verdict.why();
                }
            }
        }
        if (pending) {
            return new HelperChoice(Optional.empty(), "поиск пути идёт", true);
        }
        return new HelperChoice(
                Optional.empty(), firstRefusal == null ? "свободного помощника нет" : firstRefusal, false);
    }

    static @Nullable SpiritEntity entity(GraphPost post, String id) {
        for (SpiritEntity spirit : post.spirits) {
            if (spirit.getUUID().toString().equals(id)) {
                return spirit;
            }
        }
        return null;
    }

    static BlockCoord at(SpiritEntity spirit) {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    static void say(GraphPost post, SpiritEntity spirit, long since, String text) {
        String key = "rescue:" + spirit.getUUID() + "@" + since;
        String line = "rescue:" + spirit.getUUID() + " для " + spirit.displayName() + ": " + text;
        if (post.service.sources.due(key, line, post.now)) {
            LOGGER.info("[rescue] {}", line);
        }
    }
}
