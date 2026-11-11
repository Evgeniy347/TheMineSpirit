package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.RescueGroups;
import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.board.StuckSpirits;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.movement.ToolBreakCost;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.LevelNavWorld;
import com.theminespirit.neoforge.movement.RescueReach;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import org.slf4j.Logger;

final class RescueGroupPost {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RescueGroupPost() {}

    static boolean rescue(GraphPost post, GraphPlan.Task task) {
        Optional<BlockCoord> settlement = HomePoint.of(post.level, post.spirits);
        LevelNavWorld world = new LevelNavWorld(post.level);
        Predicate<BlockCoord> water = cell -> post.level
                .getFluidState(new BlockPos(cell.x(), cell.y(), cell.z()))
                .is(FluidTags.WATER);
        RescueReach.Cache cache = post.service.sources.rescueReach();
        List<RescueGroups.Member> members = new ArrayList<>();
        Map<UUID, SpiritEntity> entities = new LinkedHashMap<>();
        Map<UUID, StuckSpirits.Stuck> marks = new LinkedHashMap<>();
        Map<UUID, Boolean> pathHome = new LinkedHashMap<>();
        Map<UUID, BlockCoord> homes = new LinkedHashMap<>();
        boolean pending = false;
        for (GraphFacts.Spirit fact : post.facts.spirits().values()) {
            if (!fact.stuck()) {
                continue;
            }
            SpiritEntity spirit = GraphRescuePost.entity(post, fact.id());
            if (spirit == null || spirit.isRemoved()) {
                continue;
            }
            Optional<StuckSpirits.Stuck> mark = post.service.board().stuck().of(spirit.getUUID(), post.now);
            if (mark.isEmpty()) {
                pending = true;
                continue;
            }
            BlockCoord at = GraphRescuePost.at(spirit);
            Optional<BlockCoord> target = HomePoint.of(spirit).or(() -> settlement);
            if (target.isEmpty()) {
                pending = true;
                continue;
            }
            BlockCoord home = target.orElseThrow();
            RescueReach.Verdict reach = cache.verdict(spirit.getUUID(), at, home, post.now);
            if (reach == null) {
                cache.request(world, water, spirit.getUUID(), at, home, post.now);
                pending = true;
            } else {
                pathHome.put(spirit.getUUID(), reach.reachable());
            }
            members.add(new RescueGroups.Member(spirit.getUUID(), at, toolTier(fact), blocks(fact)));
            entities.put(spirit.getUUID(), spirit);
            homes.put(spirit.getUUID(), home);
            marks.put(spirit.getUUID(), mark.orElseThrow());
        }
        if (pending) {
            return true;
        }
        if (members.isEmpty()) {
            return false;
        }
        boolean posted = false;
        RescueGroups.Passage passage =
                cell -> world.available(cell.x(), cell.z()) && world.passable(cell.x(), cell.y(), cell.z());
        for (RescueGroups.Group group : RescueGroups.form(members, passage)) {
            if (follow(post, group, homes, pathHome, marks, task, entities)) {
                posted = true;
                continue;
            }
            posted |= rescueLeader(post, group, homes, marks, entities, task);
        }
        return posted;
    }

    private static boolean follow(
            GraphPost post,
            RescueGroups.Group group,
            Map<UUID, BlockCoord> homes,
            Map<UUID, Boolean> pathHome,
            Map<UUID, StuckSpirits.Stuck> marks,
            GraphPlan.Task task,
            Map<UUID, SpiritEntity> entities) {
        List<RescueGroups.Member> walkers = RescueGroups.walkers(
                group,
                member -> pathHome.getOrDefault(member.id(), false),
                member -> onSurface(post, entities.get(member.id())));
        if (walkers.isEmpty()) {
            return false;
        }
        for (RescueGroups.Member walker : walkers) {
            String ticketId = GraphRescuePost.RESCUE + walker.id();
            if (post.open(ticketId)) {
                continue;
            }
            StuckSpirits.Stuck mark = marks.get(walker.id());
            BlockCoord home = homes.get(walker.id());
            if (mark == null || home == null) {
                continue;
            }
            post.submit(
                    post.ticket(
                            ticketId,
                            BoardCase.RESCUE_SPIRIT,
                            Optional.of(walker.id()),
                            task.priority(),
                            RescuePlans.cause(mark.since()),
                            Map.of()),
                    TicketTarget.at(walker.at()).withPlan(RescuePlans.walk(home)));
        }
        sayGroup(post, group, "путь открыт: идут следом по готовому пути, мир не трогают");
        return true;
    }

