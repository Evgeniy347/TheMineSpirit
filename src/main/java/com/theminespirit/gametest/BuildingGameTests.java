package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.SpiritBuildSupplies;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildingGameTests {
    private BuildingGameTests() {
    }

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, int cobble) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        if (cobble > 0) {
            spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, cobble));
        }
        return spirit;
    }

    @GameTest(template = "arena", batch = "build_supplies", timeoutTicks = 100)
    public static void supplies_TwoChestsWithCobble_NearestFirstSkipsEmpty(GameTestHelper helper) {
        prepare(helper);
        BlockPos near = new BlockPos(2, 1, 2);
        BlockPos far = new BlockPos(6, 1, 6);
        BlockPos empty = new BlockPos(2, 1, 6);
        BlockPos beyond = new BlockPos(8, 1, 8);
        helper.setBlock(near, Blocks.CHEST);
        helper.setBlock(far, Blocks.CHEST);
        helper.setBlock(empty, Blocks.CHEST);
        helper.setBlock(beyond, Blocks.CHEST);
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.FURNACE);
        chest(helper, near).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        chest(helper, far).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        chest(helper, beyond).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        ((Container) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(4, 1, 1))))
                .setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), 2);
        SpiritBuildSupplies supplies = new SpiritBuildSupplies(spirit);

        helper.assertTrue(supplies.inInventory("cobblestone") == 2,
                "счёт обязан идти из инвентаря духа: 2 булыжника, посчитано " + supplies.inInventory("cobblestone"));
        helper.assertTrue(supplies.inInventory("nope_block") == 0,
                "неизвестный блок обязан считаться нулём, а не падать");
        var steps = supplies.withdraw("cobblestone", 8);
        helper.assertTrue(steps.size() == 2,
                "шагов обязано быть 2: ближний сундук (5) и дальний (3), пустой и печь пропускаются, дальний за радиусом исключён, шагов " + steps.size());
        BlockPos nearAbs = helper.absolutePos(near);
        BlockPos farAbs = helper.absolutePos(far);
        helper.assertTrue(steps.get(0).params().get("container").equals(nearAbs.getX() + "," + nearAbs.getY() + "," + nearAbs.getZ())
                && steps.get(0).params().get("count").equals(5),
                "первым обязан идти ближний сундук с пятёркой: ближний первым, фактически " + steps.get(0).params());
        helper.assertTrue(steps.get(1).params().get("container").equals(farAbs.getX() + "," + farAbs.getY() + "," + farAbs.getZ())
                && steps.get(1).params().get("count").equals(3),
                "вторым обязан идти дальний с остатком 3, фактически " + steps.get(1).params());
        helper.assertTrue(supplies.withdraw("cobblestone", 0).isEmpty(),
                "запрос на ноль обязан давать пустые шаги");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos relative) {
        return (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }
}
