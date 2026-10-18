package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HiveGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos START = new BlockPos(1, 1, 1);
    private static final BlockPos HIVE = new BlockPos(4, 2, 4);
    private static final BlockPos CAMPFIRE = new BlockPos(4, 0, 4);

    private HiveGameTests() {
    }

    @GameTest(template = "arena", batch = "hive_campfire", timeoutTicks = 400)
    public static void harvest_FullHiveWithCampfire_HoneycombCollectedBeesCalm(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        campfireUnderHive(helper);
        Bee first = helper.spawn(EntityType.BEE, new BlockPos(2, 1, 2));
        Bee second = helper.spawn(EntityType.BEE, new BlockPos(6, 1, 6));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.SHEARS));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("harvest_hive", Map.of("radius", 8, "count", 1)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "полный улей с костром обязан собраться");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.HONEYCOMB) == 3,
                    "ножницы дают 3 соты, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.HONEYCOMB));
            helper.assertTrue(honeyLevel(helper) == 0,
                    "улей обязан опустеть после сбора, фактически уровень " + honeyLevel(helper));
            helper.assertTrue(helper.getBlockState(HIVE).is(Blocks.BEEHIVE),
                    "улей обязан остаться на месте: сбор не ломает дом пчёл");
            helper.assertTrue(calm(first) && calm(second),
                    "при дыме костра пчёлы не гибнут, не жалят и не выбирают цель: " + describe(first)
                            + "; " + describe(second));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "hive_place_campfire", timeoutTicks = 400)
    public static void harvest_NoCampfireWithMaterials_CampfirePlacedThenHarvest(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(HIVE, fullHive());
        Bee bee = helper.spawn(EntityType.BEE, new BlockPos(2, 1, 2));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START,
                new ItemStack(Items.SHEARS),
                new ItemStack(Items.OAK_LOG, 3),
                new ItemStack(Items.STICK, 3),
                new ItemStack(Items.COAL, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("harvest_hive", Map.of("radius", 8, "count", 1)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "без костра дух обязан поставить его и собрать");
            BlockState fire = helper.getBlockState(new BlockPos(4, 1, 4));
            helper.assertTrue(fire.is(Blocks.CAMPFIRE) && fire.getValue(CampfireBlock.LIT),
                    "под ульем обязан стоять горящий костёр, фактически " + fire);
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG) == 0
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.STICK) == 0
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL) == 0,
                    "материалы ушли на костёр: брёвен "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG)
                            + ", палок " + GameTestFixtures.countInInventory(spirit.getInventory(), Items.STICK)
                            + ", угля " + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.HONEYCOMB) == 3,
                    "после костра соты обязаны собраться, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.HONEYCOMB));
            helper.assertTrue(honeyLevel(helper) == 0,
                    "улей обязан опустеть, фактически уровень " + honeyLevel(helper));
            helper.assertTrue(calm(bee),
                    "пчела обязана остаться невредимой и спокойной: " + describe(bee));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "hive_bottle", timeoutTicks = 400)
    public static void harvest_GlassBottle_MilkHoneyInInventory(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        campfireUnderHive(helper);
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START,
                new ItemStack(Items.GLASS_BOTTLE, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("harvest_hive", Map.of("radius", 8, "count", 1)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "бутылка обязана дать мёд");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.HONEY_BOTTLE) == 1,
                    "бутылка мёда обязана появиться в инвентаре, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.HONEY_BOTTLE));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.GLASS_BOTTLE) == 0,
                    "пустая бутылка обязана уйти на мёд");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.HONEYCOMB) == 0,
                    "без ножниц соты не берут: бутылка даёт только мёд");
            helper.assertTrue(honeyLevel(helper) == 0,
                    "улей обязан опустеть после бутылки, фактически уровень " + honeyLevel(helper));
            GameTestFixtures.finish(helper);
        });
    }

    private static void campfireUnderHive(GameTestHelper helper) {
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(CAMPFIRE, Blocks.CAMPFIRE);
        helper.setBlock(HIVE, fullHive());
    }

    private static BlockState fullHive() {
        return Blocks.BEEHIVE.defaultBlockState()
                .setValue(BeehiveBlock.FACING, Direction.NORTH)
                .setValue(BeehiveBlock.HONEY_LEVEL, 5);
    }

    /**
     * Пчела спокойна: не убита, не жалила и не выбрала цель. Улетевшая в улей пчела по ванили уходит из мира
     * (DISCARDED) — это не гибель, а обычная жизнь улья, поэтому «жива» проверяем по причине удаления.
     */
    private static boolean calm(Bee bee) {
        Entity.RemovalReason removal = bee.getRemovalReason();
        boolean unharmed = removal == null || removal == Entity.RemovalReason.DISCARDED;
        return unharmed && !bee.hasStung() && (bee.isRemoved() || bee.getTarget() == null);
    }

    private static String describe(Bee bee) {
        return "удалена " + bee.getRemovalReason() + ", жалила " + bee.hasStung() + ", цель " + bee.getTarget();
    }

    private static int honeyLevel(GameTestHelper helper) {
        BlockState state = helper.getBlockState(HIVE);
        return state.hasProperty(BeehiveBlock.HONEY_LEVEL) ? state.getValue(BeehiveBlock.HONEY_LEVEL) : -1;
    }
}
