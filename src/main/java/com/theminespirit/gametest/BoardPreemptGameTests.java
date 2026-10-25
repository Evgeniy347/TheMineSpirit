package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardPreemptGameTests {
    private static final String TICKET = "preempt:station";
    private static final int LONG_WAIT = 2000;

    private BoardPreemptGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void preempt_BackgroundWait_BoardTicketTakesSpirit(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-preempt-background.jsonl");
        spirit.startBackgroundTask(new ScriptCall("wait", Map.of("ticks", LONG_WAIT)));
        long background = spirit.taskGeneration();
        post(helper, board);

        board.rebuild(level, level.getGameTime());

        helper.assertTrue(
                board.board().holderOf(TICKET).filter(spirit.getUUID()::equals).isPresent()
                        && spirit.taskGeneration() != background,
                "Фоновое занятие интеллекта (wait) уступает заданию доски: держатель "
                        + board.board().holderOf(TICKET) + ", скрипт " + spirit.currentTaskScript()
                        + " — иначе первые шаги ждут конца рубки или отдыха");
        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void preempt_ForeignTask_NotInterrupted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-preempt-foreign.jsonl");
        spirit.startTask(new ScriptCall("wait", Map.of("ticks", LONG_WAIT)));
        long foreign = spirit.taskGeneration();
        post(helper, board);

        board.rebuild(level, level.getGameTime());

        helper.assertTrue(
                board.board().holderOf(TICKET).isEmpty() && spirit.taskGeneration() == foreign,
                "Задача REST/стратега/игрока не вытесняется доской: держатель "
                        + board.board().holderOf(TICKET) + ", скрипт " + spirit.currentTaskScript());
        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static void post(GameTestHelper helper, BoardService board) {
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        board.submit(
                new BoardTicket(
                        TICKET,
                        BoardCase.GATHER_MATERIALS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                        board.board().now(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(new BlockCoord(at.getX(), at.getY(), at.getZ())).withSupply("dirt", 1));
    }

    private static BoardService isolatedBoard(ServerLevel level, String journalFile) {
        Board board = new Board(level::getGameTime);
        Path journalPath = FMLPaths.CONFIGDIR.get().resolve("theminespirit/" + journalFile);
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return new BoardService(
                board,
                new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(),
                new JournalService(journalPath));
    }
}
