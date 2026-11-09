package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.NeedsOfPlan;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.social.TransferSource;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

final class TransferStockDesk {
    private static final int CHEST_RADIUS = 16;
    private static final int CHEST_PROBES = 4;

    private TransferStockDesk() {}

    static List<TransferSource> sources(
            List<SpiritEntity> spirits, StackMapper mapper, RecipeKnowledge recipes, UUID crafter) {
        List<TransferSource> sources = new ArrayList<>();
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved() || spirit.getUUID().equals(crafter)) {
                continue;
            }
            BaseKit kit = KitFor.kitFor(spirit.currentActivity().orElse(null), spirit.isChild());
            List<String> needs = spirit.currentPlan()
                    .map(plan -> NeedsOfPlan.collect(plan, recipes))
                    .orElse(List.of());
            sources.add(new TransferSource(
                    spirit.getUUID(), at(spirit), stacks(spirit.getInventory(), mapper), kit, needs));
        }
        return sources;
    }

    static Map<String, Integer> chestStock(ServerLevel level, BoardService service, BlockCoord home, String craftId) {
        Map<String, Integer> containers = new HashMap<>();
        StackMapper mapper = new StackMapper();
        for (BlockCoord pos : SettlementChestStore.get(level).chests().nearest(home, CHEST_PROBES)) {
            int dx = pos.x() - home.x();
            int dz = pos.z() - home.z();
            if (dx * dx + dz * dz > CatalogLimits.CONTAINER_RADIUS_DEFAULT * CatalogLimits.CONTAINER_RADIUS_DEFAULT) {
                continue;
            }
            if (ContainerAccess.open(level, new BlockPos(pos.x(), pos.y(), pos.z()))
                    instanceof ContainerAccess.Opened opened) {
                for (Stack stack : stacks(opened.container(), mapper)) {
                    if (stack != null) {
                        containers.merge(stack.item(), stack.count(), Integer::sum);
                    }
                }
            }
        }
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() != BoardCase.TRANSFER_ITEM
                    || TransferWatch.cause(craftId).equals(ticket.cause())
                    || !ticket.modifiers().contains("from=warehouse")
                    || !TransferWatch.active(service, ticket.id())) {
                continue;
            }
            String item = modifier(ticket, "item=");
            String count = modifier(ticket, "count=");
            if (item != null && count != null) {
                containers.merge(item, -Integer.parseInt(count), Integer::sum);
            }
        }
        containers.entrySet().removeIf(entry -> entry.getValue() <= 0);
        return Map.copyOf(containers);
    }

    static List<Stack> stacks(Container inventory, StackMapper mapper) {
        List<Stack> raw = mapper.map(inventory);
        List<Stack> normalized = new ArrayList<>(raw.size());
        for (Stack stack : raw) {
            normalized.add(
                    stack == null
                            ? null
                            : new Stack(
                                    ItemIds.normalize(stack.item()), stack.variant(), stack.count(), stack.maxStack()));
        }
        return normalized;
    }

    static boolean hasSettlementChest(ServerLevel level, BlockCoord home) {
        for (BlockCoord pos : SettlementChestStore.get(level).chests().nearest(home, CHEST_PROBES)) {
            int dx = pos.x() - home.x();
            int dz = pos.z() - home.z();
            if (dx * dx + dz * dz > CHEST_RADIUS * CHEST_RADIUS || Math.abs(pos.y() - home.y()) > CHEST_RADIUS) {
                return false;
            }
            if (ContainerAccess.allowed(level.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z())))) {
                return true;
            }
        }
        return false;
    }

    static String modifier(BoardTicket ticket, String prefix) {
        for (String modifier : ticket.modifiers()) {
            if (modifier.startsWith(prefix)) {
                return modifier.substring(prefix.length());
            }
        }
        return null;
    }

    static BlockCoord at(SpiritEntity spirit) {
        return new BlockCoord(
                spirit.blockPosition().getX(),
                spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
    }
}
