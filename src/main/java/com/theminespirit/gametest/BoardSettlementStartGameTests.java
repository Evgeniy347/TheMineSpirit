package com.theminespirit.gametest;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.FirstStepsPlan;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.board.TorchRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.FirstStepsRunner;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import com.theminespirit.neoforge.board.TorchWatch;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Развитие поселения с нуля через доску: порядок первых шагов в настоящем мире, провал шага и путь «уголь → факел».
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardSettlementStartGameTests {
    private static final String LIVE_BATCH = "board_settlement_start";
    private static final long DAY_TIME = 6000;
    private static final int TREE_HEIGHT = 3;
    private static final int REBUILD_EVERY = 20;
    private static final BlockPos HOME = new BlockPos(4, 1, 4);
    private static final List<BoardCase> WOODEN_START = List.of(
            BoardCase.CRAFTING_STATION,
            BoardCase.CRAFT_WOODEN_PICKAXE,
            BoardCase.CRAFT_WOODEN_AXE,
            BoardCase.CRAFT_WOODEN_SHOVEL);

    private BoardSettlementStartGameTests() {}

    @BeforeBatch(batch = LIVE_BATCH)
    public static void mobsOff(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = LIVE_BATCH)
    public static void mobsOn(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    // [флак] два духа рубят три дерева и крафтят верстак и два деревянных набора через доску; запас ~2× к замеру
    @GameTest(template = "arena", batch = LIVE_BATCH, timeoutTicks = 3000)
    public static void settlement_EmptyTwoSpirits_TableThenWoodenToolsThenMine(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        GameTestFixtures.holdDayTime(helper, DAY_TIME);
        FullCycleScene.buildOakTree(helper, new BlockPos(1, 1, 7), TREE_HEIGHT);
        FullCycleScene.buildOakTree(helper, new BlockPos(7, 1, 1), TREE_HEIGHT);
        FullCycleScene.buildOakTree(helper, new BlockPos(7, 1, 7), TREE_HEIGHT);
        ServerLevel level = helper.getLevel();
        List<SpiritEntity> spirits =
                List.of(spirit(helper, new BlockPos(3, 1, 2)), spirit(helper, new BlockPos(5, 1, 2)));
        BoardService board = isolatedBoard(level, "gametest-settlement-start.jsonl");
        List<BoardCase> doneOrder = new ArrayList<>();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (ticks[0] % REBUILD_EVERY == 0) {
                board.rebuild(level, level.getGameTime());
            }
            for (BoardCase step : FirstStepsPlan.chain()) {
                if (!doneOrder.contains(step) && board.board().ledger().has(step, 1)) {
                    doneOrder.add(step);
                }
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    doneOrder.containsAll(WOODEN_START),
                    "Пустое поселение обязано пройти верстак и деревянный набор через доску; сделано " + doneOrder
                            + ", доска: " + firstSteps(board) + ", духи: " + spiritsState(spirits));
            int mine = doneOrder.indexOf(BoardCase.BUILD_MINE);
            helper.assertTrue(
                    doneOrder.get(0) == BoardCase.CRAFTING_STATION
                            && (mine < 0 || doneOrder.indexOf(BoardCase.CRAFT_WOODEN_PICKAXE) < mine)
                            && stoneAfterMine(doneOrder, mine),
                    "Порядок с нуля — верстак → деревянные инструменты → шахта → каменные (решение владельца 21.10); "
                            + "упавший шаг пропускается и делается позже: " + doneOrder);
            helper.assertTrue(
                    board.board().statusOf("first:build_mine").isPresent(),
                    "После деревянного набора на доске — шахта (вход и спуск), а не постройки: " + firstSteps(board));
            helper.assertTrue(
                    board.board().statusOf("first:clear_grounds").isEmpty()
                            && board.board().statusOf("first:stock_chests").isEmpty(),
                    "Расчистка и сундуки не ставятся до инструментов и шахты: " + firstSteps(board));
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "board_settlement_blocked", timeoutTicks = 100)
    public static void firstSteps_StepBlocked_NextStepPosted(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spirit(helper, new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        AtomicLong clock = new AtomicLong(level.getGameTime());
        BoardService board = isolatedBoard(clock, "gametest-settlement-blocked.jsonl");
        String station = "first:crafting_station";
        for (int attempt = 0;
                attempt < BoardTuning.ticketFailLimit() && !board.board().blocked(station);
                attempt++) {
            board.board().prune(clock.get());
            board.board()
                    .submit(new BoardTicket(
                            station,
                            BoardCase.CRAFTING_STATION,
                            Optional.empty(),
                            40,
                            clock.get(),
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()));
            board.board().claim(station, UUID.nameUUIDFromBytes(("attempt-" + attempt).getBytes(UTF_8)), clock.get());
            board.board().fail(station);
            clock.addAndGet(BoardTuning.caseFailPauseCapTicks() + BoardTuning.failBackoffTicks() + 1);
        }
        helper.assertTrue(
                board.board().blocked(station),
                "Подготовка: верстак обязан упасть до BLOCKED, статус "
                        + board.board().statusOf(station));

        FirstStepsRunner.tick(level, board, List.of(spirit), stocked(), clock.get());

        helper.assertTrue(
                board.board().statusOf("first:craft_wooden_pickaxe").orElse(null) == BoardStatus.PENDING,
                "Заблокированный шаг не держит цепочку: дух получает следующее задание (правило владельца), статус "
                        + board.board().statusOf("first:craft_wooden_pickaxe"));
        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "board_settlement_torches", timeoutTicks = 100)
    public static void torches_FirstCoal_CraftTorchesHighPriority(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spirit(helper, new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-settlement-torches.jsonl");

        TorchWatch.tick(
                board,
                List.of(spirit),
                home(helper),
                new StockView(Map.of("coal", 1, "stick", 4)),
                false,
                false,
                level.getGameTime());

        String id = TorchWatch.ticketId(BoardCase.CRAFT_TORCHES);
        helper.assertTrue(
                board.board().statusOf(id).isPresent(),
                "Первый уголь — сразу задание «сделать факелы», статус "
                        + board.board().statusOf(id));
        int priority = board.board().find(id).orElseThrow().priority();
        helper.assertTrue(
                priority > BoardRules.priorityOf(BoardCase.BUILD_HOUSE),
                "Факелы важнее построек: приоритет " + priority);
        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "board_settlement_charcoal", timeoutTicks = 100)
    public static void torches_NoCoalFurnacePlaced_SmeltCharcoal(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spirit(helper, new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-settlement-charcoal.jsonl");
        helper.setBlock(new BlockPos(6, 1, 3), Blocks.FURNACE);
        Map<String, Integer> stock = new HashMap<>(Map.of("oak_log", 8, "oak_planks", 8, "stick", 8));
        stock.put("crafting_table", 1);

        FirstStepsRunner.tick(level, board, List.of(spirit), new StockView(stock), level.getGameTime());

        String id = TorchWatch.ticketId(BoardCase.SMELT_CHARCOAL);
        helper.assertTrue(
                board.board().statusOf(id).isPresent(),
                "Печь стоит, угля нет — пережечь пару брёвен в древесный уголь на факелы, статус "
                        + board.board().statusOf(id));
        helper.assertTrue(
                board.board().find(id).orElseThrow().requiredItems().getOrDefault("oak_log", 0)
                        == TorchRules.CHARCOAL_LOGS,
                "Гейт угля — брёвна на уголь и топливо: "
                        + board.board().find(id).orElseThrow().requiredItems());
        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static boolean stoneAfterMine(List<BoardCase> doneOrder, int mine) {
        for (BoardCase stone : ToolSet.stone()) {
            int at = doneOrder.indexOf(stone);
            if (at >= 0 && (mine < 0 || at < mine)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> firstSteps(BoardService board) {
        Map<String, String> steps = new HashMap<>();
        for (BoardTicket ticket : board.board().all()) {
            steps.put(
                    ticket.id(),
                    String.valueOf(board.board().statusOf(ticket.id()).orElse(null)));
        }
        return steps;
    }

    private static String spiritsState(List<SpiritEntity> spirits) {
        StringBuilder state = new StringBuilder();
        for (SpiritEntity spirit : spirits) {
            state.append(spirit.displayName())
                    .append(' ')
                    .append(spirit.taskStatus().orElse(null))
                    .append(' ')
                    .append(spirit.currentTaskScript().orElse("-"))
                    .append(' ')
                    .append(spirit.taskFailureReason().orElse(""))
                    .append(" inv=");
            for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
                if (!spirit.getInventory().getItem(slot).isEmpty()) {
                    state.append(spirit.getInventory().getItem(slot)).append(',');
                }
            }
            state.append("; ");
        }
        return state.toString();
    }

    private static StockView stocked() {
        return new StockView(Map.of("oak_planks", 64, "stick", 64, "oak_log", 16));
    }

    private static SpiritEntity spirit(GameTestHelper helper, BlockPos at) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), at);
        spirit.setHome(home(helper));
        return spirit;
    }

    private static BlockCoord home(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(HOME);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BoardService isolatedBoard(ServerLevel level, String journalFile) {
        return service(new Board(level::getGameTime), journalFile);
    }

    private static BoardService isolatedBoard(AtomicLong clock, String journalFile) {
        return service(new Board(clock::get), journalFile);
    }

    private static BoardService service(Board board, String journalFile) {
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
