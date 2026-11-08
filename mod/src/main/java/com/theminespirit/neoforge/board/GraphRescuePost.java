package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.board.StuckSpirits;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.LevelNavWorld;
import com.theminespirit.neoforge.movement.RescueReach;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class GraphRescuePost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESCUE = "rescue:";

    private record HelperChoice(Optional<UUID> id, String why, boolean pending) {}

    private GraphRescuePost() {}

    static boolean rescue(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        BlockCoord home = SettlementHome.of(post.spirits);
        boolean posted = false;
        for (GraphFacts.Spirit fact : post.facts.spirits().values()) {
            if (!fact.stuck()) {
                continue;
            }
            SpiritEntity spirit = entity(post, fact.id());
            if (spirit == null || spirit.isRemoved()) {
                continue;
            }
            UUID id = spirit.getUUID();
            String ticketId = RESCUE + id;
            Optional<StuckSpirits.Stuck> mark = post.service.board().stuck().of(id, post.now);
            if (mark.isEmpty()) {
                posted = true;
                continue;
            }
            long since = mark.orElseThrow().since();
            BlockPos feet = spirit.blockPosition();
            BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
            RescueTicket.Escape escape = RescueTicket.escape(post.level, spirit, home, noPath(mark));
            if (escape.self() && post.service.board().stuck().relapsed(id, at, post.now)) {
                escape = new RescueTicket.Escape(
                        false, null, "повторное падение в ту же ловушку после спасения: самоспасение не перебираем");
            }
            int failures = post.service.board().ticketFailures(ticketId);
            HelperChoice helper = !escape.self() || failures > 0
                    ? helper(post, fact.id(), at)
                    : new HelperChoice(Optional.empty(), "не нужен: выход есть", false);
            if (helper.pending() && !escape.self()) {
                posted = true;
                continue;
            }
            String refusal = refusal(escape, helper, failures);
            RescuePlans.Assignment assignment = RescuePlans.assign(id, since, refusal, helper.id());
            say(post, spirit, since, lifecycle(assignment, at, since, escape, helper));
            if (assignment.mode() == RescuePlans.Mode.IMPOSSIBLE) {
                posted = true;
                continue;
            }
            if (post.open(ticketId)) {
                posted = true;
                continue;
            }
            TaskNode plan = assignment.mode() == RescuePlans.Mode.SELF
                    ? escape.plan()
                    : RescuePlans.helper(at, home, PitRescue.escapeAfterClear(spirit));
            post.submit(
                    post.ticket(
                            ticketId,
                            BoardCase.RESCUE_SPIRIT,
                            assignment.assignee(),
                            task.priority(),
                            assignment.cause(),
                            Map.of()),
                    TicketTarget.at(at).withPlan(plan));
            posted = true;
        }
        return posted;
    }

    private static String refusal(RescueTicket.Escape escape, HelperChoice helper, int failures) {
        if (!escape.self()) {
            return escape.why();
        }
        if (helper.id().isPresent() && failures > 0) {
            return "выход сам не удался (" + escape.why() + ")";
        }
        return null;
    }

    private static String lifecycle(
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

    private static boolean noPath(Optional<StuckSpirits.Stuck> mark) {
        return mark.map(StuckSpirits.Stuck::noPath).orElse(false);
    }

    private static HelperChoice helper(GraphPost post, String rescuee, BlockCoord trapped) {
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

    private static BlockCoord at(SpiritEntity spirit) {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private static void say(GraphPost post, SpiritEntity spirit, long since, String text) {
        String key = "rescue:" + spirit.getUUID() + "@" + since;
        String line = "rescue:" + spirit.getUUID() + " для " + spirit.displayName() + ": " + text;
        if (post.service.sources.due(key, line, post.now)) {
            LOGGER.info("[rescue] {}", line);
        }
    }

    private static @Nullable SpiritEntity entity(GraphPost post, String id) {
        for (SpiritEntity spirit : post.spirits) {
            if (spirit.getUUID().toString().equals(id)) {
                return spirit;
            }
        }
        return null;
    }
}
