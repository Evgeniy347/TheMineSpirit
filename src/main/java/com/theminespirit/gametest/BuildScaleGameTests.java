package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.FillSession;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
public final class BuildScaleGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int CELLS = 8;

    private BuildScaleGameTests() {
    }

    @GameTest(template = "arena", batch = "build_scale", timeoutTicks = 400)
    public static void fill_EightCells_OneBlockPerTickWithoutRescan(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, CELLS));
        BlockPos from = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos to = helper.absolutePos(new BlockPos(5, 2, 5));
        AtomicReference<FillSession> session = new AtomicReference<>();
        AtomicInteger startTick = new AtomicInteger(-1);
        List<Integer> placedPerTick = new ArrayList<>();
        helper.onEachTick(() -> {
            if (session.get() == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(new FillSession(spirit, "cobblestone",
                            BuildBox.of(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ())));
                    startTick.set(spirit.tickCount);
                }
            } else if (session.get().outcome() == FillSession.Outcome.RUNNING) {
                int before = session.get().placed();
                session.get().tick();
                placedPerTick.add(session.get().placed() - before);
            }
        });

        helper.succeedWhen(() -> {
            FillSession run = session.get();
            helper.assertTrue(run != null && run.outcome() != FillSession.Outcome.RUNNING,
                    "сессия обязана завершиться, а не висеть в RUNNING: "
                            + (run == null ? "сессия не стартовала" : run.outcome().name()));
            helper.assertTrue(run.outcome() == FillSession.Outcome.SUCCEEDED,
                    "заливка 8 клеток обязана завершиться успехом: " + run.failureReason());
            helper.assertTrue(run.placed() == CELLS,
                    "все 8 клеток обязаны встать, поставлено " + run.placed());
            for (int delta : placedPerTick) {
                helper.assertTrue(delta <= 1,
                        "за тик ставится не больше одного блока: иначе тик перегружен, дельта " + delta);
            }
            helper.assertTrue(run.blockReads() == CELLS,
                    "область обходится один раз: чтений клеток обязано быть 8, фактически " + run.blockReads());
            helper.assertTrue(spirit.tickCount - startTick.get() <= 380,
                    "заливка 8 блоков обязана уложиться в 380 тиков духа, заняло "
                            + (spirit.tickCount - startTick.get()));
            GameTestFixtures.finish(helper);
        });
    }
}
