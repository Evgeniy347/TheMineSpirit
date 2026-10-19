package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightAreaGameTests {
    private static final int SETTLE_TICKS = 5;
    // Тёмная комната 7×7 (клетки 1..7) с потолком и стенами: внешний свет тестового мира внутрь не проходит.
    private static final int ROOM_MIN = 1;
    private static final int ROOM_MAX = 7;
    private static final BlockPos CENTER = new BlockPos(4, 1, 4);
    private static final int RADIUS = 3;

    private LightAreaGameTests() {
    }

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                boolean wall = x == 0 || x == 8 || z == 0 || z == 8;
                if (wall) {
                    for (int y = 1; y <= 3; y++) {
                        helper.setBlock(x, y, z, Blocks.STONE);
                    }
                }
                helper.setBlock(x, 3, z, Blocks.STONE);
            }
        }
    }

    private static ScriptCall lightCall(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(CENTER);
        return new ScriptCall("light_area", Map.of("x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ(),
                "radius", RADIUS));
    }

    private static int blockLight(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(relative));
    }

    private static int darkCells(GameTestHelper helper) {
        int dark = 0;
        for (int x = ROOM_MIN; x <= ROOM_MAX; x++) {
            for (int z = ROOM_MIN; z <= ROOM_MAX; z++) {
                if (blockLight(helper, new BlockPos(x, 1, z)) <= 0) {
                    dark++;
                }
            }
        }
        return dark;
    }

    private static int torchesInWorld(GameTestHelper helper) {
        int torches = 0;
        for (int x = ROOM_MIN; x <= ROOM_MAX; x++) {
            for (int z = ROOM_MIN; z <= ROOM_MAX; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.TORCH)) {
                    torches++;
                }
            }
        }
        return torches;
    }

    private static Map<String, Object> dataOf(GameTestHelper helper, SpiritEntity spirit, String what) {
        helper.assertTrue(spirit.taskResult().orElse(null) instanceof ScriptResult.Data,
                what + " обязан нести Data-итог, фактически "
                        + spirit.taskResult().map(result -> result.getClass().getSimpleName()).orElse("пусто"));
        return ((ScriptResult.Data) spirit.taskResult().orElseThrow()).fields();
    }

    @GameTest(template = "arena", batch = "light_area", timeoutTicks = 400)
    public static void light_DarkRoom_EveryCellLitByOneTorch(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), CENTER);
        spirit.getInventory().setItem(0, new ItemStack(Items.TORCH, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, lightCall(helper), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "освещение обязано завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(darkCells(helper) == 0,
                    "после освещения в комнате не должно остаться клеток со светом 0: "
                            + "враждебные мобы спавнятся при блочном свете 0, тёмных " + darkCells(helper));
            helper.assertTrue(torchesInWorld(helper) == 1,
                    "комнату 7×7 освещает один факел: лишние — перерасход, в мире " + torchesInWorld(helper));
            BlockPos torchCell = null;
            for (int x = ROOM_MIN; x <= ROOM_MAX; x++) {
                for (int z = ROOM_MIN; z <= ROOM_MAX; z++) {
                    BlockPos relative = new BlockPos(x, 1, z);
                    if (helper.getBlockState(relative).is(Blocks.TORCH)) {
                        torchCell = helper.absolutePos(relative);
                    }
                }
            }
            PlacedBlocksData placed = PlacedBlocksData.get(helper.getLevel());
            helper.assertTrue(torchCell != null && placed.isSpiritMarked(torchCell),
                    "поставленный духом факел обязан быть помечен: иначе дух не разберёт своё; клетка " + torchCell
                            + " isMarked=" + (torchCell != null && placed.isMarked(torchCell))
                            + " isSpiritMarked=" + (torchCell != null && placed.isSpiritMarked(torchCell))
                            + " size=" + placed.size());
            Map<String, Object> data = dataOf(helper, spirit, "успешный light_area");
            helper.assertTrue(Integer.valueOf(1).equals(data.get("placed")) && "minecraft:torch".equals(data.get("block")),
                    "итог обязан назвать placed=1 и block=minecraft:torch, фактически " + data);
            helper.assertTrue(Integer.valueOf(0).equals(data.get("missing")),
                    "факела хватило: недостачи быть не должно, фактически " + data);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "light_area", timeoutTicks = 400)
    public static void light_NoTorches_ReportsMissingPartial(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), CENTER);
        SpiritRun run = SpiritRun.launch(helper, spirit, lightCall(helper), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "нехватка факелов обязана дать частичный итог, а не провал, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(torchesInWorld(helper) == 0,
                    "без факелов в инвентаре в мире не появляется ни одного, в мире " + torchesInWorld(helper));
            helper.assertTrue(darkCells(helper) > 0,
                    "пока факелов нет, комната остаётся тёмной: без света мобы спавнятся, тёмных " + darkCells(helper));
            Map<String, Object> data = dataOf(helper, spirit, "частичный light_area");
            helper.assertTrue(Integer.valueOf(0).equals(data.get("placed")),
                    "итог обязан назвать placed=0, фактически " + data);
            helper.assertTrue(Integer.valueOf(1).equals(data.get("missing")),
                    "итог обязан назвать 1 недостающий факел, фактически " + data);
            GameTestFixtures.finish(helper);
        });
    }
}
