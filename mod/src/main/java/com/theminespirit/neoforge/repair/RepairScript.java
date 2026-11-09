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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class RepairScript implements ScriptInstance {
    private static final int ANVIL_RADIUS = CatalogLimits.STATION_RADIUS_DEFAULT;
    private static final int ANVIL_SLICE_BUDGET = CraftTuning.STATION_SLICES_PER_TICK;

    private record Plan(
            RepairMethod method,
            int toolSlot,
            int secondSlot,
            String materialItem,
            int durability,
            int maxDurability) {}

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
        int toolSlot = RepairInventory.mostWorn(inventory, filter);
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
        String itemId = RepairInventory.itemId(tool);
        boolean enchanted = RepairInventory.isEnchanted(tool);
        int secondSlot = RepairInventory.secondDamaged(inventory, filter, tool.getItem(), toolSlot);
        int percent = RepairData.loadDefault().maxDurabilityPercent();
        boolean damaged = RepairCalculator.damagedEnoughToRepair(durability, maxDurability, percent);
        Optional<String> material = RepairMaterials.materialFor(itemId);
        boolean materialAvailable =
                material.isPresent() && RepairInventory.countMaterial(inventory, material.orElseThrow()) > 0;
        RepairMethod method = RepairCalculator.chooseMethod(
                RepairCosts.defaults(),
                new RepairCalculator.Availability(materialAvailable, secondSlot >= 0, enchanted, damaged));
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
            anvilSearch =
                    BlockLocator.search(spirit.level(), spirit.blockPosition(), ANVIL_RADIUS, Set.of(Blocks.ANVIL));
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
        if (plan.secondSlot() >= 0 && !RepairInventory.isEnchanted(inventory.getItem(plan.toolSlot()))) {
            return grindstone(inventory);
        }
        return fail("нет наковальни поблизости: чинить материалом негде");
    }

    private ScriptStatus applyAnvil(SpiritInventory inventory) {
        Optional<Item> material = ItemIds.item(plan.materialItem());
        if (material.isEmpty()) {
            return fail("материал ремонта '" + plan.materialItem() + "' отсутствует в реестре");
        }
        if (RepairInventory.countMaterial(inventory, plan.materialItem()) < 1) {
            return fail("нет материала '" + plan.materialItem() + "' для ремонта '" + params.item() + "'");
        }
        int repaired = RepairCalculator.anvilResult(
                plan.durability(), plan.maxDurability(), RepairCalculator.ANVIL_REPAIR_PERCENT);
        RepairInventory.consumeMaterial(inventory, material.orElseThrow(), 1);
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
        int repaired = RepairCalculator.grindstoneResult(
                plan.durability(), maxDurability - stackB.getDamageValue(), maxDurability);
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
                "item", params.item(), "method", "grindstone", "repaired", repaired, "max_durability", maxDurability));
        return succeed();
    }

    private String noDamagedToolReason(SpiritInventory inventory, IdFilter filter) {
        if (RepairInventory.hasEnchantedDamaged(inventory, filter)) {
            return "зачарованный предмет '" + params.item() + "' в точиле потеряет чары: так его не чинить";
        }
        return "в инвентаре нет повреждённого предмета '" + params.item() + "'";
    }

    private String nothingReason(
            int durability,
            int maxDurability,
            int percent,
            boolean enchanted,
            boolean materialAvailable,
            int secondSlot) {
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

    @Override
    public void cancel() {}

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
