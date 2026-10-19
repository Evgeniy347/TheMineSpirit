package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
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
public final class DescentGameTests {
    private static final int SETTLE_TICKS = 5;

    private DescentGameTests() {}

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 3; x <= 6; x++) {
            for (int y = 0; y <= 7; y++) {
                for (int z = 2; z <= 5; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(new BlockPos(3, 5, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 6, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 7, 4), Blocks.AIR);
    }

    private static SpiritEntity spawnWithKit(GameTestHelper helper, BlockPos relative) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE, 1));
        spirit.getInventory().setItem(1, new ItemStack(Items.TORCH, 16));
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

    private static int torchesInShaft(GameTestHelper helper) {
        int total = 0;
        for (int x = 2; x <= 7; x++) {
            for (int y = 1; y <= 7; y++) {
                for (int z = 3; z <= 5; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.TORCH)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    @GameTest(template = "arena", batch = "descent", timeoutTicks = 400)
    public static void descend_SafeShaft_ReachesTargetYAndLightsShaft(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWithKit(helper, new BlockPos(3, 5, 4));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("dig_descent", Map.of("direction", "east", "y", -59)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "безопасный спуск обязан завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = dataOf(helper, spirit, "успешный dig_descent");
            helper.assertTrue(
                    Boolean.TRUE.equals(data.get("reached")),
                    "спуск без опасности обязан дойти до цели, фактически " + data);
            helper.assertTrue(
                    Integer.valueOf(-59).equals(data.get("y"))
                            && Integer.valueOf(3).equals(data.get("depth")),
                    "итог обязан назвать y=-59 и depth=3 (этаж под ногами -56 минус цель -59), фактически " + data);
            helper.assertTrue(
                    Integer.valueOf(9).equals(data.get("blocks_dug")),
                    "итог обязан насчитать 9 выкопанных блоков (три шага по три клетки, шаг 0 уже воздух), "
                            + "фактически " + data);
            for (int y = 4; y <= 6; y++) {
                helper.assertTrue(
                        helper.getBlockState(new BlockPos(4, y, 4)).isAir()
                                || helper.getBlockState(new BlockPos(4, y, 4)).is(Blocks.TORCH),
                        "колонка шага 1 (4," + y + ",4) обязана быть выкопана");
            }
            for (int y = 3; y <= 5; y++) {
                helper.assertTrue(
                        helper.getBlockState(new BlockPos(5, y, 4)).isAir(),
                        "колонка шага 2 (5," + y + ",4) обязана быть выкопана");
            }
            for (int y = 2; y <= 4; y++) {
                helper.assertTrue(
                        helper.getBlockState(new BlockPos(6, y, 4)).isAir(),
                        "колонка шага 3 (6," + y + ",4) обязана быть выкопана");
            }
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(6, 1, 4)).is(Blocks.STONE)
                            && helper.getBlockState(new BlockPos(6, 0, 4)).is(Blocks.STONE),
                    "пол под последней ступенью обязан остаться камнем: спуск не копает под своим полом");
            helper.assertTrue(
                    torchesInShaft(helper) == 1
                            && helper.getBlockState(new BlockPos(3, 5, 4)).is(Blocks.TORCH),
                    "на ступенях обязан стоять ровно один факел — в клетке ступени шага 0, фактически факелов "
                            + torchesInShaft(helper));
            helper.assertTrue(
                    spirit.blockPosition().getY() == -58,
                    "дух обязан спуститься на ступень последней шага (y=-58), фактически y="
                            + spirit.blockPosition().getY());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "descent", timeoutTicks = 400)
    public static void descend_UnreachableDropInShaft_StillReachesTargetY(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.AIR);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 3.5, 2, 4.5);
        SpiritEntity spirit = spawnWithKit(helper, new BlockPos(3, 5, 4));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("dig_descent", Map.of("direction", "east", "y", -59)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "недостижимый дроп в шахте не обязан останавливать спуск: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = dataOf(helper, spirit, "спуск с недостижимым дропом");
            helper.assertTrue(
                    Boolean.TRUE.equals(data.get("reached"))
                            && Integer.valueOf(-59).equals(data.get("y")),
                    "спуск обязан дойти до цели, невзирая на дроп в закрытой нише, фактически " + data);
            helper.assertTrue(
                    Integer.valueOf(9).equals(data.get("blocks_dug")),
                    "спуск обязан выкопать все 9 блоков шагов, не прерываясь на дроп, фактически " + data);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "descent", timeoutTicks = 400)
    public static void descend_LavaInShaft_WarnsAndStopsWithWorldSafe(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.LAVA);
        helper.assertTrue(
                helper.getBlockState(new BlockPos(5, 2, 4)).is(Blocks.LAVA),
                "лава обязана встать в (5,2,4): пол шага 2 обязан стать жидкостью, а не твёрдым");
        SpiritEntity spirit = spawnWithKit(helper, new BlockPos(3, 5, 4));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("dig_descent", Map.of("direction", "east", "y", -59)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "остановка по опасности — не поломка задачи: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            Map<String, Object> data = dataOf(helper, spirit, "остановленный по лаве dig_descent");
            helper.assertTrue(
                    Boolean.FALSE.equals(data.get("reached")),
                    "до цели не дошли: лава обязана отметить reached=false, фактически " + data);
            String reason = String.valueOf(data.get("reason"));
            helper.assertTrue(
                    reason.contains("опасность") && reason.contains("лава"),
                    "причина обязана начинаться с 'опасность' и называть лаву, фактически " + data);
            helper.assertTrue(
                    Integer.valueOf(3).equals(data.get("blocks_dug"))
                            && Integer.valueOf(-57).equals(data.get("y"))
                            && Integer.valueOf(1).equals(data.get("depth")),
                    "итог обязан назвать blocks_dug=3, y=-57, depth=1: два шага до опасности, фактически " + data);
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(5, 3, 4)).is(Blocks.STONE),
                    "колонка шага с лавой (5,3,4) обязана остаться камнем: перед опасностью не копают");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(5, 2, 4)).is(Blocks.LAVA),
                    "лава обязана остаться лавой: спуск не сливает и не заливает её");
            helper.assertTrue(
                    torchesInShaft(helper) == 1
                            && helper.getBlockState(new BlockPos(3, 5, 4)).is(Blocks.TORCH),
                    "до остановки обязан стоять факел шага 0 в (3,5,4), фактически факелов " + torchesInShaft(helper));
            helper.assertTrue(
                    spirit.blockPosition().getY() == -56,
                    "дух обязан остаться на ступени шага 1 (y=-56), фактически y="
                            + spirit.blockPosition().getY());
            GameTestFixtures.reset(helper);
        });
    }
}
