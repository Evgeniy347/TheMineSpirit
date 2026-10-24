package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Сброс заданий при смене сборки (INC-40): снятое задание доски отменяет и задачу духа, запущенную по нему.
 * На живом сервере v0.1.2053 spirit-5 50+ мин копал dig_area бывшего mine_level_3 и числился внешним занятием,
 * а first:stock_chests некому было выдать.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardResetGameTests {
    private static final String BATCH = "board_reset_build";
    private static final String TICKET = "gather:inc40_reset";
    private static final int LONG_WAIT_TICKS = 4000;

    private BoardResetGameTests() {}

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 100)
    public static void reset_NewBuild_CancelsTaskOfRemovedTicket(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 2));
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        ServerLevel level = helper.getLevel();
        BoardService service = BoardRegistry.get(level);
        long now = level.getGameTime();
        BoardOutcome submitted = service.board()
                .submit(new BoardTicket(
                        TICKET,
                        BoardCase.GATHER_MATERIALS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()));
        helper.assertTrue(submitted == BoardOutcome.ACCEPTED, "Задание сцены не принято доской: " + submitted);
        BoardOutcome claimed = service.board().claim(TICKET, spirit.getUUID(), now);
        helper.assertTrue(claimed == BoardOutcome.CLAIMED, "Задание сцены не выдано духу: " + claimed);
        // Задача по заданию — как восстановленная из сохранения после выкладки: долгая и без связи с новой доской.
        spirit.startTask(new ScriptCall("wait", Map.of("ticks", LONG_WAIT_TICKS)));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                    "Задача сцены не запущена: проверять отмену нечего");
            long generation = spirit.taskGeneration();
            List<String> reset = service.resetForNewRules();
            helper.assertTrue(
                    reset.contains(TICKET), "Сброс при смене сборки обязан снять выданное задание источника: " + reset);
            service.rebuild(level, level.getGameTime());
            // Освобождённый дух в той же пересборке может получить новую работу (занятие) — старая задача снята,
            // если сменилось поколение задачи или задачи нет.
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) != RunStatus.RUNNING || spirit.taskGeneration() != generation,
                    "Задание " + TICKET + " снято сбросом, а его задача у духа идёт дальше ("
                            + spirit.currentTaskScript().orElse("-")
                            + "): дух числится внешним занятием и не берёт задания доски (INC-40)");
            helper.succeed();
        });
    }
}
