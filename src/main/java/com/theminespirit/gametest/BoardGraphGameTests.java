package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.FirstStepsRunner;
import com.theminespirit.neoforge.board.GraphSource;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardGraphGameTests {
    private static final Map<String, Integer> WOOD = Map.of("oak_planks", 64, "stick", 64, "oak_log", 16);
    private static final String TABLE = "first:crafting_station";
    private static final int TREE_HEIGHT = 3;

    private BoardGraphGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void graph_EmptyStockNoTools_ChopPostedTableWaits(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-graph-empty.jsonl");

        Set<String> live = new GraphSource()
                .tick(level, board, List.of(spirit), new StockView(Map.of()), Set.of(), level.getGameTime());

        String chop = "graph:chop_oak_log";
        helper.assertTrue(
                board.board().statusOf(chop).orElse(null) == BoardStatus.PENDING && live.contains(chop),
                "Пустой склад, нет инструментов, рядом дуб — граф ставит рубку руками (выполнима сразу), статус "
                        + board.board().statusOf(chop) + ", живые причины " + live + ", доска "
                        + board.board().all());
        helper.assertTrue(
                board.board().statusOf(TABLE).isEmpty(),
                "Без досок верстак невыполним и на доску не ставится — он давит приоритетом на доски, статус "
                        + board.board().statusOf(TABLE));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void graph_PlanksStocked_CraftingTablePosted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-graph-table.jsonl");

        new GraphSource().tick(level, board, List.of(spirit), new StockView(WOOD), Set.of(), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf(TABLE).orElse(null) == BoardStatus.PENDING
                        && board.board()
                                .find(TABLE)
                                .filter(ticket -> "graph:building:crafting_table".equals(ticket.cause()))
                                .isPresent(),
                "Доски есть — верстак ставится заданием графа с причиной графа (её держит пересборка), статус "
                        + board.board().statusOf(TABLE) + ", задание "
                        + board.board().find(TABLE));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void graph_NeighbourHasPickaxe_PickaxeCraftedForBareSpirit(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity armed = settlement(helper);
        SpiritEntity bare = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 1));
        bare.setHome(armed.home().orElseThrow());
        armed.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        helper.setBlock(new BlockPos(2, 1, 6), Blocks.CRAFTING_TABLE);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-graph-own-kit.jsonl");

        new GraphSource().tick(level, board, List.of(armed, bare), new StockView(WOOD), Set.of(), level.getGameTime());

        String barePickaxe = "graph:craft_wooden_pickaxe@" + bare.getUUID();
        helper.assertTrue(
                board.board()
                        .find(barePickaxe)
                        .flatMap(ticket -> ticket.assignee())
                        .filter(bare.getUUID()::equals)
                        .isPresent(),
                "Набор у каждого духа свой (владелец 23.10): кирка соседа не заменяет свою — крафт ставится духу "
                        + "без кирки, статус " + board.board().statusOf(barePickaxe));
        helper.assertTrue(
                board.board()
                        .statusOf("graph:craft_wooden_pickaxe@" + armed.getUUID())
                        .isEmpty(),
                "Дух с деревянной киркой вторую деревянную не получает: граф тянет только уровни выше набора");
        bare.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void graph_ItemLyingNearHome_CollectDropsPosted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-graph-drops.jsonl");
        GameTestFixtures.dropAt(helper, Items.APPLE, 1, 5.5, 1, 6.5);

        Set<String> live = new GraphSource()
                .tick(level, board, List.of(spirit), new StockView(WOOD), Set.of(), level.getGameTime());

        String collect = "graph:event:drops_nearby:collect_drops";
        helper.assertTrue(
                board.board()
                                .find(collect)
                                .filter(ticket -> ticket.boardCase() == BoardCase.COLLECT_DROPS)
                                .isPresent()
                        && live.contains(collect),
                "Предмет лежит у дома — событие drops_nearby ставит сбор выпавшего COLLECT_DROPS и держит его "
                        + "живой причиной, статус " + board.board().statusOf(collect) + ", живые причины " + live);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 100, board = true)
    public static void facts_ChestsAtWorkYard_YardCounted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = settlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-graph-yard.jsonl");
        StockView stock = new StockView(WOOD);
        FirstStepsRunner.StageScan before = FirstStepsRunner.tick(
                        level, board, List.of(spirit), stock, level.getGameTime())
                .orElseThrow(() -> new GameTestAssertException("Участок у дома духа обязан размечаться"));
        BlockCoord yard = before.stage().workYard();
        BlockPos first = new BlockPos(yard.x() + 1, yard.y(), yard.z());
        level.setBlockAndUpdate(first, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(first.east(2), Blocks.CHEST.defaultBlockState());

        int chests = FirstStepsRunner.tick(level, board, List.of(spirit), stock, level.getGameTime())
                .orElseThrow()
                .facts()
                .chests();

        level.setBlockAndUpdate(first, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(first.east(2), Blocks.AIR.defaultBlockState());
        helper.assertTrue(
                chests >= 2,
                "Сундуки двора стоят у рабочего двора (" + yard + "), а не у якоря "
                        + before.stage().anchor()
                        + ": факты поселения обязаны их видеть, иначе двор навсегда «не готов» и хижина, ограда, "
                        + "свет не ставятся; сундуков в фактах " + chests);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static SpiritEntity settlement(GameTestHelper helper) {
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int y = 1; y <= TREE_HEIGHT; y++) {
            helper.setBlock(new BlockPos(7, y, 7), Blocks.OAK_LOG);
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        return spirit;
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
