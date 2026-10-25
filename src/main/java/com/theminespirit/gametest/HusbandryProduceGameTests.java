package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HusbandryProduceGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int PEN_RADIUS = 4;
    private static final BlockPos START = new BlockPos(1, 1, 1);

    private HusbandryProduceGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void shear_SheepWithShears_WoolCollectedSheepAlive(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.SHEEP, new BlockPos(3, 1, 3));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.SHEARS));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("shear", Map.of("radius", 8, "count", 1)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "овца с шерстью обязана постричься");
            List<Sheep> sheep = HusbandryGameTests.alive(helper, Sheep.class);
            helper.assertTrue(
                    sheep.size() == 1 && sheep.get(0).isAlive(),
                    "овца обязана остаться жива, фактически " + sheep.size());
            helper.assertTrue(
                    sheep.get(0).isSheared(),
                    "овца обязана остаться без шерсти: иначе стрижка не произошла, а шерсть взялась из воздуха");
            int wool = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHITE_WOOL);
            helper.assertTrue(
                    wool >= 1, "в инвентаре духа обязано стать больше шерсти на 1 и выше, фактически " + wool);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void milk_CowWithBucket_MilkInInventory(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.BUCKET));
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("milk", Map.of("radius", 8, "count", 1)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "корова обязана подоиться");
            helper.assertTrue(HusbandryGameTests.alive(helper, Cow.class).size() == 1, "корова обязана остаться жива");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.MILK_BUCKET) == 1,
                    "ведро молока обязано появиться в инвентаре, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.MILK_BUCKET));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.BUCKET) == 0,
                    "пустое ведро обязано уйти на молоко");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectEggs_EggsOnFloor_AllPickedChickensAlive(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.CHICKEN, new BlockPos(2, 1, 4));
        helper.spawn(EntityType.CHICKEN, new BlockPos(4, 1, 2));
        GameTestFixtures.dropAt(helper, Items.EGG, 1, 3.5, 1.0, 3.5);
        GameTestFixtures.dropAt(helper, Items.EGG, 2, 5.5, 1.0, 5.5);
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_eggs", Map.of("radius", 8)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "все яйца загона обязаны подобраться");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.EGG) == 3,
                    "все 3 яйца обязаны оказаться в инвентаре, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.EGG));
            helper.assertTrue(GameTestFixtures.countInWorld(helper, Items.EGG) == 0, "на полу яиц остаться не должно");
            helper.assertTrue(
                    HusbandryGameTests.alive(helper, Chicken.class).size() == 2, "куры обязаны остаться живы");
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] овца может выйти из загона после первого возврата и приманка повторится (каждая укладка
    // ограничена 150 тиками): замер 395 тиков работы духа, потолок 500 с запасом, сам тест 800 ≈ 2×
    @SceneStep(template = "arena", timeoutTicks = 800)
    public static void feed_EscapeeFiveBlocksAway_ReturnedInsideWithoutDamage(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        Sheep escapee = helper.spawn(EntityType.SHEEP, new BlockPos(6, 1, 1));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.WHEAT, 2));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "feed", Map.of("species", "sheep", "radius", PEN_RADIUS, "count", 1, "babiesFirst", true)),
                SETTLE_TICKS);
        BlockPos sheepStart = escapee.blockPosition();
        AtomicInteger workingTicks = new AtomicInteger();
        AtomicInteger pausedTicks = new AtomicInteger();
        helper.onEachTick(() -> {
            if (!run.started()) {
                return;
            }
            boolean suspended =
                    spirit.brainIfPresent().map(brain -> brain.isSuspended()).orElse(false);
            if (suspended) {
                pausedTicks.incrementAndGet();
            } else {
                workingTicks.incrementAndGet();
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    workingTicks.get() <= 500,
                    "возврат приманкой обязан уложиться в 500 тиков работы духа (без пауз политики/тактики; "
                            + "каждая укладка ≤150, овца может выйти повторно), "
                            + "заняло " + workingTicks.get()
                            + "; тиков духа всего: " + run.spiritTicks()
                            + "; пауз: " + pausedTicks.get()
                            + "; статус " + spirit.taskStatus().map(Enum::name).orElse("нет задачи")
                            + "; причина " + spirit.taskFailureReason().orElse("нет")
                            + "; овца " + escapee.blockPosition()
                            + ", дух " + spirit.blockPosition()
                            + ", дух–овца "
                            + String.format(
                                    Locale.ROOT, "%.2f", spirit.position().distanceTo(escapee.position()))
                            + ", овца из " + sheepStart
                            + ", nav овцы done=" + escapee.getNavigation().isDone()
                            + ", path=" + (escapee.getNavigation().getPath() != null));
            HusbandryGameTests.assertSucceeded(helper, spirit, "отбившаяся овца обязана вернуться приманкой");
            helper.assertTrue(escapee.isAlive(), "приманка не бьёт: овца обязана остаться жива");
            helper.assertTrue(
                    escapee.getHealth() >= escapee.getMaxHealth(),
                    "приманка не ранит: здоровье обязано остаться полным, фактически " + escapee.getHealth());
            BlockPos start = helper.absolutePos(START);
            BlockPos sheepCell = escapee.blockPosition();
            int dx = sheepCell.getX() - start.getX();
            int dz = sheepCell.getZ() - start.getZ();
            helper.assertTrue(
                    dx * dx + dz * dz <= PEN_RADIUS * PEN_RADIUS,
                    "овца обязана оказаться внутри загона радиуса " + PEN_RADIUS + " от клетки старта духа, "
                            + "а она на смещении (" + dx + ", " + dz + ")");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void shear_SheepInLava_SkippedAndCounted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        BlockPos cell = new BlockPos(4, 1, 4);
        helper.setBlock(cell.below(), Blocks.LAVA);
        for (BlockPos fence : List.of(cell.north(), cell.south(), cell.east(), cell.west())) {
            helper.setBlock(fence, Blocks.OAK_FENCE);
        }
        Sheep sheep = helper.spawn(EntityType.SHEEP, cell);
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.SHEARS));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("shear", Map.of("radius", 8, "count", 1)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertFailedWith(helper, spirit, "пропущено опасных: 1");
            helper.assertTrue(sheep.isAlive(), "овца в лаве обязана остаться жива: опасную пропускают, а не добивают");
            helper.assertTrue(!sheep.isSheared(), "овца в лаве обязана остаться с шерстью: её пропускают до подхода");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void shear_WithoutShears_FailsBeforeApproach(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.SHEEP, new BlockPos(6, 1, 6));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.WHEAT, 2));
        Block before = helper.getBlockState(new BlockPos(6, 1, 6)).getBlock();
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("shear", Map.of("radius", 8, "count", 1)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertFailedWith(helper, spirit, "нет ножниц");
            List<Sheep> sheep = HusbandryGameTests.alive(helper, Sheep.class);
            helper.assertTrue(
                    sheep.size() == 1 && !sheep.get(0).isSheared(),
                    "без ножниц овца обязана остаться нетронутой и с шерстью");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 1, 6)).is(before), "при отказе мир не меняется");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void shear_CountTwoOneSheep_SecondRunHasNothingToDo(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.SHEEP, new BlockPos(3, 1, 3));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.SHEARS));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("shear", Map.of("radius", 8, "count", 2)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertFailedWith(helper, spirit, "пострижено 1 из 2");
            List<Sheep> sheep = HusbandryGameTests.alive(helper, Sheep.class);
            helper.assertTrue(
                    sheep.size() == 1 && sheep.get(0).isSheared(),
                    "первая овца пострижена и жива, второй раз стричь некого");
            GameTestFixtures.finish(helper);
        });
    }
}
