package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.observation.BlockCoord;
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
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Постоянные запасы доски — только на своём этапе: книги/бумага ждут шахты, полки — первого
 * уровня чаровальни. На старте поселения доска не просит книги, а повторная пересборка не
 * плодит вторую постановку того же тикета.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardSupplyGateGameTests {

    private BoardSupplyGateGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void supply_EnchantItems_WaitForStageAndPostOnce(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockPos homePos = helper.absolutePos(new BlockPos(4, 1, 4));
        spirit.setHome(new BlockCoord(homePos.getX(), homePos.getY(), homePos.getZ()));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level);
        long now = level.getGameTime();

        board.rebuild(level, now);
        helper.assertTrue(
                board.board().statusOf("supply:book").isEmpty()
                        && board.board().statusOf("supply:paper").isEmpty()
                        && board.board().statusOf("supply:bookshelf").isEmpty(),
                "На старте поселения книги, бумага и полки не нужны: доска просит их только "
                        + "на своём этапе, а не сразу, статусы "
                        + board.board().statusOf("supply:book") + ", "
                        + board.board().statusOf("supply:paper") + ", "
                        + board.board().statusOf("supply:bookshelf"));
        board.rebuild(level, now + BoardTuning.boardIntervalTicks());
        helper.assertTrue(
                board.board().statusOf("supply:book").isEmpty(),
                "Повторная пересборка не ставит supply:book, статус "
                        + board.board().statusOf("supply:book"));

        board.board().ledger().note(BoardCase.BUILD_HOUSE, 1);
        board.rebuild(level, now + BoardTuning.boardIntervalTicks() * 2);
        helper.assertTrue(
                board.board().statusOf("supply:book").orElse(null) == BoardStatus.PENDING,
                "После первого дома наступает этап чаровальни: книга обязана попасть на доску, статус "
                        + board.board().statusOf("supply:book"));
        helper.assertTrue(
                board.board().statusOf("supply:paper").orElse(null) == BoardStatus.PENDING,
                "Бумага нужна вместе с книгой: статус " + board.board().statusOf("supply:paper"));
        helper.assertTrue(
                board.board().statusOf("supply:bookshelf").isEmpty(),
                "Полки нужны только после первого уровня чаровальни, статус "
                        + board.board().statusOf("supply:bookshelf"));

        board.rebuild(level, now + BoardTuning.boardIntervalTicks() * 3);
        board.journal().flushAll();
        long posted = board.journal().last(256).stream()
                .filter(entry -> entry.event() == BoardLog.Event.POSTED)
                .filter(entry -> "supply:book".equals(entry.ticketId()))
                .count();
        helper.assertTrue(
                posted == 1,
                "Постановка supply:book повторной пересборкой не дублируется: POSTED записей " + posted
                        + ", а на один тикет должна быть одна");

        board.board().ledger().note(BoardCase.ENCHANT_ROOM, 1);
        board.rebuild(level, now + BoardTuning.boardIntervalTicks() * 4);
        helper.assertTrue(
                board.board().statusOf("supply:bookshelf").orElse(null) == BoardStatus.PENDING,
                "После чаровальни первого уровня нужны полки, статус "
                        + board.board().statusOf("supply:bookshelf"));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static BoardService isolatedBoard(ServerLevel level) {
        Path journalPath = FMLPaths.CONFIGDIR.get().resolve("theminespirit/gametest-supply-gate.jsonl");
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        JournalService journal = new JournalService(journalPath);
        Board board = new Board(
                level::getGameTime, (event, ticket, holder, tick) -> journal.record(event, ticket, holder, tick));
        return new BoardService(
                board,
                new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(),
                journal);
    }
}
