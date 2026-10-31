package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Observe9ScenesGameTests {
    private static final int MEASURE_TICKS = 900;

    private Observe9ScenesGameTests() {}

    // [флак] шаг наблюдения 9 ждёт 900 тиков тактики, а не мгновенного события: бюджет двойной.
    @SettlementSystems(coordinator = true)
    @GameTest(template = "arena", batch = "scene_observe9_arena", timeoutTicks = 1400)
    public static void observe9Scene_ActivityCardPaused_ModeDoesNotFlap(GameTestHelper helper) {
        GameTestFilter.setMobsTargetSpirits(false);
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        BlockPos at = helper.absolutePos(new BlockPos(5, 1, 5));
        spirit.setHome(new BlockCoord(at.getX(), at.getY(), at.getZ()));
        BoardService board = BoardRegistry.get(helper.getLevel());
        for (BoardTicket ticket : board.board().all()) {
            board.board().cancel(ticket.id());
        }
        long start = helper.getTick();
        List<String> orderChanges = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<CombatMode> lastMode =
                new AtomicReference<>(spirit.tactics().combatMode());
        helper.onEachTick(() -> {
            board.board()
                    .occupy(
                            spirit.getUUID(),
                            BoardCase.ACTIVITY_SMELT,
                            "smelt",
                            "-171,71,-489",
                            helper.getLevel().getGameTime());
            CombatMode mode = spirit.tactics().combatMode();
            if (mode != lastMode.getAndSet(mode)) {
                orderChanges.add((helper.getTick() - start) + ":" + mode);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    helper.getTick() - start >= MEASURE_TICKS,
                    "Наблюдение за тактикой ещё идёт: " + (helper.getTick() - start) + " из " + MEASURE_TICKS
                            + " тиков");
            List<String> flips = spirit.tactics().decisions(32).stream()
                    .filter(record -> "mode".equals(record.changed()))
                    .map(DecisionRecord::toString)
                    .toList();
            helper.assertTrue(
                    flips.size() <= 1,
                    "Дух с занятием доски (карточка ACTIVITY_SMELT) и без врагов не должен переключать режим: "
                            + "за " + MEASURE_TICKS + " тиков записей режима " + flips.size() + " — " + flips
                            + "; приказ " + spirit.tactics().activeDirective()
                            + "; ordered " + orderChanges
                            + "; скрипт " + spirit.currentTaskScript() + "/" + spirit.taskStatus());
            board.board().vacate(spirit.getUUID());
            TacticsScene.finish(helper, spirit);
        });
    }
}
