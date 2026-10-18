package com.theminespirit.neoforge.repair;

import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.repair.RepairCalculator;
import com.theminespirit.core.repair.RepairCosts;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.repair.RepairMaterials;
import com.theminespirit.core.repair.RepairMethod;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.RepairParams;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.inventory.SpiritInventory;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;

public final class RepairScript implements ScriptInstance {
    private static final int ANVIL_RADIUS = CatalogLimits.STATION_RADIUS_DEFAULT;
    private static final int ANVIL_SLICE_BUDGET = CraftTuning.STATION_SLICES_PER_TICK;

    private record Plan(RepairMethod method, int toolSlot, int secondSlot, String materialItem, int durability,
            int maxDurability) {
    }

    private final SpiritEntity spirit;
    private final RepairParams params;
    private ScriptStatus status = ScriptStatus.RUNNING;
    private String reason;
    private ScriptResult result;
    private Plan plan;
    private BlockLocator.Search anvilSearch;
    private BlockPos anvil;

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
        SpiritInventory inventory = spirit.getInventory();
        if (plan == null) {
            plan = decide(inventory);
            if (plan == null) {
                return status;
            }
        }
        return switch (plan.method()) {
            case ANVIL -> anvil(inventory);
            case GRINDSTONE -> grindstone(inventory);
            case NONE -> fail("способ ремонта не определён: решение обязано назвать наковальню или точило");
        };
    }

    private Plan decide(SpiritInventory inventory) {
        IdFilter filter = IdFilter.of(List.of(params.item()));
        int toolSlot = findMostWorn(inventory, filter);
        if (toolSlot < 0) {
            fail(noDamagedToolReason(inventory, filter));
            return null;
        }
        ItemStack tool = inventory.getItem(toolSlot);
        int maxDurability = tool.getMaxDamage();
        if (maxDurability <= 0) {
            fail("у предмета '" + params.item() + "' нет прочности: чинить нечего");
            return null;
        }
        int durability = maxDurability - tool.getDamageValue();
        String itemId = itemId(tool);
        boolean enchanted = isEnchanted(tool);
        int secondSlot = findSecondDamaged(inventory, filter, tool.getItem(), toolSlot);
        int percent = RepairData.loadDefault().maxDurabilityPercent();
        boolean damaged = RepairCalculator.damagedEnoughToRepair(durability, maxDurability, percent);
        Optional<String> material = RepairMaterials.materialFor(itemId);
        boolean materialAvailable = material.isPresent() && countMaterial(inventory, material.orElseThrow()) > 0;
        RepairMethod method = RepairCalculator.chooseMethod(RepairCosts.defaults(), materialAvailable,
                secondSlot >= 0, enchanted, damaged);
        if (method == RepairMethod.NONE) {
            fail(nothingReason(durability, maxDurability, percent, enchanted, materialAvailable, secondSlot));
            return null;
        }
        return new Plan(method, toolSlot, secondSlot, material.orElse(null), durability, maxDurability);
    }

    private ScriptStatus anvil(SpiritInventory inventory) {
        if (anvil != null) {
            return applyAnvil(inventory);
        }
        if (anvilSearch == null) {
            anvilSearch = BlockLocator.search(spirit.level(), spirit.blockPosition(), ANVIL_RADIUS,
                    Set.of(Blocks.ANVIL));
        }
        List<BlockPos> slices = anvilSearch.step(ANVIL_SLICE_BUDGET);
        if (!slices.isEmpty()) {
            BlockPos center = spirit.blockPosition();
            anvil = slices.stream()
                    .min(Comparator.comparingDouble(position -> position.distSqr(center)))
                    .orElseThrow();
            return applyAnvil(inventory);
        }
        if (!anvilSearch.done()) {
            return ScriptStatus.RUNNING;
        }
        if (plan.secondSlot() >= 0 && !isEnchanted(inventory.getItem(plan.toolSlot()))) {
            return grindstone(inventory);
        }
        return fail("нет наковальни поблизости: чинить материалом негде");
    }

    private ScriptStatus applyAnvil(SpiritInventory inventory) {
        Optional<Item> material = ItemIds.item(plan.materialItem());
        if (material.isEmpty()) {
            return fail("материал ремонта '" + plan.materialItem() + "' отсутствует в реестре");
        }
        if (countMaterial(inventory, plan.materialItem()) < 1) {
            return fail("нет материала '" + plan.materialItem() + "' для ремонта '" + params.item() + "'");
        }
        int repaired = RepairCalculator.anvilResult(plan.durability(), plan.maxDurability(),
                RepairCalculator.ANVIL_REPAIR_PERCENT);
        consumeMaterial(inventory, material.orElseThrow(), 1);
        inventory.getItem(plan.toolSlot()).setDamageValue(plan.maxDurability() - repaired);
        this.result = new ScriptResult.Data(Map.of(
                "item", params.item(),
                "method", "anvil",
                "material", plan.materialItem(),
                "repaired", repaired,
                "max_durability", plan.maxDurability()));
        return succeed();
    }

    private ScriptStatus grindstone(SpiritInventory inventory) {
        ItemStack stackA = inventory.getItem(plan.toolSlot());
        ItemStack stackB = inventory.getItem(plan.secondSlot());
        int maxDurability = stackA.getMaxDamage();
        int repaired = RepairCalculator.grindstoneResult(plan.durability(), maxDurability - stackB.getDamageValue(),
                maxDurability);
        ItemStack combined = stackA.copy();
        combined.setCount(1);
        combined.setDamageValue(maxDurability - repaired);
        inventory.removeItem(plan.toolSlot(), 1);
        inventory.removeItem(plan.secondSlot(), 1);
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

    private int findMostWorn(SpiritInventory inventory, IdFilter filter) {
        int slot = -1;
        int worst = Integer.MAX_VALUE;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty() || stack.getDamageValue() <= 0 || !filter.matches(itemId(stack))) {
                continue;
            }
            if (stack.getDamageValue() < worst) {
                worst = stack.getDamageValue();
                slot = index;
            }
        }
        return slot;
    }

    private int findSecondDamaged(SpiritInventory inventory, IdFilter filter, Item item, int exclude) {
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            if (index == exclude) {
                continue;
            }
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty() || stack.getDamageValue() <= 0 || stack.getItem() != item
                    || !filter.matches(itemId(stack)) || isEnchanted(stack)) {
                continue;
            }
            return index;
        }
        return -1;
    }

    private String noDamagedToolReason(SpiritInventory inventory, IdFilter filter) {
        if (hasEnchantedDamaged(inventory, filter)) {
            return "зачарованный предмет '" + params.item() + "' в точиле потеряет чары: так его не чинить";
        }
        return "в инвентаре нет повреждённого предмета '" + params.item() + "'";
    }

    private String nothingReason(int durability, int maxDurability, int percent, boolean enchanted,
            boolean materialAvailable, int secondSlot) {
        if (!RepairCalculator.damagedEnoughToRepair(durability, maxDurability, percent)) {
            return "предмет '" + params.item() + "' ещё крепок (прочность " + durability + " из " + maxDurability
                    + ", порог " + percent + "%): чинить не нужно";
        }
        if (enchanted && !materialAvailable) {
            return "зачарованный предмет '" + params.item() + "' не чинится точилом, а материала ремонта нет";
        }
        if (secondSlot < 0 && !materialAvailable) {
            return "нет ни материала ремонта, ни второго такого же повреждённого предмета '" + params.item() + "'";
        }
        return "предмет '" + params.item() + "' починить нечем";
    }

    private boolean hasEnchantedDamaged(SpiritInventory inventory, IdFilter filter) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getDamageValue() > 0 && filter.matches(itemId(stack))
                    && isEnchanted(stack)) {
                return true;
            }
        }
        return false;
    }

    private int countMaterial(SpiritInventory inventory, String materialItem) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && materialItem.equals(itemId(stack))) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void consumeMaterial(SpiritInventory inventory, Item item, int count) {
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            int taken = Math.min(left, stack.getCount());
            inventory.removeItem(slot, taken);
            left -= taken;
        }
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
}
