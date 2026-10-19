package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Темп стройки по схеме из таблицы building/build.json (решение владельца 18.10: около 2 блоков в секунду, взмах руки
 * обязателен).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildPaceGameTests {
    private static final String BATCH = "build_pace";
    private static final int SETTLE_TICKS = 5;

    private BuildPaceGameTests() {}

    // [флак] 17 блоков по 10 тиков = 170 тиков стройки плюс подход; потолок ≈ 2× замера.
    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 600)
    public static void pace_FixturePlate_PlacesNoFasterThanTableAndSwingsArm(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        int interval = DataTables.INSTANCE.building().current().placeIntervalTicks();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(7, 1, 7));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 17));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "build_schematic",
                        Map.of("file", "fixture_plate", "x", anchor.getX(), "y", anchor.getY(), "z", anchor.getZ())),
                SETTLE_TICKS);
        List<Long> placedAt = new ArrayList<>();
        int[] lastCount = {0};
        boolean[] swung = {false};
        helper.onEachTick(() -> {
            int count = 0;
            for (BlockPos pos : BlockPos.betweenClosed(anchor, anchor.offset(2, 1, 2))) {
                if (helper.getLevel().getBlockState(pos).is(Blocks.COBBLESTONE)) {
                    count++;
                }
            }
            if (count > lastCount[0]) {
                placedAt.add(helper.getTick());
                lastCount[0] = count;
            }
            swung[0] |= spirit.swinging;
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Плита из 17 блоков обязана достроиться; статус " + spirit.taskStatus() + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            for (int i = 1; i < placedAt.size(); i++) {
                long gap = placedAt.get(i) - placedAt.get(i - 1);
                helper.assertTrue(
                        gap >= interval,
                        "Между блоками стройки обязан быть интервал не меньше " + interval
                                + " тиков (building/build.json, ~2 блока в секунду): иначе стройку не разглядеть; блоки "
                                + "встали в тики " + placedAt);
            }
            helper.assertTrue(
                    placedAt.size() == 17,
                    "Каждый блок обязан появиться отдельно: замечено " + placedAt.size() + " постановок в тики "
                            + placedAt);
            helper.assertTrue(swung[0], "Дух обязан взмахивать рукой при постановке: без этого не видно, кто ставит");
            GameTestFixtures.finish(helper);
        });
    }
}
