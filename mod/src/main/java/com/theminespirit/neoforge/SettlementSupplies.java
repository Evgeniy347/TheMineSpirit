package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ChestShare;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.planner.AcquireSupplies;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class SettlementSupplies implements AcquireSupplies {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final SpiritEntity spirit;
    private final AcquireSupplies nearby;
    private final int keep;

    public SettlementSupplies(SpiritEntity spirit, AcquireSupplies nearby, int keep) {
        this.spirit = spirit;
        this.nearby = nearby;
        this.keep = keep;
    }

    @Override
    public int inInventory(String item) {
        return nearby.inInventory(item);
    }

    @Override
    public Map<String, Integer> inventory() {
        return nearby.inventory();
    }

    @Override
    public Set<String> nearbyStations() {
        return nearby.nearbyStations();
    }

    @Override
    public List<ScriptCall> withdraw(String item, int maxCount) {
        IdFilter filter = IdFilter.of(List.of(item));
        List<BlockPos> chests = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : BuildSupplyTrip.containers(spirit)) {
            BlockPos other = BuildSupplyTrip.otherHalf(spirit, pos);
            if (seen.add(pos)
                    && (other.equals(pos) || seen.add(other))
                    && ContainerAccess.open(spirit.level(), pos) instanceof ContainerAccess.Opened opened) {
                chests.add(pos);
                counts.add(count(opened.container(), filter));
            }
        }
        if (chests.isEmpty()) {
            List<ScriptCall> near = nearby.withdraw(item, maxCount);
            LOGGER.info(
                    "[plot] {}: {} не хватает {}, сундуков поселения нет, рядом шагов взять {}",
                    spirit.displayName(),
                    item,
                    maxCount,
                    near.size());
            return near;
        }
        List<Integer> takes = ChestShare.takes(counts, keep, Math.max(0, maxCount));
        int fromChests = takes.stream().mapToInt(Integer::intValue).sum();
        LOGGER.info(
                "[plot] {}: {} не хватает {}, из сундуков {} (минимум {} из {} не трогается), добычей {}",
                spirit.displayName(),
                item,
                maxCount,
                fromChests,
                keep,
                counts.stream().mapToInt(Integer::intValue).sum(),
                Math.max(0, maxCount - fromChests));
        List<ScriptCall> steps = new ArrayList<>();
        for (int index = 0; index < chests.size(); index++) {
            BlockPos pos = chests.get(index);
            for (int left = takes.get(index); left > 0; left -= CatalogLimits.COUNT_MAX) {
                steps.add(new ScriptCall(
                        "withdraw",
                        Map.of(
                                "container",
                                pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                                "item",
                                item,
                                "count",
                                Math.min(left, CatalogLimits.COUNT_MAX),
                                "radius",
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
            }
        }
        return steps;
    }

    private static int count(Container container, IdFilter filter) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && filter.matches(ItemIds.of(stack.getItem()))) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
