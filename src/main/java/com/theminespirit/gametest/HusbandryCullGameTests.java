package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HusbandryCullGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos START = new BlockPos(1, 1, 1);

    private HusbandryCullGameTests() {}

    @GameTest(template = "arena", batch = "husbandry_cull", timeoutTicks = 400)
    public static void cull_FiveCowsKeepThree_TwoCulledCalfAliveDropsPicked(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(2, 1, 5));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(4, 1, 6));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(6, 1, 4));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(6, 1, 6));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(5, 1, 2));
        HusbandryGameTests.spawnBaby(helper, EntityType.COW, new BlockPos(1, 1, 7), true);
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.IRON_SWORD));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("cull", Map.of("species", "cow", "keep", 3, "radius", 8)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "из пяти коров лишние две обязаны забиться");
            List<Cow> cows = HusbandryGameTests.alive(helper, Cow.class);
            helper.assertTrue(
                    cows.size() == 4, "остаться обязаны 3 взрослые и телёнок (всего 4), фактически " + cows.size());
            helper.assertTrue(
                    cows.stream().filter(Cow::isBaby).count() == 1,
                    "телёнок обязан остаться цел: детёнышей не трогают никогда");
            int beef = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BEEF);
            int leather = GameTestFixtures.countInInventory(spirit.getInventory(), Items.LEATHER);
            helper.assertTrue(
                    beef >= 2 && beef + leather >= 2,
                    "говядина и кожа с двух туш обязаны подобраться (говядины " + beef + ", кожи " + leather + ")");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "husbandry_cull_stone", timeoutTicks = 200)
    public static void cull_StoneSword_RefusedAllAlive(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(2, 1, 2));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(3, 1, 2));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(4, 1, 2));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(2, 1, 3));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.STONE_SWORD));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("cull", Map.of("species", "cow", "keep", 2, "radius", 8)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertFailedWith(helper, spirit, "железным мечом и выше");
            helper.assertTrue(
                    HusbandryGameTests.alive(helper, Cow.class).size() == 4,
                    "каменным мечом стадо не бьют: все 4 обязаны остаться живы");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "husbandry_cull_hands", timeoutTicks = 200)
    public static void cull_BareHands_RefusedAllAlive(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(2, 1, 2));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(3, 1, 2));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(4, 1, 2));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("cull", Map.of("species", "cow", "keep", 2, "radius", 8)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertFailedWith(helper, spirit, "железным мечом и выше");
            helper.assertTrue(
                    HusbandryGameTests.alive(helper, Cow.class).size() == 3,
                    "руками стадо не забивают: все 3 обязаны остаться живы");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "husbandry_hunt", timeoutTicks = 400)
    public static void hunt_WildPigsOutsideHomePen_AllCulledOwnAlive(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Pig own = HusbandryGameTests.spawnStill(helper, EntityType.PIG, new BlockPos(3, 1, 1));
        HusbandryGameTests.spawnStill(helper, EntityType.PIG, new BlockPos(8, 1, 8));
        HusbandryGameTests.spawnStill(helper, EntityType.PIG, new BlockPos(8, 1, 6));
        HusbandryGameTests.spawnStill(helper, EntityType.PIG, new BlockPos(6, 1, 8));
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.IRON_SWORD));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("hunt", Map.of("species", "pig", "radius", 16, "count", 3)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "трое диких обязаны забиться");
            List<Pig> pigs = HusbandryGameTests.alive(helper, Pig.class);
            helper.assertTrue(
                    pigs.size() == 1 && pigs.get(0) == own,
                    "жива обязана остаться ровно своя свинья внутри домашнего круга, а диких — не остаться: "
                            + "фактически живых " + pigs.size() + ", своя жива: " + own.isAlive());
            int pork = GameTestFixtures.countInInventory(spirit.getInventory(), Items.PORKCHOP);
            helper.assertTrue(pork >= 1, "свинина с диких обязана подобраться, фактически " + pork);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "husbandry_cull_calf", timeoutTicks = 400)
    public static void cull_CalfNearVictim_StrikeDeferredToNext(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(4, 1, 2));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(2, 1, 5));
        HusbandryGameTests.spawnStill(helper, EntityType.COW, new BlockPos(6, 1, 6));
        HusbandryGameTests.spawnBaby(helper, EntityType.COW, new BlockPos(6, 1, 5), true);
        SpiritEntity spirit = HusbandryGameTests.spawnWith(helper, START, new ItemStack(Items.IRON_SWORD));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("cull", Map.of("species", "cow", "keep", 2, "radius", 8)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            HusbandryGameTests.assertSucceeded(helper, spirit, "норма держится и без удара на глазах телёнка");
            List<Cow> cows = HusbandryGameTests.alive(helper, Cow.class);
            long grown = cows.stream().filter(cow -> !cow.isBaby()).count();
            long young = cows.stream().filter(Cow::isBaby).count();
            helper.assertTrue(
                    grown == 2, "взрослых обязано остаться 2 (ближняя и дальняя под опекой), фактически " + grown);
            helper.assertTrue(young == 1, "телёнок обязан остаться цел, фактически " + young);
            Cow far = cows.stream()
                    .filter(cow -> !cow.isBaby())
                    .filter(cow -> cow.blockPosition().equals(helper.absolutePos(new BlockPos(6, 1, 6))))
                    .findFirst()
                    .orElse(null);
            helper.assertTrue(
                    far != null, "дальняя корова под опекой телёнка обязана остаться жива: удар откладывается");
            GameTestFixtures.finish(helper);
        });
    }
}
