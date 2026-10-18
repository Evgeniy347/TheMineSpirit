package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
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
    private static final BlockPos START = new BlockPos(1, 1, 8);
    private static final BlockPos HOME = new BlockPos(2, 1, 2);
    private static final BlockPos FLOCK = new BlockPos(8, 1, 8);
    private static final String BATCH = "capture";
    // [флак] овца идёт за кормом шагом ванильной навигации; потолок ≈ 2× замера ~300 тиков.
    private static final int CAPTURE_TIMEOUT_TICKS = 800;

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

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = CAPTURE_TIMEOUT_TICKS)
    public static void capture_SheepOutsidePenWithWheat_LuredIntoPenWithoutBlocks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.spawn(EntityType.SHEEP, FLOCK);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT, 4), new ItemStack(Items.DIRT, 64));
        spirit.setHome(absolute(helper, HOME));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("capture", Map.of("species", "sheep", "radius", 12)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "овца с пшеницей у духа обязана прийти в загон у дома (упрощение 44.4: приманка вместо кольца): "
                            + "статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Sheep sheep = onlySheep(helper);
            BlockCoord home = absolute(helper, HOME);
            double dx = sheep.getX() - (home.x() + 0.5);
            double dz = sheep.getZ() - (home.z() + 0.5);
            helper.assertTrue(dx * dx + dz * dz <= 4.5 * 4.5,
                    "овца обязана стоять в загоне у дома (радиус " + CatalogLimits.CAPTURE_PEN_RADIUS + "), фактически "
                            + sheep.blockPosition().toShortString() + ", дом " + home);
            helper.assertTrue(placedBlocks(helper) == 0,
                    "отлов приманкой не ставит ни одного блока земли из инвентаря: кольцо из блоков убрано решением "
                            + "владельца 18.10");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 200)
    public static void capture_WithoutFeed_FailsNamingFeed(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.spawn(EntityType.SHEEP, FLOCK);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.DIRT, 16));
        spirit.setHome(absolute(helper, HOME));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("capture", Map.of("species", "sheep", "radius", 12)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED && reason.contains("корма"),
                    "без корма отлов обязан провалиться с причиной «нет корма», фактически "
                            + spirit.taskStatus() + ": " + reason);
            helper.assertTrue(placedBlocks(helper) == 0, "провал без корма не ставит блоков");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 200)
    public static void capture_WithoutHome_FailsNamingHome(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.spawn(EntityType.SHEEP, FLOCK);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("capture", Map.of("species", "sheep", "radius", 12)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED && reason.contains("дома"),
                    "без дома вести животное некуда: отлов обязан провалиться с причиной, фактически "
                            + spirit.taskStatus() + ": " + reason);
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

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
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

    private static int placedBlocks(GameTestHelper helper) {
        int count = 0;
        for (int x = 0; x < 11; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 0; z < 11; z++) {
                    if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).is(Blocks.DIRT)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
