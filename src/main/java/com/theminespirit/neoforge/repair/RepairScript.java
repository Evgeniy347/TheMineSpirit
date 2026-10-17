package com.theminespirit.neoforge.repair;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.repair.RepairCalculator;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.repair.RepairMethod;
import com.theminespirit.core.script.RepairParams;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.SpiritInventory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class RepairScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final RepairParams params;
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;
    private ScriptResult result;

    public RepairScript(SpiritEntity spirit, RepairParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (status != ScriptStatus.RUNNING) {
            return status;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время ремонта");
        }
        return repair(spirit.getInventory());
    }

    private ScriptStatus repair(SpiritInventory inventory) {
        IdFilter filter = IdFilter.of(List.of(params.item()));
        Pair pair = findPair(inventory, filter);
        if (pair == null) {
            return fail(noPairReason(inventory, filter));
        }
        ItemStack stackA = inventory.getItem(pair.first());
        ItemStack stackB = inventory.getItem(pair.second());
        int maxDurability = stackA.getMaxDamage();
        if (maxDurability <= 0) {
            return fail("у предмета '" + params.item() + "' нет прочности: чинить нечего");
        }
        int durabilityA = maxDurability - stackA.getDamageValue();
        int durabilityB = maxDurability - stackB.getDamageValue();
        int percent = RepairData.loadDefault().maxDurabilityPercent();
        int best = Math.max(durabilityA, durabilityB);
        if (RepairCalculator.chooseMethod(2, false, best, maxDurability, percent) != RepairMethod.GRINDSTONE) {
            return fail("предмет '" + params.item() + "' ещё крепок (лучшая прочность " + best + " из "
                    + maxDurability + ", порог " + percent + "%): чинить не нужно");
        }
        int repaired = RepairCalculator.grindstoneResult(durabilityA, durabilityB, maxDurability);
        ItemStack combined = stackA.copy();
        combined.setCount(1);
        combined.setDamageValue(maxDurability - repaired);
        inventory.removeItem(pair.first(), 1);
        inventory.removeItem(pair.second(), 1);
        ItemStack remainder = inventory.addItem(combined);
        if (!remainder.isEmpty()) {
            return fail("нет места в инвентаре для результата ремонта");
        }
        this.result = new ScriptResult.Data(Map.of(
                "item", params.item(),
                "method", "grindstone",
                "repaired", repaired,
                "max_durability", maxDurability));
        return succeed();
    }

    private Pair findPair(SpiritInventory inventory, IdFilter filter) {
        for (int first = 0; first < inventory.getContainerSize(); first++) {
            ItemStack candidate = inventory.getItem(first);
            if (!isRepairableCandidate(candidate, filter)) {
                continue;
            }
            for (int second = first + 1; second < inventory.getContainerSize(); second++) {
                ItemStack other = inventory.getItem(second);
                if (!isRepairableCandidate(other, filter)) {
                    continue;
                }
                if (candidate.getItem() == other.getItem()) {
                    return new Pair(first, second);
                }
            }
        }
        return null;
    }

    private boolean isRepairableCandidate(ItemStack stack, IdFilter filter) {
        return !stack.isEmpty() && stack.getDamageValue() > 0 && filter.matches(itemId(stack)) && !isEnchanted(stack);
    }

    private String noPairReason(SpiritInventory inventory, IdFilter filter) {
        if (hasEnchantedDamaged(inventory, filter)) {
            return "зачарованный предмет '" + params.item() + "' в точиле потеряет чары: так его не чинить";
        }
        return "в инвентаре нет двух одинаковых повреждённых предметов '" + params.item() + "'";
    }

    private boolean hasEnchantedDamaged(SpiritInventory inventory, IdFilter filter) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getDamageValue() > 0 && filter.matches(itemId(stack)) && isEnchanted(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEnchanted(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return !enchantments.isEmpty();
    }

    private String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Override
    public void cancel() {
    }

    @Override
    public String failureReason() {
        return reason;
    }

    @Override
    public Optional<ScriptResult> result() {
        return Optional.ofNullable(result);
    }

    private ScriptStatus succeed() {
        status = ScriptStatus.SUCCEEDED;
        return status;
    }

    private ScriptStatus fail(String message) {
        status = ScriptStatus.FAILED;
        reason = message;
        return status;
    }

    private record Pair(int first, int second) {
    }
}
