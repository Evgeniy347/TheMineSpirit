package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.husbandry.HerdSession;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HusbandryGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int BABY_AGE = -24000;
    private static final int FEED_GROWTH_FLOOR = 1000;
    private static final BlockPos START = new BlockPos(1, 1, 1);

    private HusbandryGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void breed_TwoCowsWithWheat_GivesCalfWithin200Ticks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        helper.spawn(EntityType.COW, new BlockPos(4, 1, 3));
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT, 4));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("breed", Map.of("species", "cow", "radius", 8, "pairs", 1)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    run.spiritTicks() <= 200, "телёнок обязан родиться за 200 тиков духа, заняло " + run.spiritTicks());
            assertSucceeded(helper, spirit, "пара коров с пшеницей обязана свестись");
            List<Cow> cows = alive(helper, Cow.class);
            helper.assertTrue(
                    cows.size() == 3,
                    "после сведения пары коров обязано стать 3 (две матери и телёнок), фактически " + cows.size());
            long calves = cows.stream().filter(Cow::isBaby).count();
            helper.assertTrue(calves == 1, "телёнок обязан быть ровно 1 (сведено пар: 1), фактически " + calves);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT) == 2,
                    "на пару уходит ровно 2 пшеницы из 4, фактически осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void feed_ChickensSeedsBabiesFirst_FeedsDistantChicksBeforeNearHens(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.CHICKEN, new BlockPos(2, 1, 2));
        helper.spawn(EntityType.CHICKEN, new BlockPos(2, 1, 3));
        spawnBaby(helper, EntityType.CHICKEN, new BlockPos(6, 1, 6), true);
        spawnBaby(helper, EntityType.CHICKEN, new BlockPos(7, 1, 6), true);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 4));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("feed", Map.of("species", "chicken", "radius", 8, "count", 2, "babiesFirst", true)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "два цыплёнка обязаны покормиться первыми");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS) == 2,
                    "на двоих уходит ровно 2 семени из 4, фактически осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS));
            List<Chicken> grown = alive(helper, Chicken.class).stream()
                    .filter(Chicken::isBaby)
                    .toList();
            helper.assertTrue(
                    grown.size() == 2,
                    "цыплят обязано остаться 2 (кормление растит, а не убивает), фактически " + grown.size());
            for (Chicken chick : grown) {
                helper.assertTrue(
                        chick.getAge() > BABY_AGE + FEED_GROWTH_FLOOR,
                        "цыплёнок обязан подрасти после корма больше чем на " + FEED_GROWTH_FLOOR
                                + " тиков (естественный рост за тест меньше): иначе кормили ближних кур, "
                                + "а не дальних цыплят, возраст " + chick.getAge());
            }
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void feed_TwoLiveCowsInSmallPen_BothFedWithin300SpiritTicks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int i = 1; i <= 5; i++) {
            helper.setBlock(new BlockPos(i, 1, 1), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(i, 1, 5), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(1, 1, i), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(5, 1, i), Blocks.OAK_FENCE);
        }
        helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        helper.spawn(EntityType.COW, new BlockPos(3, 1, 2));
        SpiritEntity spirit = spawnWith(helper, new BlockPos(2, 1, 4), new ItemStack(Items.WHEAT, 2));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("feed", Map.of("species", "cow", "radius", 8, "count", 2, "babiesFirst", false)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    run.spiritTicks() <= 300,
                    "две живые коровы обязаны покормиться за 300 тиков духа, заняло " + run.spiritTicks());
            assertSucceeded(helper, spirit, "две живые коровы в загоне обязаны покормиться");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT) == 0,
                    "на двух коров уходят обе пшеницы, фактически осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT));
            helper.assertTrue(
                    alive(helper, Cow.class).size() == 2,
                    "кормление не убивает: живых коров обязано остаться 2, фактически "
                            + alive(helper, Cow.class).size());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void breed_WithoutFeed_FailsAndLeavesHerdUntouched(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        helper.spawn(EntityType.COW, new BlockPos(4, 1, 3));
        SpiritEntity spirit = spawnWith(helper, START);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("breed", Map.of("species", "cow", "radius", 8, "pairs", 1)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertFailedWith(helper, spirit, "нет корма: нужен minecraft:wheat");
            List<Cow> cows = alive(helper, Cow.class);
            helper.assertTrue(
                    cows.size() == 2 && cows.stream().noneMatch(Cow::isBaby),
                    "без корма стадо обязано остаться нетронутым (2 взрослые, без телят), фактически " + cows.size());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void breed_AdultAndCalf_CalfIsNotPaired(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        spawnBaby(helper, EntityType.COW, new BlockPos(4, 1, 3));
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT, 4));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("breed", Map.of("species", "cow", "radius", 8, "pairs", 1)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertFailedWith(helper, spirit, "готово к разведению 1 из 2");
            List<Cow> cows = alive(helper, Cow.class);
            helper.assertTrue(
                    cows.size() == 2,
                    "телёнок в пару не сводится: новых телят нет, коров обязано остаться 2, фактически " + cows.size());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT) == 4,
                    "несостоявшаяся пара корм не тратит: пшеницы обязано остаться 4");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void breed_PigsWithWheat_AsksForCarrots(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.PIG, new BlockPos(3, 1, 3));
        helper.spawn(EntityType.PIG, new BlockPos(4, 1, 3));
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT, 4));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("breed", Map.of("species", "pig", "radius", 8, "pairs", 1)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertFailedWith(helper, spirit, "нужен minecraft:carrot");
            helper.assertTrue(
                    alive(helper, Pig.class).size() == 2, "чужим кормом свиней не кормят: обе обязаны остаться целы");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT) == 4,
                    "пшеница свиньям не подходит: все 4 обязаны остаться");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void breed_SessionRestoredMidWork_KeepsDoneAndFinishes(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        helper.spawn(EntityType.COW, new BlockPos(3, 1, 2));
        helper.spawn(EntityType.COW, new BlockPos(2, 1, 3));
        helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT, 8));
        AtomicReference<HerdSession> session = new AtomicReference<>();
        AtomicReference<Map<String, Object>> saved = new AtomicReference<>();
        AtomicReference<HerdSession> restored = new AtomicReference<>();

        helper.onEachTick(() -> {
            HerdSession current = session.get();
            if (current == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(HerdSession.breed(spirit, Species.COW, 8, 2));
                }
                return;
            }
            if (saved.get() == null) {
                if (current.outcome() == HerdSession.Outcome.RUNNING) {
                    current.tick();
                }
                if (current.done() >= 1) {
                    saved.set(current.saveState());
                    HerdSession again = HerdSession.breed(spirit, Species.COW, 8, 2);
                    again.restoreState(saved.get());
                    restored.set(again);
                }
                return;
            }
            HerdSession again = restored.get();
            if (again != null && again.outcome() == HerdSession.Outcome.RUNNING) {
                again.tick();
            }
        });

        helper.succeedWhen(() -> {
            HerdSession first = session.get();
            helper.assertTrue(
                    first != null && first.done() >= 1,
                    "первая сессия обязана свести первую пару до сохранения: иначе нечего восстанавливать");
            helper.assertTrue(saved.get() != null, "состояние обязано сохраниться после первой пары");
            Object done = saved.get().get("done");
            helper.assertTrue(
                    done instanceof Number number && number.intValue() >= 1,
                    "прогресс done обязан пережить saveState/restoreState (не меньше 1 пары), а там " + done);
            HerdSession again = restored.get();
            helper.assertTrue(
                    again != null && again.outcome() == HerdSession.Outcome.SUCCEEDED,
                    "восстановленная сессия обязана довести разведение до конца: "
                            + (again == null
                                    ? "сессия не создана"
                                    : again.outcome() + ", причина " + again.failureReason()));
            helper.assertTrue(
                    again.done() == 2,
                    "восстановленная сессия обязана свести обе пары (done = 2 с учётом сохранённого), фактически "
                            + again.done());
            helper.assertTrue(
                    alive(helper, Cow.class).size() == 6,
                    "после двух пар коров обязано стать 6, фактически "
                            + alive(helper, Cow.class).size());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void feed_SuspendedMidApproach_StopsNavigationAtOnce(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        HusbandryGameTests.fencePerimeter(helper);
        spawnStill(helper, EntityType.COW, new BlockPos(7, 1, 3));
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT, 2));
        AtomicReference<HerdSession> session = new AtomicReference<>();
        AtomicBoolean suspended = new AtomicBoolean();
        AtomicBoolean stoppedAtOnce = new AtomicBoolean();

        helper.onEachTick(() -> {
            if (spirit.tickCount < SETTLE_TICKS || suspended.get()) {
                return;
            }
            if (session.get() == null) {
                session.set(HerdSession.feed(spirit, Species.COW, 8, 1, false));
            }
            session.get().tick();
            if (!spirit.getNavigation().isDone()) {
                session.get().suspend();
                stoppedAtOnce.set(spirit.getNavigation().isDone());
                suspended.set(true);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(suspended.get(), "дух обязан начать идти к корове: иначе приостанавливать нечего");
            helper.assertTrue(
                    stoppedAtOnce.get(),
                    "после suspend навигация духа обязана остановиться в тот же тик: иначе рефлекс политики не "
                            + "перехватит движение к стаду");
            GameTestFixtures.finish(helper);
        });
    }

    static void assertSucceeded(GameTestHelper helper, SpiritEntity spirit, String expectation) {
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                expectation + ": статус "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + spirit.taskFailureReason().orElse("нет"));
    }

    static void assertFailedWith(GameTestHelper helper, SpiritEntity spirit, String fragment) {
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                "задача обязана провалиться, а статус "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
        String reason = spirit.taskFailureReason().orElse("");
        helper.assertTrue(
                reason.contains(fragment),
                "причина провала обязана содержать «" + fragment + "», фактически: " + reason);
    }

    static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        for (int slot = 0; slot < stacks.length; slot++) {
            spirit.getInventory().setItem(slot, stacks[slot]);
        }
        return spirit;
    }

    static <T extends Entity> List<T> alive(GameTestHelper helper, Class<T> type) {
        Vec3 first = helper.absoluteVec(new Vec3(-2, 0, -2));
        Vec3 second = helper.absoluteVec(new Vec3(11, 8, 11));
        return helper.getLevel()
                .getEntitiesOfClass(type, new AABB(first, second), entity -> !entity.isRemoved() && entity.isAlive());
    }

    static <T extends Mob> T spawnStill(GameTestHelper helper, EntityType<T> type, BlockPos relative) {
        T mob = helper.spawn(type, relative);
        mob.setNoAi(true);
        return mob;
    }

    static void spawnBaby(GameTestHelper helper, EntityType<? extends AgeableMob> type, BlockPos relative) {
        spawnBaby(helper, type, relative, false);
    }

    static void spawnBaby(
            GameTestHelper helper, EntityType<? extends AgeableMob> type, BlockPos relative, boolean still) {
        AgeableMob baby = type.create(helper.getLevel());
        baby.setAge(BABY_AGE);
        baby.setNoAi(still);
        Vec3 at = helper.absoluteVec(new Vec3(relative.getX() + 0.5, relative.getY(), relative.getZ() + 0.5));
        baby.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        helper.getLevel().addFreshEntity(baby);
    }

    static void fencePerimeter(GameTestHelper helper) {
        for (int i = 0; i <= 8; i++) {
            helper.setBlock(new BlockPos(i, 1, 0), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(i, 1, 8), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(0, 1, i), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(8, 1, i), Blocks.OAK_FENCE);
        }
    }
}
