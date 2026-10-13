package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.building.BuildSupplies;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class SpiritBuildSupplies implements BuildSupplies {
    private static final int CHEST_RADIUS = 8;

    private final SpiritEntity spirit;

    public SpiritBuildSupplies(SpiritEntity spirit) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, чей инвентарь считать");
    }

    @Override
    public int inInventory(String block) {
        Objects.requireNonNull(block, "Id блока не может быть null: без него неизвестно, что считать");
        return BlockItems.countIn(spirit.getInventory(), block);
    }

    @Override
    public List<ScriptCall> withdraw(String block, int maxCount) {
        Objects.requireNonNull(block, "Id блока не может быть null: без него неизвестно, что забирать");
        if (maxCount < 0) {
            throw new IllegalArgumentException("Число блоков " + maxCount + " отрицательно: забирать нечего");
        }
        if (maxCount == 0) {
            return List.of();
        }
        BlockLocator.Search search = BlockLocator.search(spirit.level(), spirit.blockPosition(), CHEST_RADIUS,
                ContainerAccess.allowedBlocks());
        List<BlockPos> found = new ArrayList<>();
        while (!search.done()) {
            found.addAll(search.step(4));
        }
        found.sort(Comparator.comparingDouble(spirit.blockPosition()::distSqr).thenComparing(Comparator.naturalOrder()));
        List<ScriptCall> steps = new ArrayList<>();
        int rest = maxCount;
        for (BlockPos pos : found) {
            if (rest <= 0) {
                break;
            }
            if (!(ContainerAccess.open(spirit.level(), pos) instanceof ContainerAccess.Opened opened)) {
                continue;
            }
            int have = countIn(opened.container(), block);
            if (have <= 0) {
                continue;
            }
            int take = Math.min(rest, have);
            steps.add(new ScriptCall("withdraw", Map.of("container", pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                    "item", block, "count", take, "radius", CHEST_RADIUS)));
            rest -= take;
        }
        return List.copyOf(steps);
    }

    private int countIn(Container container, String block) {
        int total = 0;
        try {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && stack.is(BlockItems.itemFor(block))) {
                    total += stack.getCount();
                }
            }
        } catch (IllegalArgumentException unknown) {
            return 0;
        }
        return total;
    }
}
