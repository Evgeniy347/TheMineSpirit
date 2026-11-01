package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
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

    RescueWatch() {}

    Set<String> tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        Set<String> live = new HashSet<>();
        BlockCoord settlement = SettlementHome.of(spirits);
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved() || digging(spirit)) {
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
            live.add(id);
            if (active(service, id)) {
                continue;
            }
            submit(level, service, id, spirit, feet, settlement, now);
        }
        return live;
    }

    private static void submit(
            ServerLevel level,
            BoardService service,
            String id,
            SpiritEntity spirit,
            BlockPos feet,
            BlockCoord settlement,
            long now) {
        boolean tool = hasPickaxe(spirit);
        int steps = spirit.getInventory().countItem(Items.DIRT)
                + spirit.getInventory().countItem(Items.COBBLESTONE);
        BlockCoord goal = SurfacePoint.of(level, spirit.home().orElse(settlement));
        Optional<SelfEscape.Plan> self = SelfEscape.plan(coord(feet), goal, new RescueGrid(level, feet), tool, steps);
        if (self.isEmpty()
                && !StuckSituation.detect(coord(feet), new RescueGrid(level, feet))
                        .stuck()) {
            self = Optional.of(new SelfEscape.Plan(SelfEscape.Method.STEP, goal, 1, List.of()));
        }
        Optional<UUID> assignee = self.isPresent() ? Optional.of(spirit.getUUID()) : Optional.empty();
        String what = "застрял в " + feet.toShortString() + " ("
                + self.map(plan -> plan.method().name()).orElse("сам не выйдет") + "), задание спасения " + id
                + " выдано " + assignee.map(UUID::toString).orElse("любому свободному духу");
        LOGGER.warn(
                "[incident] дух {} застрял в {} ({}): задание спасения {} выдано {}; {}",
                spirit.displayName(),
                feet.toShortString(),
                self.map(plan -> plan.method().name()).orElse("сам не выйдет"),
                id,
                assignee.map(UUID::toString).orElse("любому свободному духу"),
                IncidentSnapshots.record(level, spirit, what));
        TicketTarget target = TicketTarget.at(coord(feet)).withRelocate(coord(feet), goal);
        if (self.isPresent()) {
            target = target.withRescue(self.orElseThrow());
        }
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.RESCUE_SPIRIT,
                        assignee,
                        BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT),
                        now,
                        1,
                        id,
                        List.of(),
                        Map.of()),
                target);
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
