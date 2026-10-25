package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SafeMineGameTests {
    private static final int SETTLE_TICKS = 5;

    private SafeMineGameTests() {}

    private static SpiritEntity spawnWithKit(GameTestHelper helper, BlockPos relative) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE, 1));
        spirit.getInventory().setItem(1, new ItemStack(Items.TORCH, 16));
        spirit.getInventory().setItem(2, new ItemStack(Items.COBBLESTONE, 16));
        return spirit;
    }

    private static Map<String, Object> dataOf(GameTestHelper helper, SpiritEntity spirit, String what) {
        helper.assertTrue(
                spirit.taskResult().orElse(null) instanceof ScriptResult.Data,
                what + " обязан нести Data-итог общим каналом, фактически "
                        + spirit.taskResult()
                                .map(result -> result.getClass().getSimpleName())
                                .orElse("пусто"));
        return ((ScriptResult.Data) spirit.taskResult().orElseThrow()).fields();
    }

    private static boolean cleared(SpiritEntity spirit) {
        return spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED;
    }

    private static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, Map<String, Object> params) {
        return SpiritRun.launch(helper, spirit, new ScriptCall("safe_mine", params), SETTLE_TICKS);
    }

    private static void fillStone(GameTestHelper helper, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static void assertPassageClear(GameTestHelper helper, int x1, int x2, int floor, int z) {
        for (int x = x1; x <= x2; x++) {
            for (int height = 1; height <= 2; height++) {
                BlockPos cell = new BlockPos(x, floor + height, z);
                helper.assertTrue(
                        helper.getBlockState(cell).isAir()
                                || helper.getBlockState(cell).is(Blocks.TORCH),
                        "клетка прохода " + cell + " обязана быть выкопана, фактически " + helper.getBlockState(cell));
            }
        }
    }

    // [флак] восемь ступеней (14 блоков), подход и сбор дропа на каждой ступени: запас 2× к ожидаемым тикам.
    @SceneStep(template = "mine_arena", timeoutTicks = 800)
    public static void staircase_EightSteps_ReachesTargetYLitByTorches(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        fillStone(helper, 3, 0, 2, 10, 15, 6);
        helper.setBlock(new BlockPos(3, 8, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 9, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 10, 4), Blocks.AIR);
        SpiritEntity spirit = spawnWithKit(helper, new BlockPos(3, 8, 4));
        int targetY = spirit.blockPosition().getY() - 8;
        SpiritRun run = launch(
                helper, spirit, Map.of("mode", "staircase", "direction", "east", "y", targetY, "torchInterval", 4));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    cleared(spirit),
                    "спуск лесенкой обязан завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = dataOf(helper, spirit, "успешный safe_mine staircase");
            helper.assertTrue(
                    Boolean.TRUE.equals(data.get("reached"))
                            && Integer.valueOf(targetY).equals(data.get("y"))
                            && Integer.valueOf(7).equals(data.get("depth")),
                    "лесенка обязана дойти до этажа " + targetY + " с глубиной 7, фактически " + data);
            helper.assertTrue(
                    Integer.valueOf(2).equals(data.get("torches")),
                    "на восьми ступенях с интервалом 4 обязано быть два факела (шаги 0 и 4), фактически " + data);
            helper.assertTrue(
                    spirit.blockPosition().getY() == targetY + 1,
                    "дух обязан встать на последнюю ступень (y=" + (targetY + 1) + "), фактически y="
                            + spirit.blockPosition().getY());
            assertStairsClear(helper, 3, targetY);
            GameTestFixtures.finish(helper, 16);
        });
    }

    private static void assertStairsClear(GameTestHelper helper, int x1, int targetY) {
        int entryFloor = targetY + 7;
        for (int index = 0; index < 8; index++) {
            int floor = entryFloor - index;
            for (int height = 1; height <= 3; height++) {
                BlockPos cell = new BlockPos(x1 + index, floor + height, 4);
                helper.assertTrue(
                        helper.getBlockState(cell).isAir()
                                || helper.getBlockState(cell).is(Blocks.TORCH),
                        "ступень " + cell + " обязана быть выкопана, фактически " + helper.getBlockState(cell));
            }
        }
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void tunnel_LavaBeside_IsFilledAndSpiritSurvives(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        fillStone(helper, 0, 0, 0, 10, 0, 8);
        fillStone(helper, 1, 1, 3, 10, 3, 5);
        helper.setBlock(new BlockPos(1, 1, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 2, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.LAVA);
        SpiritEntity spirit = spawnWithKit(helper, new BlockPos(1, 1, 4));
        SpiritRun run =
                launch(helper, spirit, Map.of("mode", "tunnel", "direction", "east", "length", 10, "torchInterval", 4));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    cleared(spirit),
                    "штольня обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = dataOf(helper, spirit, "успешный safe_mine tunnel с лавой");
            helper.assertTrue(Boolean.TRUE.equals(data.get("reached")), "штольня обязана быть пройдена, " + data);
            helper.assertTrue(
                    !helper.getBlockState(new BlockPos(5, 1, 3)).is(Blocks.LAVA),
                    "лава сбоку обязана быть заложена блоком, фактически "
                            + helper.getBlockState(new BlockPos(5, 1, 3)));
            helper.assertTrue(spirit.isAlive(), "дух обязан выжить: лава заложена до копания прохода");
            assertPassageClear(helper, 1, 10, 0, 4);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void tunnel_GravelAbove_DoesNotKillAndPassageStaysClear(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        fillStone(helper, 0, 0, 0, 10, 0, 8);
        fillStone(helper, 1, 1, 3, 6, 3, 5);
        helper.setBlock(new BlockPos(1, 1, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 2, 4), Blocks.AIR);
        for (int x = 3; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 3, 4), Blocks.GRAVEL);
        }
        SpiritEntity spirit = spawnWithKit(helper, new BlockPos(1, 1, 4));
        SpiritRun run =
                launch(helper, spirit, Map.of("mode", "tunnel", "direction", "east", "length", 6, "torchInterval", 4));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    cleared(spirit),
                    "штольня под гравием обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.isAlive() && spirit.getHealth() == spirit.getMaxHealth(),
                    "падающий гравий не должен ранить духа: здоровье " + spirit.getHealth() + " из "
                            + spirit.getMaxHealth());
            assertPassageClear(helper, 1, 6, 0, 4);
            for (int x = 3; x <= 6; x++) {
                helper.assertTrue(
                        !helper.getBlockState(new BlockPos(x, 1, 4)).is(Blocks.GRAVEL)
                                && !helper.getBlockState(new BlockPos(x, 1, 4)).is(Blocks.SAND),
                        "осыпавшийся гравий обязан быть выкопан из прохода (" + x + ",1,4), фактически "
                                + helper.getBlockState(new BlockPos(x, 1, 4)));
            }
            GameTestFixtures.finish(helper);
        });
    }
}
