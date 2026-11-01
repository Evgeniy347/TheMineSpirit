package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.ChangeSummary;
import com.theminespirit.core.board.ScriptProgressWatch;
import com.theminespirit.core.board.StillWatch;
import com.theminespirit.core.board.TaskStall;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.movement.SelfEscape;
import com.theminespirit.core.movement.StuckSituation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.IncidentSnapshots;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import org.slf4j.Logger;

final class RescueWatch {
    static final String PREFIX = "rescue:";
    private static final String DIG_OUT = "dig_out";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ScriptProgressWatch progress = new ScriptProgressWatch();
    private final StillWatch still = new StillWatch();
    private final Map<UUID, String> causes = new HashMap<>();
    private final ChangeSummary log = new ChangeSummary(BoardTuning.logSummaryTicks());

    private record Sighting(String id, String cause, SpiritEntity spirit, BlockPos feet, BlockCoord settlement) {}

    private record Rescue(
            SpiritEntity spirit,
            BlockPos feet,
            String id,
            String method,
            Optional<UUID> assignee,
            boolean tool,
            int steps) {}

    RescueWatch() {}

    static void cancelRestored(Board board) {
        board.all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.RESCUE_SPIRIT)
                .forEach(ticket -> board.cancel(ticket.id()));
    }

    Set<String> tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        Set<String> live = new HashSet<>();
        Set<String> presentIds = new HashSet<>();
        BlockCoord settlement = SettlementHome.of(spirits);
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            presentIds.add(PREFIX + spirit.getUUID());
            if (digging(spirit)) {
                continue;
            }
            if (progress.advancing(spirit.getUUID(), spirit.taskProgress().orElse(null), now)) {
                continue;
            }
            BlockPos feet = spirit.blockPosition();
            StuckSituation.Grid grid = new RescueGrid(level, feet);
            StuckSituation.Verdict verdict = StuckSituation.detect(coord(feet), grid);
            String id = PREFIX + spirit.getUUID();
            boolean marked = service.board().stuck().of(spirit.getUUID(), now).isPresent();
            boolean standing = still.still(spirit.getUUID(), coord(feet), now, TaskStall.STALL_TICKS);
            if (!verdict.stuck() && !(marked && standing)) {
                continue;
            }
            String cause = causes.getOrDefault(spirit.getUUID(), id);
            live.add(cause);
            if (active(service, id)) {
                continue;
            }
            cause = id + ":" + now;
            causes.put(spirit.getUUID(), cause);
            live.remove(id);
            live.add(cause);
            submit(level, service, new Sighting(id, cause, spirit, feet, settlement), now);
        }
        log.retain(presentIds);
        return live;
    }

    private void submit(ServerLevel level, BoardService service, Sighting sighting, long now) {
        SpiritEntity spirit = sighting.spirit();
        BlockPos feet = sighting.feet();
        boolean tool = hasPickaxe(spirit);
        int steps = spirit.getInventory().countItem(Items.DIRT)
                + spirit.getInventory().countItem(Items.COBBLESTONE);
        BlockCoord goal = spirit.home().orElse(sighting.settlement());
        Optional<SelfEscape.Plan> self = SelfEscape.plan(coord(feet), goal, new RescueGrid(level, feet), tool, steps);
        boolean relocate = true;
        if (self.isEmpty()) {
            Optional<SelfEscape.Plan> near = SelfEscape.nearby(coord(feet), new RescueGrid(level, feet));
            if (near.isPresent()) {
                goal = near.orElseThrow().target();
                relocate = false;
                self = near;
            }
        }
        Optional<UUID> assignee = self.isPresent() ? Optional.of(spirit.getUUID()) : Optional.empty();
        String method = self.map(plan -> plan.method().name()).orElse("сам не выйдет");
        note(level, new Rescue(spirit, feet, sighting.id(), method, assignee, tool, steps), now);
        TicketTarget target = TicketTarget.at(coord(feet));
        if (relocate) {
            target = target.withRelocate(coord(feet), goal);
        }
        if (self.isPresent()) {
            target = target.withRescue(self.orElseThrow());
        }
        service.submit(
                new BoardTicket(
                        sighting.id(),
                        BoardCase.RESCUE_SPIRIT,
                        assignee,
                        BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT),
                        now,
                        1,
                        sighting.cause(),
                        List.of(),
                        Map.of()),
                target);
    }

    private void note(ServerLevel level, Rescue rescue, long now) {
        String where = rescue.feet().toShortString();
        String state = rescue.method() + "|" + where + "|" + rescue.tool() + "|" + rescue.steps();
        String tool = rescue.tool() ? "есть" : "нет";
        log.note(rescue.id(), state, 1, now).ifPresent(summary -> {
            if (summary.changed()) {
                String what = "застрял в " + where + " (" + rescue.method() + "), задание спасения " + rescue.id();
                LOGGER.warn(
                        "[rescue] дух {} застрял в {} ({}): задание спасения {} выдано {}; материал столба: "
                                + "земля+булыжник {}, кирка {}; {}",
                        rescue.spirit().displayName(),
                        where,
                        rescue.method(),
                        rescue.id(),
                        rescue.assignee().map(UUID::toString).orElse("любому свободному духу"),
                        rescue.steps(),
                        tool,
                        IncidentSnapshots.record(level, rescue.spirit(), what));
                return;
            }
            LOGGER.warn(
                    "[rescue] сводка: дух {} стоит в {} ({}), задание {} выдавалось {} раз за окно; "
                            + "материал столба {}, кирка {}",
                    rescue.spirit().displayName(),
                    where,
                    rescue.method(),
                    rescue.id(),
                    summary.count(),
                    rescue.steps(),
                    tool);
        });
    }

    private static boolean digging(SpiritEntity spirit) {
        return DIG_OUT.equals(spirit.currentTaskScript().orElse(null))
                && spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED
                || status == BoardStatus.BLOCKED;
    }

    private static boolean hasPickaxe(SpiritEntity spirit) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.getItem() instanceof PickaxeItem) {
                return true;
            }
        }
        return false;
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
