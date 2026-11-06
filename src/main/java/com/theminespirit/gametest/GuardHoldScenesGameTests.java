package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.SpiritCoordinator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardHoldScenesGameTests {
    private GuardHoldScenesGameTests() {}

    @GameTest(template = "arena", batch = "defense_guard_hold", timeoutTicks = 200)
    public static void defense_WorkingSpirit_KeepsGuardModeWithoutFlapping(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        BlockPos homeAt = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockCoord home = new BlockCoord(homeAt.getX(), homeAt.getY(), homeAt.getZ());
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(4, 1, 4));
        spirit.setHome(home);
        ServerLevel level = helper.getLevel();
        SettlementKey key = SettlementGroups.keyOf(spirit.home(), spirit.getUUID());
        SpiritCoordinator coordinator = new SpiritCoordinator(level, key);

        coordinator.tick(List.of(spirit), level.getGameTime());
        helper.assertTrue(
                spirit.tactics().combatMode() == CombatMode.GUARD_AREA,
                "Свободному духу с домом координатор обязан выдать пост GUARD_AREA: режим "
                        + spirit.tactics().combatMode());

        spirit.startTask(new SequenceNode(List.of(new ScriptCall("wait", Map.of("ticks", 200)))));
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                "Задача не запущена: проверять занятость не на чем");
        coordinator.tick(List.of(spirit), level.getGameTime());

        helper.assertTrue(
                spirit.tactics().combatMode() == CombatMode.GUARD_AREA,
                "Старт работы не снимает приказ обороны: иначе режим мигает GUARD_AREA ↔ DEFENSIVE каждые "
                        + "5–18 с при 0 врагов, режим " + spirit.tactics().combatMode());
        helper.assertTrue(
                spirit.tactics().directiveHeld(),
                "Приказ обороны обязан удерживаться (удержание), пока дух занят работой");
        helper.assertTrue(
                spirit.tactics()
                        .activeDirective()
                        .map(directive -> directive.mode() == CombatMode.GUARD_AREA)
                        .orElse(false),
                "Удерживаемый приказ остаётся приказом GUARD_AREA, а не исчезает");
        helper.assertTrue(
                !spirit.tactics().wantsControl(),
                "Удерживаемый приказ не ведёт духа к якорю и не отбирает тик у задачи");

        spirit.cancelTask();
        coordinator.tick(List.of(spirit), level.getGameTime());
        helper.assertTrue(
                spirit.tactics().combatMode() == CombatMode.GUARD_AREA,
                "После работы режим остаётся GUARD_AREA, без ложного DEFENSIVE: "
                        + spirit.tactics().combatMode());

        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }
}
