package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.mining.LeftOreRules;
import com.theminespirit.core.mining.MineOres;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.MineOreLedger;
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
import net.minecraft.world.item.PickaxeItem;
import org.slf4j.Logger;

public final class LeftOreWatch {
    static final String PREFIX = "left_ore:";
    static final int MAX_TICKETS = 16;
    private static final Logger LOGGER = LogUtils.getLogger();

    private LeftOreWatch() {}

    public static Set<String> tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        MineOreLedger ledger = MineOreLedger.get(level);
        Set<String> live = new HashSet<>();
        for (Map.Entry<BlockCoord, MineOreLedger.Left> ore : ledger.ores().entrySet()) {
            BlockCoord pos = ore.getKey();
            BlockPos block = new BlockPos(pos.x(), pos.y(), pos.z());
            if (!level.isLoaded(block)) {
                live.add(id(pos));
                continue;
            }
            if (MineOres.kindOf(BlockStates.idOf(level.getBlockState(block))) == MineOres.Kind.NONE) {
                ledger.forget(pos);
                continue;
            }
            if (live.size() >= MAX_TICKETS) {
                continue;
            }
            String tool = LeftOreRules.requiredTool(ore.getValue().block());
            Optional<UUID> holder = holder(spirits, tool);
            String id = holder.map(uuid -> id(pos) + "@" + uuid).orElse(id(pos));
            live.add(id);
            if (!active(service, id)) {
                submit(service, id, ore.getValue(), tool, holder, now);
            }
        }
        return live;
    }

    public static String id(BlockCoord pos) {
        return PREFIX + pos.x() + "," + pos.y() + "," + pos.z();
    }

    private static Optional<UUID> holder(List<SpiritEntity> spirits, String tool) {
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
                ItemStack stack = spirit.getInventory().getItem(slot);
                if (stack.getItem() instanceof PickaxeItem
                        && StockReader.pickaxeKeys(stack).contains(tool)) {
                    return Optional.of(spirit.getUUID());
                }
            }
        }
        return Optional.empty();
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED
                || status == BoardStatus.BLOCKED;
    }

    private static void submit(
            BoardService service, String id, MineOreLedger.Left left, String tool, Optional<UUID> holder, long now) {
        LOGGER.info("Поставлено: добыть оставленную руду {} ({}) — ждёт {}", left.block(), id, tool);
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.MINE_LEFT_ORE,
                        holder,
                        BoardRules.priorityOf(BoardCase.MINE_LEFT_ORE),
                        now,
                        1,
                        id,
                        List.of(),
                        Map.of(tool, 1)),
                TicketTarget.at(left.stand()).withSupply(left.block(), 1));
    }
}
