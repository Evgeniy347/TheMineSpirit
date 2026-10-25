package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.movement.PersonalSpace;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Личное пространство игрока (владелец 19.10): дух не стоит вплотную к игроку — отходит на 1–2 клетки и продолжает
 * работу, в том числе посреди стройки.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PersonalSpaceGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int MAX_CROWDED_TICKS = 30;

    private PersonalSpaceGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void idleSpirit_PlayerInNeighbourCell_StepsAway(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(5, 1, 5));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(6, 1, 5), "personal_space_idle");
        PersonalSpace rules = DataTables.INSTANCE.personalSpace().current();

        helper.succeedWhen(() -> {
            double distance = spirit.distanceTo(player);
            helper.assertTrue(
                    distance >= rules.minDistance(),
                    "Игрок в соседней клетке — дух обязан отойти не ближе " + rules.minDistance()
                            + " (movement/personal_space.json): вплотную он мешает пройти; сейчас " + distance);
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] 17 блоков по 10 тиков плюс шаги в сторону; потолок ≈ 2× замера.
    @SceneStep(template = "arena", timeoutTicks = 600)
    public static void build_PlayerStandsNextToSpirit_SpiritStepsAsideAndFinishes(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(7, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 17));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(7, 1, 5), "personal_space_build");
        PersonalSpace rules = DataTables.INSTANCE.personalSpace().current();
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "build_schematic",
                        Map.of("file", "fixture_plate", "x", anchor.getX(), "y", anchor.getY(), "z", anchor.getZ())),
                SETTLE_TICKS);
        int[] crowded = {0, 0};
        helper.onEachTick(() -> {
            crowded[0] = spirit.distanceTo(player) < rules.minDistance() ? crowded[0] + 1 : 0;
            crowded[1] = Math.max(crowded[1], crowded[0]);
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Шаг в сторону не прерывает стройку: плита обязана достроиться; статус " + spirit.taskStatus()
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    crowded[1] <= MAX_CROWDED_TICKS,
                    "Дух не стоит вплотную к игроку дольше " + MAX_CROWDED_TICKS + " тиков: обязан отойти и строить "
                            + "дальше; вплотную подряд " + crowded[1] + " тиков");
            GameTestFixtures.finish(helper);
        });
    }
}
