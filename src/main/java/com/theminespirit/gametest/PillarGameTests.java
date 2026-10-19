package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.CellCentering;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.PillarUpParams;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.PillarUpScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarGameTests {
    private static final String TEMPLATE = "arena";
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos SPIRIT_START = new BlockPos(5, 1, 5);

    private PillarGameTests() {}

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static SpiritEntity spawnWithDirt(GameTestHelper helper, int dirt) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        if (dirt > 0) {
            spirit.getInventory().setItem(0, new ItemStack(Items.DIRT, dirt));
        }
        return spirit;
    }

    @GameTest(template = TEMPLATE, batch = "pillar_up_down", timeoutTicks = 300)
    public static void pillarUp_FourBlocksThenDown_ReturnsAllBlocks(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWithDirt(helper, 8);
        int[] phase = {0};
        int[] topY = {0};
        int[] startY = {spirit.blockPosition().getY()};
        int[] downDoneAt = {-1};

        helper.onEachTick(() -> {
            if (phase[0] == 0) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    spirit.startTask(new ScriptCall("pillar_up", Map.of("height", 4)));
                    phase[0] = 1;
                }
                return;
            }
            RunStatus status = spirit.taskStatus().orElse(null);
            if (status == RunStatus.FAILED) {
                helper.fail("столб не построен: " + spirit.taskFailureReason().orElse("нет причины"));
                return;
            }
            if (phase[0] == 1 && status == RunStatus.SUCCEEDED) {
                topY[0] = spirit.blockPosition().getY();
                spirit.startTask(new ScriptCall("pillar_down", Map.of()));
                phase[0] = 2;
                return;
            }
            if (phase[0] == 2 && status == RunStatus.SUCCEEDED) {
                phase[0] = 3;
                downDoneAt[0] = spirit.tickCount;
                return;
            }
            if (phase[0] != 3 || spirit.tickCount - downDoneAt[0] < 20) {
                return;
            }
            int dirtInWorld = countDirtInWorld(helper);
            int dirtInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIRT);
            try {
                helper.assertTrue(
                        topY[0] >= startY[0] + 4,
                        "pillar_up высотой 4 обязан поднять духа с y=" + startY[0] + " минимум до y=" + (startY[0] + 4)
                                + ", а он на y=" + topY[0]);
                helper.assertTrue(
                        dirtInWorld == 0,
                        "после pillar_down в мире не остаётся ни одного своего блока столба, найдено " + dirtInWorld);
                helper.assertTrue(
                        dirtInInventory == 8,
                        "четыре блока столба обязаны вернуться в инвентарь: было 8, стало " + dirtInInventory);
                helper.assertTrue(
                        spirit.blockPosition().getY() <= startY[0] + 1,
                        "после разбора дух снова на земле, а он на y="
                                + spirit.blockPosition().getY());
            } finally {
                GameTestFixtures.finish(helper);
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "pillar_up_no_material", timeoutTicks = 100)
    public static void pillarUp_EmptyInventory_FailsWithMaterialReason(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWithDirt(helper, 0);
        PillarUpScript script = new PillarUpScript(spirit, PillarUpParams.from(Map.of("height", 3)));

        ScriptStatus status = script.tick();

        helper.assertTrue(
                status == ScriptStatus.FAILED,
                "без блоков столб не строится: статус " + status + ", причина " + script.failureReason());
        String reason = script.failureReason() == null ? "" : script.failureReason();
        helper.assertTrue(
                reason.contains("нет материала") && reason.contains("0"),
                "причина обязана называть нехватку материала и число (0), фактически: " + reason);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "pillar_up_ceiling", timeoutTicks = 100)
    public static void pillarUp_SolidCeiling_FailsWithCeilingReason(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(SPIRIT_START.getX(), SPIRIT_START.getY() + 1, SPIRIT_START.getZ(), Blocks.STONE);
        SpiritEntity spirit = spawnWithDirt(helper, 8);
        PillarUpScript script = new PillarUpScript(spirit, PillarUpParams.from(Map.of("height", 3)));

        ScriptStatus status = script.tick();

        helper.assertTrue(
                status == ScriptStatus.FAILED,
                "потолок над столбом обязан остановить подъём: статус " + status + ", причина "
                        + script.failureReason());
        String reason = script.failureReason() == null ? "" : script.failureReason();
        helper.assertTrue(reason.contains("потолок"), "причина обязана называть потолок, фактически: " + reason);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    // Потолок в двух клетках над ногами проходит проверку клетки и головы, но не даёт духу
    // подпрыгнуть: раньше каждый тик на земле сбрасывал счётчик застоя, и pillar_up висел
    // в RUNNING вечно (флак chop_tree_tall 17.10). Счётчик обязан идти и на земле.
    @GameTest(template = TEMPLATE, batch = "pillar_up_blocked_jump", timeoutTicks = 400)
    public static void pillarUp_CeilingAboveHead_FailsInsteadOfRunningForever(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(SPIRIT_START.getX(), SPIRIT_START.getY() + 2, SPIRIT_START.getZ(), Blocks.STONE);
        SpiritEntity spirit = spawnWithDirt(helper, 8);
        PillarUpScript script = new PillarUpScript(spirit, PillarUpParams.from(Map.of("height", 3)));
        ScriptStatus[] status = {ScriptStatus.RUNNING};
        int[] ticks = {0};

        helper.onEachTick(() -> {
            if (status[0] != ScriptStatus.RUNNING || spirit.tickCount < SETTLE_TICKS) {
                return;
            }
            ticks[0]++;
            status[0] = script.tick();
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    status[0] == ScriptStatus.FAILED,
                    "потолок над головой обязан остановить pillar_up, а не держать его в RUNNING вечно: статус "
                            + status[0] + ", тиков " + ticks[0] + ", причина "
                            + script.failureReason());
            String reason = script.failureReason() == null ? "" : script.failureReason();
            helper.assertTrue(
                    reason.contains("не поднялся") || reason.contains("потолок"),
                    "причина обязана называть неподнятие или потолок, фактически: " + reason);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = TEMPLATE, batch = "pillar_up_water", timeoutTicks = 100)
    public static void pillarUp_WaterAbove_FailsWithFluidReason(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(SPIRIT_START.getX(), SPIRIT_START.getY() + 1, SPIRIT_START.getZ(), Blocks.WATER);
        SpiritEntity spirit = spawnWithDirt(helper, 8);
        PillarUpScript script = new PillarUpScript(spirit, PillarUpParams.from(Map.of("height", 3)));

        ScriptStatus status = script.tick();

        helper.assertTrue(
                status == ScriptStatus.FAILED,
                "вода над столбом обязана остановить подъём: статус " + status + ", причина " + script.failureReason());
        String reason = script.failureReason() == null ? "" : script.failureReason();
        helper.assertTrue(reason.contains("жидкость"), "причина обязана называть жидкость, фактически: " + reason);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    // [флак] Клетка столба прижата к стволу: тело духа 0.6 шириной, сдвиг к стволу больше 0.2 задевает
    // бревно на прыжке (CellCentering.TOLERANCE) и подъём встаёт (флак chop_tree_tall: «не поднялся на
    // 5-й блок»). Столб обязан возвращать духа в центр клетки, а не оставлять его у ствола.
    @GameTest(template = TEMPLATE, batch = "pillar_up_off_center", timeoutTicks = 300)
    public static void pillarUp_StartedBesideTrunk_ReturnsToCellCenter(GameTestHelper helper) {
        prepare(helper);
        for (int y = 1; y <= 6; y++) {
            helper.setBlock(SPIRIT_START.getX() + 1, y, SPIRIT_START.getZ(), Blocks.OAK_LOG);
        }
        SpiritEntity spirit = spawnWithDirt(helper, 8);
        double centerX = SPIRIT_START.getX() + 0.5;
        double centerZ = SPIRIT_START.getZ() + 0.5;
        // Дух стартует сдвинутым к стволу — как после случайного сноса при подъёме.
        spirit.moveTo(centerX + 0.3, spirit.getY(), centerZ);
        PillarUpScript script = new PillarUpScript(spirit, PillarUpParams.from(Map.of("height", 4)));
        ScriptStatus[] status = {ScriptStatus.RUNNING};
        int[] ticks = {0};

        helper.onEachTick(() -> {
            if (status[0] != ScriptStatus.RUNNING || spirit.tickCount < SETTLE_TICKS) {
                return;
            }
            ticks[0]++;
            status[0] = script.tick();
        });

        helper.succeedWhen(() -> {
            double dx = spirit.getX() - centerX;
            double dz = spirit.getZ() - centerZ;
            helper.assertTrue(
                    status[0] == ScriptStatus.SUCCEEDED
                            && dx * dx + dz * dz <= CellCentering.TOLERANCE * CellCentering.TOLERANCE,
                    "дух, стартовавший у самого ствола, обязан вернуться в центр клетки столба и построить "
                            + "его за " + ticks[0] + " тиков: сдвиг к стволу задевает бревно на прыжке "
                            + "(флак chop_tree_tall), а он на " + spirit.position() + " при центре клетки "
                            + centerX + "," + centerZ + ", статус " + status[0] + ", причина "
                            + script.failureReason());
            GameTestFixtures.finish(helper);
        });
    }

    private static int countDirtInWorld(GameTestHelper helper) {
        int count = 0;
        for (int x = -1; x <= 11; x++) {
            for (int y = 0; y < 8; y++) {
                for (int z = -1; z <= 11; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
