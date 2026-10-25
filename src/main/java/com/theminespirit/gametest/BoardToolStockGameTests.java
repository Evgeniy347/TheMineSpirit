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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Постоянный запас деревянного набора: сломанный инструмент доска восполняет сама. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardToolStockGameTests {
    private static final Map<String, Integer> WOOD = Map.of("oak_planks", 64, "stick", 64, "oak_log", 16);

    private BoardToolStockGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void toolStock_PickaxeBrokeAfterFirstSteps_RecraftPosted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-tool-stock-broke.jsonl");
        completeFirstStep(board, spirit, BoardCase.CRAFT_WOODEN_PICKAXE);
        String pickaxe = pickaxeOf(spirit);

        ToolStockWatch.tick(level, board, List.of(spirit), new StockView(WOOD), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf(pickaxe).orElse(null) == BoardStatus.PENDING,
                "Кирка сделана первыми шагами и пропала (сломалась): доска ставит её крафт сама, статус "
                        + board.board().statusOf(pickaxe)
                        + " — иначе first:* не переиздаётся и дух остаётся без кирки");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void toolStock_BeforeFirstStep_LeavesToFirstSteps(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-tool-stock-early.jsonl");
        String pickaxe = pickaxeOf(spirit);

        ToolStockWatch.tick(level, board, List.of(spirit), new StockView(WOOD), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf(pickaxe).isEmpty(),
                "До шага первых шагов кирку ставит цепочка, а не запас: иначе два задания на одну кирку, статус "
                        + board.board().statusOf(pickaxe));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void toolStock_NoWood_NotPosted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-tool-stock-nowood.jsonl");
        completeFirstStep(board, spirit, BoardCase.CRAFT_WOODEN_PICKAXE);
        String pickaxe = pickaxeOf(spirit);

        ToolStockWatch.tick(level, board, List.of(spirit), new StockView(Map.of()), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf(pickaxe).isEmpty(),
                "Без досок и палок задание невыполнимо и не ставится (правило владельца): статус "
                        + board.board().statusOf(pickaxe));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void toolStock_NeighbourHasPickaxe_OwnSetRecraftedForSpiritWithout(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity armed = settlement(helper);
        SpiritEntity bare = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 1));
        bare.setHome(armed.home().orElseThrow());
        armed.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-tool-stock-own.jsonl");
        completeFirstStep(board, armed, BoardCase.CRAFT_WOODEN_PICKAXE);
        Map<String, Integer> stock = new HashMap<>(WOOD);
        stock.put("wooden_pickaxe", 1);

        ToolStockWatch.tick(level, board, List.of(armed, bare), new StockView(stock), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf(pickaxeOf(bare)).orElse(null) == BoardStatus.PENDING
                        && board.board()
                                .find(pickaxeOf(bare))
                                .flatMap(ticket -> ticket.assignee())
                                .filter(bare.getUUID()::equals)
                                .isPresent(),
                "Набор у каждого духа свой (владелец 23.10): кирка соседа не заменяет свою — крафт ставится духу "
                        + "без кирки, статус " + board.board().statusOf(pickaxeOf(bare)));
        helper.assertTrue(
                board.board().statusOf(pickaxeOf(armed)).isEmpty(),
                "Дух со своей киркой новую не получает: статус " + board.board().statusOf(pickaxeOf(armed)));
        bare.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static String pickaxeOf(SpiritEntity spirit) {
        return ToolStockWatch.ticketId(BoardCase.CRAFT_WOODEN_PICKAXE, spirit.getUUID());
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
