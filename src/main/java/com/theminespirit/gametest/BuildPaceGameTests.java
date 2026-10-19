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
    private static final int PLATE_BLOCKS = 17;

    private BuildPaceGameTests() {}

    // [флак] 17 блоков по 10 тиков = 170 тиков стройки плюс подход; потолок ≈ 2× замера.
    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 600)
    public static void pace_FixturePlate_PlacesNoFasterThanTableAndSwingsArm(GameTestHelper helper) {
        int interval = DataTables.INSTANCE.building().current().intervalTicks(false);
        Pace pace = Pace.start(helper, false);

        helper.succeedWhen(() -> {
            pace.assertBuilt(helper);
            for (int i = 1; i < pace.placedAt.size(); i++) {
                long gap = pace.placedAt.get(i) - pace.placedAt.get(i - 1);
                helper.assertTrue(
                        gap >= interval,
                        "Между блоками стройки обязан быть интервал не меньше " + interval
                                + " тиков (building/build.json, ~2 блока в секунду): иначе стройку не разглядеть; блоки "
                                + "встали в тики " + pace.placedAt);
            }
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 200)
    public static void pace_DemoSpirit_PlacesAtDemoPace(GameTestHelper helper) {
        int demoInterval = DataTables.INSTANCE.building().current().intervalTicks(true);
        int normalInterval = DataTables.INSTANCE.building().current().intervalTicks(false);
        Pace pace = Pace.start(helper, true);

        helper.succeedWhen(() -> {
            pace.assertBuilt(helper);
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < pace.placedAt.size(); i++) {
                gaps.add(pace.placedAt.get(i) - pace.placedAt.get(i - 1));
            }
            gaps.sort(Long::compare);
            long median = gaps.get(gaps.size() / 2);
            helper.assertTrue(
                    gaps.get(0) >= demoInterval && median <= demoInterval + 1 && median < normalInterval,
                    "Демо-стройка обязана идти темпом demoPlaceIntervalTicks = " + demoInterval
                            + " тика на блок (владелец 19.10: 10 блоков в секунду), а не темпом обычной игры "
                            + normalInterval + "; промежутки " + gaps);
            GameTestFixtures.finish(helper);
        });
    }

    private static final class Pace {
        private final List<Long> placedAt = new ArrayList<>();
        private final SpiritEntity spirit;
        private final SpiritRun run;
        private int lastCount;
        private boolean swung;

        private Pace(SpiritEntity spirit, SpiritRun run) {
            this.spirit = spirit;
            this.run = run;
        }

        static Pace start(GameTestHelper helper, boolean demo) {
            GameTestFixtures.reset(helper);
            GameTestFixtures.buildFloor(helper, Blocks.STONE);
            BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
            SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(7, 1, 7));
            spirit.setDemo(demo);
            spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, PLATE_BLOCKS));
            SpiritRun run = SpiritRun.launch(
                    helper,
                    spirit,
                    new ScriptCall(
                            "build_schematic",
                            Map.of(
                                    "file",
                                    "fixture_plate",
                                    "x",
                                    anchor.getX(),
                                    "y",
                                    anchor.getY(),
                                    "z",
                                    anchor.getZ())),
                    SETTLE_TICKS);
            Pace pace = new Pace(spirit, run);
            helper.onEachTick(() -> {
                int count = 0;
                for (BlockPos pos : BlockPos.betweenClosed(anchor, anchor.offset(2, 1, 2))) {
                    if (helper.getLevel().getBlockState(pos).is(Blocks.COBBLESTONE)) {
                        count++;
                    }
                }
                if (count > pace.lastCount) {
                    pace.placedAt.add(helper.getTick());
                    pace.lastCount = count;
                }
                pace.swung |= spirit.swinging;
            });
            return pace;
        }

        void assertBuilt(GameTestHelper helper) {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Плита из 17 блоков обязана достроиться; статус " + spirit.taskStatus() + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    placedAt.size() == PLATE_BLOCKS,
                    "Каждый блок обязан появиться отдельно: замечено " + placedAt.size() + " постановок в тики "
                            + placedAt);
            helper.assertTrue(swung, "Дух обязан взмахивать рукой при постановке: без этого не видно, кто ставит");
        }
    }
}