    private static boolean rescueLeader(
            GraphPost post,
            RescueGroups.Group group,
            Map<UUID, BlockCoord> homes,
            Map<UUID, StuckSpirits.Stuck> marks,
            Map<UUID, SpiritEntity> entities,
            GraphPlan.Task task) {
        for (RescueGroups.Member member : group.members()) {
            if (post.open(GraphRescuePost.RESCUE + member.id())) {
                return true;
            }
        }
        RescueTicket.Escape chosen = null;
        RescueGroups.Member leader = group.members().get(0);
        for (RescueGroups.Member member : group.members()) {
            SpiritEntity spirit = entities.get(member.id());
            StuckSpirits.Stuck mark = marks.get(member.id());
            BlockCoord home = homes.get(member.id());
            if (spirit == null || mark == null || home == null) {
                continue;
            }
            RescueTicket.Escape escape = RescueTicket.escape(post.level, spirit, home, mark.noPath(), false);
            if (escape.self() && post.service.board().stuck().relapsed(member.id(), member.at(), post.now)) {
                escape = new RescueTicket.Escape(
                        false, null, "повторное падение в ту же ловушку после спасения: самоспасение не перебираем");
            }
            if (escape.self()) {
                leader = member;
                chosen = escape;
                break;
            }
            if (chosen == null) {
                chosen = escape;
            }
        }
        SpiritEntity spirit = entities.get(leader.id());
        StuckSpirits.Stuck mark = marks.get(leader.id());
        BlockCoord home = homes.get(leader.id());
        if (spirit == null || mark == null || home == null || chosen == null) {
            return false;
        }
        String ticketId = GraphRescuePost.RESCUE + leader.id();
        int failures = post.service.board().ticketFailures(ticketId);
        GraphRescuePost.HelperChoice helper = !chosen.self() || failures > 0
                ? GraphRescuePost.helper(post, leader.id().toString(), leader.at())
                : new GraphRescuePost.HelperChoice(Optional.empty(), "не нужен: выход есть", false);
        if (helper.pending() && !chosen.self()) {
            return true;
        }
        String refusal = GraphRescuePost.refusal(chosen, helper, failures);
        RescuePlans.Assignment assignment = RescuePlans.assign(leader.id(), mark.since(), refusal, helper.id());
        GraphRescuePost.say(
                post, spirit, mark.since(), lifecycle(assignment, leader.at(), mark.since(), chosen, helper, group));
        if (assignment.mode() == RescuePlans.Mode.IMPOSSIBLE || post.open(ticketId)) {
            return true;
        }
        TaskNode plan = assignment.mode() == RescuePlans.Mode.SELF
                ? chosen.plan()
                : RescuePlans.helper(leader.at(), home, PitRescue.escapeAfterClear(spirit));
        post.submit(
                post.ticket(
                        ticketId,
                        BoardCase.RESCUE_SPIRIT,
                        assignment.assignee(),
                        task.priority(),
                        assignment.cause(),
                        Map.of()),
                TicketTarget.at(leader.at()).withPlan(plan));
        return true;
    }

    private static boolean onSurface(GraphPost post, SpiritEntity spirit) {
        return spirit != null && !spirit.isRemoved() && DigOutScript.open(post.level, spirit.blockPosition());
    }

    private static int toolTier(GraphFacts.Spirit fact) {
        int best = 0;
        for (GraphFacts.Tool tool : fact.tools()) {
            boolean pickaxe = tool.item().endsWith("_pickaxe")
                    || (tool.kind() != null && tool.kind().contains("pickaxe"));
            if (pickaxe) {
                best = Math.max(best, tool.tier());
            }
        }
        return best;
    }

    private static int blocks(GraphFacts.Spirit fact) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : fact.inventory().entrySet()) {
            String id = entry.getKey().startsWith("minecraft:")
                    ? entry.getKey().substring("minecraft:".length())
                    : entry.getKey();
            if (ToolBreakCost.CHEAP_ORDER.contains(id)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private static void sayGroup(GraphPost post, RescueGroups.Group group, String text) {
        String key = "rescue-group:" + group.leader() + "@" + post.now / 1200;
        String line = "rescue: группа " + group.members().size() + " во главе с " + group.leader() + ": " + text;
        if (post.service.sources.due(key, line, post.now)) {
            LOGGER.info("[rescue] {}", line);
        }
    }

    private static String lifecycle(
            RescuePlans.Assignment assignment,
            BlockCoord at,
            long since,
            RescueTicket.Escape escape,
            GraphRescuePost.HelperChoice helper,
            RescueGroups.Group group) {
        String base = GraphRescuePost.lifecycle(assignment, at, since, escape, helper);
        if (group.followers().isEmpty()) {
            return base;
        }
        return base + "; группа " + group.members().size() + ": ведущий " + group.leader() + ", ведомые ждут";
    }
}
