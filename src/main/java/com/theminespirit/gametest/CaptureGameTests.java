package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CaptureGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int HOLD_TICKS = 200;
    private static final BlockPos START = new BlockPos(1, 1, 1);
    private static final BlockPos FLOCK = new BlockPos(4, 1, 4);
    private static final String BATCH = "capture";
    // Кольцо — около 52 блоков. Если животное выйдет из кольца, кольцо пересобирается вокруг нового
    // центра, а часть уже поставленных блоков остаётся в мире и в новое кольцо не входит. Запас 128
    // переживает одну-две пересборки (флак capture_sheeponflat: «нужно 52, уже поставлено 0, есть 51»).
    private static final int RING_MATERIAL = 128;

    private CaptureGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void stopMobsTargetingSpirits(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = BATCH)
    public static void restoreMobTargeting(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(true);
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 400)
    public static void capture_SheepOnFlat_CaughtAndHeld200Ticks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.spawn(EntityType.SHEEP, FLOCK);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.DIRT, RING_MATERIAL));
        CaptureProbe probe = CaptureProbe.attach(helper, spirit);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("capture", Map.of("species", "sheep", "radius", 8)), SETTLE_TICKS);
        AtomicInteger successTick = new AtomicInteger(-1);
        AtomicReference<BlockPos> center = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED && successTick.get() < 0) {
                successTick.set(spirit.tickCount);
                center.set(readCenter(spirit));
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(successTick.get() >= 0,
                    "овца на ровной земле обязана пойматься: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + "\n" + probe.report("нет поимки"));
            helper.assertTrue(spirit.tickCount - successTick.get() >= HOLD_TICKS,
                    "после поимки ждём " + HOLD_TICKS + " тиков: иначе неизвестно, держит ли кольцо");
            BlockPos anchor = center.get();
            helper.assertTrue(anchor != null, "итог поимки обязан нести центр кольца: без него негде искать овцу");
            Sheep sheep = onlySheep(helper);
            helper.assertTrue(sheep.blockPosition().getX() >= anchor.getX() - 1
                    && sheep.blockPosition().getX() <= anchor.getX() + 2
                    && sheep.blockPosition().getZ() >= anchor.getZ() - 1
                    && sheep.blockPosition().getZ() <= anchor.getZ() + 2,
                    "через " + HOLD_TICKS + " тиков овца обязана оставаться в кольце вокруг " + anchor + ", фактически "
                            + sheep.blockPosition().toShortString() + "\n" + probe.report("овца вышла из кольца"));
            helper.assertTrue(ringBlocks(helper) >= 24,
                    "кольцо вокруг овцы обязано стоять из всех 24 блоков: иначе считать поимку нельзя");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 400)
    public static void capture_SheepByStep_RingThreeHighAndCaught(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(4, 2, 4));
        sheep.setNoAi(true);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.DIRT, RING_MATERIAL));
        CaptureProbe probe = CaptureProbe.attach(helper, spirit);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("capture", Map.of("species", "sheep", "radius", 8)), SETTLE_TICKS);
        AtomicInteger successTick = new AtomicInteger(-1);
        helper.onEachTick(() -> {
            if (spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED && successTick.get() < 0) {
                successTick.set(spirit.tickCount);
            }
            // Овца уходит: кольцо пересчитывается за ней. Материал на уже поставленные блоки не списывается
            // заново — иначе на 17-й постановке дух видит «нужно 48, есть 47» и бросает отлов.
            if (successTick.get() < 0) {
                sheep.move(MoverType.SELF, new Vec3(0.06, 0, 0));
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "овца у ступеньки обязана пойматься: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + "\n" + probe.report("нет поимки"));
            helper.assertTrue(maxColumn(helper) == 3,
                    "кольцо у ступеньки местами обязано быть из 3 блоков: стенка в 2 блока от низкой земли "
                            + "лишь на 1 выше высокого пола, и овца уходит прыжком");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 400)
    public static void capture_SheepWalkingAway_BlockedAndCaught(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Sheep sheep = helper.spawn(EntityType.SHEEP, FLOCK);
        sheep.setNoAi(true);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.DIRT, RING_MATERIAL));
        CaptureProbe probe = CaptureProbe.attach(helper, spirit);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("capture", Map.of("species", "sheep", "radius", 8)), SETTLE_TICKS);
        AtomicInteger successTick = new AtomicInteger(-1);
        AtomicReference<BlockPos> center = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED && successTick.get() < 0) {
                successTick.set(spirit.tickCount);
                center.set(readCenter(spirit));
            }
            if (successTick.get() < 0) {
                sheep.move(MoverType.SELF, new Vec3(0.08, 0, 0));
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(successTick.get() >= 0,
                    "овца, идущая на восток, обязана пойматься: дух обязан отрезать её стенкой впереди. Статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + "\n" + probe.report("нет поимки"));
            helper.assertTrue(spirit.tickCount - successTick.get() >= HOLD_TICKS,
                    "после поимки ждём " + HOLD_TICKS + " тиков: иначе неизвестно, держит ли кольцо идущую овцу");
            BlockPos anchor = center.get();
            helper.assertTrue(anchor != null, "итог поимки обязан нести центр кольца: без него негде искать овцу");
            helper.assertTrue(ringBlocks(helper) >= 24,
                    "кольцо вокруг идущей овцы обязано стоять из всех 24 блоков: иначе считать поимку нельзя");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 400)
    public static void capture_WithoutBlocks_FailsExplainingShortage(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.spawn(EntityType.SHEEP, FLOCK);
        SpiritEntity spirit = spawnWith(helper, START);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("capture", Map.of("species", "sheep", "radius", 8)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "без блоков отлов обязан провалиться, а статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("не хватило блоков"),
                    "причина провала обязана объяснять нехватку блоков, фактически: " + reason);
            helper.assertTrue(alive(helper).size() == 1,
                    "провал без блоков обязан оставлять овцу целой: иначе отлов калечит стадо");
            GameTestFixtures.finish(helper);
        });
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        for (int slot = 0; slot < stacks.length; slot++) {
            spirit.getInventory().setItem(slot, stacks[slot]);
        }
        return spirit;
    }

    private static BlockPos readCenter(SpiritEntity spirit) {
        if (spirit.taskResult().orElse(null) instanceof ScriptResult.Data data
                && data.fields().get("center") instanceof List<?> point && point.size() == 3
                && point.get(0) instanceof Number x && point.get(2) instanceof Number z) {
            return new BlockPos(x.intValue(), 1, z.intValue());
        }
        return null;
    }

    private static Sheep onlySheep(GameTestHelper helper) {
        List<Sheep> sheep = alive(helper);
        helper.assertTrue(sheep.size() == 1,
                "овец обязана остаться ровно 1 (фактически " + sheep.size() + "): иначе проверяем не ту поимку");
        return sheep.get(0);
    }

    private static List<Sheep> alive(GameTestHelper helper) {
        Vec3 first = helper.absoluteVec(new Vec3(-2, 0, -2));
        Vec3 second = helper.absoluteVec(new Vec3(11, 8, 11));
        return helper.getLevel().getEntitiesOfClass(Sheep.class, new AABB(first, second),
                entity -> !entity.isRemoved() && entity.isAlive());
    }

    private static int ringBlocks(GameTestHelper helper) {
        int count = 0;
        for (int x = 0; x < 9; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 0; z < 9; z++) {
                    var state = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z)));
                    if (state.is(Blocks.DIRT) || state.is(Blocks.COBBLESTONE)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int maxColumn(GameTestHelper helper) {
        int tallest = 0;
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                int column = 0;
                for (int y = 1; y <= 5; y++) {
                    var state = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z)));
                    if (state.is(Blocks.DIRT) || state.is(Blocks.COBBLESTONE)) {
                        column++;
                    } else {
                        break;
                    }
                }
                tallest = Math.max(tallest, column);
            }
        }
        return tallest;
    }
}
