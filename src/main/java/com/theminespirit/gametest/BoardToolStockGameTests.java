package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import com.theminespirit.neoforge.board.ToolStockWatch;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Постоянный запас деревянного набора: сломанный инструмент доска восполняет сама. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardToolStockGameTests {
    private static final String PICKAXE = ToolStockWatch.ticketId(BoardCase.CRAFT_WOODEN_PICKAXE);
    private static final Map<String, Integer> WOOD = Map.of("oak_planks", 64, "stick", 64, "oak_log", 16);

    private BoardToolStockGameTests() {}

    @GameTest(template = "arena", batch = "board_tool_stock_broke", timeoutTicks = 100)
    public static void toolStock_PickaxeBrokeAfterFirstSteps_RecraftPosted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-tool-stock-broke.jsonl");
        completeFirstStep(board, spirit, BoardCase.CRAFT_WOODEN_PICKAXE);

        ToolStockWatch.tick(level, board, List.of(spirit), new StockView(WOOD), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf(PICKAXE).orElse(null) == BoardStatus.PENDING,
                "Кирка сделана первыми шагами и пропала (сломалась): доска ставит её крафт сама, статус "
                        + board.board().statusOf(PICKAXE)
                        + " — иначе first:* не переиздаётся и дух остаётся без кирки");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "board_tool_stock_stone", timeoutTicks = 100)
    public static void toolStock_StonePickaxeInStock_NoRecraft(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-tool-stock-stone.jsonl");
        completeFirstStep(board, spirit, BoardCase.CRAFT_WOODEN_PICKAXE);
        Map<String, Integer> stock = new HashMap<>(WOOD);
        stock.put("stone_pickaxe", 1);

        ToolStockWatch.tick(level, board, List.of(spirit), new StockView(stock), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf(PICKAXE).isEmpty(),
                "Каменная кирка в запасе заменяет деревянную: задание запаса не ставится, статус "
                        + board.board().statusOf(PICKAXE));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "board_tool_stock_early", timeoutTicks = 100)
    public static void toolStock_BeforeFirstStep_LeavesToFirstSteps(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-tool-stock-early.jsonl");

        ToolStockWatch.tick(level, board, List.of(spirit), new StockView(WOOD), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf(PICKAXE).isEmpty(),
                "До шага первых шагов кирку ставит цепочка, а не запас: иначе два задания на одну кирку, статус "
                        + board.board().statusOf(PICKAXE));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "board_tool_stock_nowood", timeoutTicks = 100)
    public static void toolStock_NoWood_NotPosted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-tool-stock-nowood.jsonl");
        completeFirstStep(board, spirit, BoardCase.CRAFT_WOODEN_PICKAXE);

        ToolStockWatch.tick(level, board, List.of(spirit), new StockView(Map.of()), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf(PICKAXE).isEmpty(),
                "Без досок и палок задание невыполнимо и не ставится (правило владельца): статус "
                        + board.board().statusOf(PICKAXE));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static SpiritEntity settlement(GameTestHelper helper) {
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        return spirit;
    }

    private static void completeFirstStep(BoardService board, SpiritEntity spirit, BoardCase step) {
        String id = "first:" + step.name().toLowerCase(Locale.ROOT);
        board.board()
                .submit(new BoardTicket(
                        id,
                        step,
                        Optional.empty(),
                        40,
                        board.board().now(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()));
        board.board().claim(id, spirit.getUUID(), board.board().now());
        board.board().complete(id);
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
