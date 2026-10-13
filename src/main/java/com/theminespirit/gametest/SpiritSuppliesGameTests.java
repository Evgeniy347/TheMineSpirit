package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.SpiritSupplies;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Снабжение для acquire: счёт инвентаря духа и шаги withdraw по ближним контейнерам —
 * ближний первым, пустые и недоступные пропускаются, радиус соблюдён.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritSuppliesGameTests {
    private static final String FIELD = "arena";
    private static final String BATCH = "acquire_supplies";
    private static final int TIMEOUT = 100;

    private SpiritSuppliesGameTests() {
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void inventory_TwoStacksInInventory_CountsTotal(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        spirit.getInventory().setItem(3, new ItemStack(Items.IRON_INGOT, 3));
        SpiritSupplies supplies = new SpiritSupplies(spirit, 8);

        helper.assertTrue(supplies.inInventory("iron_ingot") == 7,
                "счёт инвентаря обязан сложить обе стопки (4+3), фактически "
                        + supplies.inInventory("iron_ingot"));
        helper.assertTrue(supplies.inInventory("diamond") == 0,
                "отсутствующий предмет обязан дать ноль, а не ошибку");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void inventory_MapOfStacks_ReturnsAllItemsWithTotals(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        spirit.getInventory().setItem(3, new ItemStack(Items.IRON_INGOT, 3));
        spirit.getInventory().setItem(5, new ItemStack(Items.COAL, 2));
        SpiritSupplies supplies = new SpiritSupplies(spirit, 8);

        Map<String, Integer> inventory = supplies.inventory();

        helper.assertTrue(inventory.getOrDefault("iron_ingot", 0) == 7,
                "инвентарь обязан сложить обе стопки железа (4+3), фактически "
                        + inventory.getOrDefault("iron_ingot", 0));
        helper.assertTrue(inventory.getOrDefault("coal", 0) == 2,
                "инвентарь обязан содержать уголь, фактически " + inventory.getOrDefault("coal", 0));
        helper.assertTrue(inventory.size() == 2,
                "инвентарь обязан содержать только занятые предметы, фактически " + inventory);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void withdraw_SingleChest_ReturnsNearestStepWithCappedCount(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        fillChest(helper, new BlockPos(6, 1, 4), Items.IRON_INGOT, 7);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        SpiritSupplies supplies = new SpiritSupplies(spirit, 8);

        List<ScriptCall> steps = supplies.withdraw("iron_ingot", 5);

        String absolute = absolute(helper, new BlockPos(6, 1, 4));
        List<ScriptCall> expected = List.of(new ScriptCall("withdraw",
                Map.of("container", absolute, "item", "iron_ingot", "count", 5)));
        helper.assertTrue(steps.equals(expected),
                "один сундук обязан дать один шаг с его мировыми координатами и числом не больше нужного, "
                        + "шаги: " + steps);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void withdraw_TwoChests_NearerChestFirstThenFarther(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        helper.setBlock(7, 1, 4, Blocks.CHEST);
        fillChest(helper, new BlockPos(5, 1, 4), Items.IRON_INGOT, 2);
        fillChest(helper, new BlockPos(7, 1, 4), Items.IRON_INGOT, 10);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        SpiritSupplies supplies = new SpiritSupplies(spirit, 8);

        List<ScriptCall> steps = supplies.withdraw("iron_ingot", 5);

        List<ScriptCall> expected = List.of(
                new ScriptCall("withdraw",
                        Map.of("container", absolute(helper, new BlockPos(5, 1, 4)),
                                "item", "iron_ingot", "count", 2)),
                new ScriptCall("withdraw",
                        Map.of("container", absolute(helper, new BlockPos(7, 1, 4)),
                                "item", "iron_ingot", "count", 3)));
        helper.assertTrue(steps.equals(expected),
                "ближний сундук обязан идти первым и отдать всё своё, дальний — добирать остаток, шаги: " + steps);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void withdraw_EmptyAndLootChests_SkippedWithoutSteps(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        fillChest(helper, new BlockPos(6, 1, 4), Items.IRON_INGOT, 5);
        BlockPos lootChest = helper.absolutePos(new BlockPos(6, 1, 4));
        RandomizableContainerBlockEntity entity =
                (RandomizableContainerBlockEntity) helper.getLevel().getBlockEntity(lootChest);
        entity.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.parse("minecraft:chests/simple_dungeon")));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        SpiritSupplies supplies = new SpiritSupplies(spirit, 8);

        List<ScriptCall> steps = supplies.withdraw("iron_ingot", 5);

        helper.assertTrue(steps.isEmpty(),
                "пустой сундук и сундук с нераскрытой таблицей добычи обязаны быть пропущены без шагов, "
                        + "шаги: " + steps);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void withdraw_ChestBeyondRadius_SkippedAtRadiusTwo(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(8, 1, 4, Blocks.CHEST);
        fillChest(helper, new BlockPos(8, 1, 4), Items.IRON_INGOT, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));

        List<ScriptCall> near = new SpiritSupplies(spirit, 2).withdraw("iron_ingot", 5);
        List<ScriptCall> far = new SpiritSupplies(spirit, 4).withdraw("iron_ingot", 5);

        helper.assertTrue(near.isEmpty(),
                "сундук на расстоянии 4 не должен попадать в радиус 2, шаги: " + near);
        helper.assertTrue(far.size() == 1,
                "тот же сундук обязан найтись в радиусе 4, шаги: " + far);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    private static void fillChest(GameTestHelper helper, BlockPos pos, Item item, int count) {
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(pos));
        if (!(open instanceof ContainerAccess.Opened opened)) {
            throw new IllegalStateException("Тестовый сундук не открылся: " + open);
        }
        opened.container().setItem(0, new ItemStack(item, count));
    }

    private static String absolute(GameTestHelper helper, BlockPos pos) {
        BlockPos world = helper.absolutePos(pos);
        return world.getX() + "," + world.getY() + "," + world.getZ();
    }
}
