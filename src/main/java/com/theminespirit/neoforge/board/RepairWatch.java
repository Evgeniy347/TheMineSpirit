package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.repair.RepairCalculator;
import com.theminespirit.core.repair.RepairCosts;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.repair.RepairMaterials;
import com.theminespirit.core.repair.RepairMethod;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.SpiritInventory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AnvilBlock;

/**
 * Источник доски «починить инструмент»: духу с изношенным инструментом и материалом
 * ремонта (или вторым таким же повреждённым) ставится задание {@code REPAIR_TOOL},
 * адресованное этому духу. Задание держится на доске до ремонта (причина постоянная)
 * и снимается, когда изношенного инструмента больше нет. Ремонт материалом ставится, только если у дома
 * стоит наковальня: иначе задание невыполнимо и крутилось бы FAILED→POSTED (INC-24).
 */
final class RepairWatch {
    private RepairWatch() {}

    static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        Map<BlockCoord, Boolean> anvils = new HashMap<>();
        for (SpiritEntity spirit : spirits) {
            String id = ticketId(spirit);
            Optional<BlockCoord> home = spirit.home();
            Optional<String> item = home.isEmpty()
                    ? Optional.empty()
                    : repairableTool(spirit, () -> anvils.computeIfAbsent(home.get(), at -> anvilNear(level, at)));
            if (item.isPresent()) {
                service.submit(
                        new BoardTicket(
                                id,
                                BoardCase.REPAIR_TOOL,
                                Optional.of(spirit.getUUID()),
                                BoardRules.priorityOf(BoardCase.REPAIR_TOOL),
                                now,
                                1,
                                BoardTicket.ALWAYS_CAUSE,
                                List.of(),
                                Map.of()),
                        TicketTarget.at(home.orElseThrow()).withCraft(item.orElseThrow(), 0));
                continue;
            }
            cancelWaiting(service, id);
        }
    }

    private static Optional<String> repairableTool(SpiritEntity spirit, BooleanSupplier anvilNear) {
        SpiritInventory inventory = spirit.getInventory();
        int percent = RepairData.loadDefault().maxDurabilityPercent();
        RepairCosts costs = RepairCosts.defaults();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getDamageValue() <= 0 || stack.getMaxDamage() <= 0) {
                continue;
            }
            int maxDurability = stack.getMaxDamage();
            int durability = maxDurability - stack.getDamageValue();
            if (!RepairCalculator.damagedEnoughToRepair(durability, maxDurability, percent)) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (ToolSet.replacedNotRepaired(itemId)) {
                continue;
            }
            Optional<String> material = RepairMaterials.materialFor(itemId);
            boolean secondAvailable = hasSecondDamaged(inventory, itemId, slot);
            boolean materialAvailable = material.isPresent()
                    && countMaterial(inventory, material.orElseThrow()) > 0
                    && anvilNear.getAsBoolean();
            RepairMethod method = RepairCalculator.chooseMethod(
                    costs,
                    new RepairCalculator.Availability(materialAvailable, secondAvailable, isEnchanted(stack), true));
            if (method != RepairMethod.NONE) {
                return Optional.of(itemId);
            }
        }
        return Optional.empty();
    }

    static boolean anvilNear(ServerLevel level, BlockCoord home) {
        return HomeBlocks.near(
                level, home, CatalogLimits.STATION_RADIUS_DEFAULT, state -> state.getBlock() instanceof AnvilBlock);
    }

    private static boolean hasSecondDamaged(SpiritInventory inventory, String itemId, int exclude) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == exclude) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getDamageValue() <= 0 || isEnchanted(stack)) {
                continue;
            }
            if (itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                return true;
            }
        }
        return false;
    }

    private static int countMaterial(SpiritInventory inventory, String materialItem) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()
                    && materialItem.equals(
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean isEnchanted(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return !enchantments.isEmpty();
    }

    private static void cancelWaiting(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        if (status == BoardStatus.PENDING || status == BoardStatus.WAITING_RESOURCES) {
            service.board().cancel(id);
        }
    }

    static String ticketId(SpiritEntity spirit) {
        return "repair_tool:" + spirit.getUUID();
    }
}
