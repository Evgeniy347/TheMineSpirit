package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.FirstStepsRunner;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Первые шаги на доске: новое поселение → CLEAR_GROUNDS; дальше задания идут строго по цепочке,
 * каждое следующее — после выполнения предыдущего; выполненная цепочка новых заданий не плодит.
 * Всё синхронно: генератор — чистая функция от фактов мира, тело теста атомарно.
 * Каждый сценарий — своя пачка и своя доска: генератор обходит мир радиусом 16, а структуры
 * GameTest стоят в 16 блоках друг от друга — в общей пачке сосед ставит сундук или верстак,
 * и факты чужой сцены подменяют шаг цепочки. Общая доска уровня вдобавок приносит чужие
 * билеты (mine_level_*), которые держат шаг первыми шагами.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardFirstStepsGameTests {
    private static final String BATCH = "board_first_steps";
    private static final String STOCK_BATCH = "board_first_steps_stock";
    private static final String TOOLS_BATCH = "board_first_steps_tools";
    private static final String SMITHY_BATCH = "board_first_steps_smithy";
    private static final String SMITHY_IRON_BATCH = "board_first_steps_smithy_iron";
    private static final String RESTART_BATCH = "board_first_steps_restart";
    // Геометрия обзора FirstStepsRunner.buildFacts: радиус и высоты обязаны совпадать,
    // иначе диагностика «чужие деревья» увидит не то, что видит генератор.
    private static final int SCAN_RADIUS = 16;
    private static final int FIRST_TOOL_INDEX = 3;

    private BoardFirstStepsGameTests() {}

    @BeforeBatch(batch = BATCH)
    public static void stopMobsTargetingSpirits(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = BATCH)
    public static void restoreMobTargeting(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    @BeforeBatch(batch = STOCK_BATCH)
    public static void stopMobsTargetingSpiritsForStock(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = STOCK_BATCH)
    public static void restoreMobTargetingAfterStock(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    @BeforeBatch(batch = TOOLS_BATCH)
    public static void stopMobsTargetingSpiritsForTools(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = TOOLS_BATCH)
    public static void restoreMobTargetingAfterTools(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    @BeforeBatch(batch = SMITHY_BATCH)
    public static void stopMobsTargetingSpiritsForSmithy(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = SMITHY_BATCH)
    public static void restoreMobTargetingAfterSmithy(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    @BeforeBatch(batch = SMITHY_IRON_BATCH)
    public static void stopMobsTargetingSpiritsForSmithyIron(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = SMITHY_IRON_BATCH)
    public static void restoreMobTargetingAfterSmithyIron(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 100)
    public static void firstSteps_NewSettlement_ChainInOrderWithoutDuplicates(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-first-steps-chain.jsonl");
        placeLogs(helper, false);
        int prevIndex = -1;
        BoardCase lastCase = null;
        int streak = 0;
        boolean foreignTrees = false;

        for (int step = 0; step < 40 && !foreignTrees; step++) {
            long now = level.getGameTime();
            FirstStepsRunner.tick(level, board, List.of(spirit), stockOf(board), now);
            List<BoardTicket> active = board.board().all().stream()
                    .filter(ticket -> ticket.id().startsWith("first:"))
                    .filter(ticket -> {
                        BoardStatus status = board.board().statusOf(ticket.id()).orElse(null);
                        return status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED;
                    })
                    .toList();
            helper.assertTrue(
                    active.size() <= 1,
                    "Задание первых шагов всегда одно: активных " + active.size()
                            + ": иначе поселение строится вразнобой");
            if (active.isEmpty()) {
                if (lastCase != null
                        && board.board()
                                        .statusOf("first:" + lastCase.name().toLowerCase(Locale.ROOT))
                                        .orElse(null)
                                == BoardStatus.DONE) {
                    // Тень DONE мешает переизданию того же шага: снимаем напрямую.
                    board.board().prune(board.board().now());
                    continue;
                }
                break;
            }
            BoardTicket head = active.get(0);
            if (head.boardCase() == lastCase) {
                streak++;
                // Факты не продвинулись: переустанавливаем маркеры идемпотентно и пробуем ещё.
                satisfy(helper, board, spirit, head.boardCase());
                board.board().prune(board.board().now());
                if (streak >= 2) {
                    if (head.boardCase() == BoardCase.CLEAR_GROUNDS && foreignTreesPresent(helper)) {
                        // Чужие деревья в обзоре держат землю не зачищенной вечно:
                        // генератор прав, дальше цепочке идти некуда — выходим через
                        // зафиксированный факт, а не через пропуск шагов.
                        foreignTrees = true;
                        break;
                    }
                    helper.assertTrue(
                            false,
                            "Шаг " + head.boardCase() + " повторяется в третий раз, маркеры целы: "
                                    + "генератор топчется на месте");
                }
                continue;
            }
            streak = 0;
            int index = chainIndex(head.boardCase());
            helper.assertTrue(
                    index > prevIndex,
                    "Следующий шаг " + head.boardCase() + " идёт после предыдущего (индекс " + prevIndex
                            + "): цепочка не идёт назад и не топчется на месте");
            satisfy(helper, board, spirit, head.boardCase());
            closeTicket(board, head.id(), spirit);
            prevIndex = index;
            lastCase = head.boardCase();
        }
        if (!foreignTrees) {
            helper.assertTrue(
                    prevIndex >= chainIndex(BoardCase.BUILD_MINE),
                    "Цепочка сошлась не раньше шахты: последний шаг " + prevIndex
                            + ": ранний конец означает пропуск шагов, а не выполненную цепочку");
        }
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = RESTART_BATCH, timeoutTicks = 100)
    public static void firstSteps_DoneClearGrounds_NotReopenedAfterRestart(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        ServerLevel level = helper.getLevel();
        placeLogs(helper, false);
        Path journalPath = FMLPaths.CONFIGDIR.get().resolve("theminespirit/gametest-first-steps-restart.jsonl");
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }

        BoardService first = journaledBoard(level, journalPath);
        FirstStepsRunner.tick(level, first, List.of(spirit), stocked(), level.getGameTime());
        helper.assertTrue(
                first.board().statusOf("first:clear_grounds").orElse(null) == BoardStatus.PENDING,
                "Новое поселение начинает с расчистки участка, статус "
                        + first.board().statusOf("first:clear_grounds"));
        closeTicket(first, "first:clear_grounds", spirit);
        first.journal().flushAll();

        BoardService restarted = journaledBoard(level, journalPath);
        helper.assertTrue(
                restarted.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1),
                "Журнал доски обязан вернуть отметку «расчищено» после перезапуска: иначе "
                        + "выполненный кейс откроется снова");
        FirstStepsRunner.tick(level, restarted, List.of(spirit), stocked(), level.getGameTime());

        helper.assertTrue(
                restarted.board().statusOf("first:clear_grounds").isEmpty(),
                "Выполненный кейс расчистки не переоткрывается после перезапуска, статус "
                        + restarted.board().statusOf("first:clear_grounds") + ": деревья в мире "
                        + "не должны перебивать факт done");
        helper.assertTrue(
                restarted.board().statusOf("first:stock_chests").orElse(null) == BoardStatus.PENDING,
                "После «расчищено» цепочка идёт дальше — к сундукам, статус "
                        + restarted.board().statusOf("first:stock_chests"));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = STOCK_BATCH, timeoutTicks = 100)
    public static void firstSteps_EmptyStock_GathersBeforeStations(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level);
        long now = level.getGameTime();

        FirstStepsRunner.tick(level, board, List.of(spirit), new StockView(Map.of()), now);
        helper.assertTrue(
                board.board().statusOf("first:stock_chests").isEmpty(),
                "Без досок шаг сундуков не ставится: он ждёт добычу материала");
        helper.assertTrue(
                board.board().statusOf("gather:oak_planks").isPresent(),
                "Пустой склад: на доску встаёт добыча досок (рубка дерева), статус "
                        + board.board().statusOf("gather:oak_planks"));

        FirstStepsRunner.tick(level, board, List.of(spirit), stocked(), now);
        helper.assertTrue(
                board.board().statusOf("first:stock_chests").isPresent(),
                "После добычи материал на складе — шаг сундуков открывается, статус "
                        + board.board().statusOf("first:stock_chests"));

        helper.setBlock(new BlockPos(6, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(7, 1, 2), Blocks.CHEST);
        board.board().claim("first:stock_chests", spirit.getUUID(), now);
        board.board().complete("first:stock_chests");
        FirstStepsRunner.tick(level, board, List.of(spirit), stocked(), now);
        helper.assertTrue(
                board.board().statusOf("first:crafting_station").isPresent(),
                "После сундуков наступает верстак: цепочка не перескакивает шаги, статус "
                        + board.board().statusOf("first:crafting_station"));

        board.journal().flushAll();
        List<BoardLogEntry> log = board.journal().last(64);
        helper.assertTrue(
                hasPosted(log, "gather:oak_planks"),
                "Журнал доски обязан содержать постановку добычи: по ней разбирают, кто что получил");
        helper.assertTrue(
                hasPosted(log, "first:stock_chests") && hasPosted(log, "first:crafting_station"),
                "Журнал доски обязан содержать постановки шагов — сундуки и верстак");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = TOOLS_BATCH, timeoutTicks = 100)
    public static void firstSteps_EmptyStock_WoodenToolsThenStone(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        helper.setBlock(new BlockPos(6, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(7, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 1, 6), Blocks.CRAFTING_TABLE);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-first-steps-tools.jsonl");
        Map<String, Integer> stock = new HashMap<>();

        tick(level, board, spirit, stock);
        helper.assertTrue(
                board.board().statusOf("first:craft_wooden_pickaxe").isEmpty(),
                "Пустой склад: деревянная кирка ждёт досок, а не выдаётся вхолостую");
        helper.assertTrue(
                board.board().statusOf("gather:oak_planks").isPresent(),
                "Пустой склад: на доску встаёт добыча досок под деревянный набор, статус "
                        + board.board().statusOf("gather:oak_planks"));

        stock.put("oak_planks", 64);
        stock.put("stick", 64);
        tick(level, board, spirit, stock);
        helper.assertTrue(
                board.board().statusOf("first:craft_wooden_pickaxe").isPresent(),
                "С досками и палками открывается деревянная кирка, статус "
                        + board.board().statusOf("first:craft_wooden_pickaxe"));

        craftTool(board, spirit, stock, "first:craft_wooden_pickaxe", "wooden_pickaxe");
        tick(level, board, spirit, stock);
        helper.assertTrue(
                board.board().statusOf("first:craft_wooden_axe").isPresent(),
                "После деревянной кирки наступает деревянный топор: ступень идёт по одному предмету");
        craftTool(board, spirit, stock, "first:craft_wooden_axe", "wooden_axe");
        tick(level, board, spirit, stock);
        helper.assertTrue(
                board.board().statusOf("first:craft_wooden_shovel").isPresent(),
                "После топора — деревянная лопата: деревянный набор закрывается перед каменным");
        craftTool(board, spirit, stock, "first:craft_wooden_shovel", "wooden_shovel");

        tick(level, board, spirit, stock);
        helper.assertTrue(
                board.board().statusOf("gather:cobblestone").isPresent(),
                "Каменная ступень ждёт булыжник: при недостаче встаёт добыча булыжника, статус "
                        + board.board().statusOf("gather:cobblestone"));
        helper.assertTrue(
                board.board().statusOf("first:craft_stone_pickaxe").isEmpty(),
                "Без булыжника каменная кирка не открывается: гейт из рецепта держит задание");

        stock.put("cobblestone", 64);
        tick(level, board, spirit, stock);
        helper.assertTrue(
                board.board().statusOf("first:craft_stone_pickaxe").isPresent(),
                "С булыжником открывается каменная кирка — вторая ступень набора, статус "
                        + board.board().statusOf("first:craft_stone_pickaxe"));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = SMITHY_BATCH, timeoutTicks = 100)
    public static void firstSteps_WithIron_CraftsAnvil(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = smithySettlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-first-steps-smithy.jsonl");

        FirstStepsRunner.tick(level, board, List.of(spirit), new StockView(smithyStock(true)), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf("first:craft_anvil").isPresent(),
                "С железом (3 блока + 4 слитка из рецепта) наковальня открывается: статус "
                        + board.board().statusOf("first:craft_anvil"));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = SMITHY_IRON_BATCH, timeoutTicks = 100)
    public static void firstSteps_WithoutIron_WaitsWithGather(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = smithySettlement(helper);
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-first-steps-smithy-iron.jsonl");

        FirstStepsRunner.tick(level, board, List.of(spirit), new StockView(smithyStock(false)), level.getGameTime());

        helper.assertTrue(
                board.board().statusOf("first:craft_anvil").isEmpty(),
                "Без железа крафт наковальни не открывается: гейт из рецепта держит задание, статус "
                        + board.board().statusOf("first:craft_anvil"));
        boolean gather = board.board().statusOf("gather:iron_ingot").isPresent()
                || board.board().statusOf("gather:iron_block").isPresent();
        helper.assertTrue(
                gather,
                "При недостаче железа на доску встаёт добыча слитка или блока: статусы "
                        + board.board().statusOf("gather:iron_ingot") + " и "
                        + board.board().statusOf("gather:iron_block"));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static SpiritEntity smithySettlement(GameTestHelper helper) {
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        placeLogs(helper, true);
        helper.setBlock(new BlockPos(6, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(7, 1, 2), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 1, 6), Blocks.CRAFTING_TABLE);
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(6, 1, 5), Blocks.LADDER);
        helper.setBlock(new BlockPos(3, 1, 7), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(1, 1, 7), Blocks.TORCH);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.TORCH);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.TORCH);
        helper.setBlock(new BlockPos(7, 1, 3), Blocks.TORCH);
        helper.setBlock(new BlockPos(4, 1, 7), Blocks.OAK_STAIRS);
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.SMITHING_TABLE);
        return spirit;
    }

    private static Map<String, Integer> smithyStock(boolean iron) {
        Map<String, Integer> stock = new HashMap<>();
        for (BoardCase tool : ToolSet.chain()) {
            stock.merge(ToolSet.stockKey(tool), 1, Integer::sum);
        }
        stock.put("oak_planks", 256);
        stock.put("stick", 256);
        stock.put("oak_fence", 256);
        stock.put("torch", 256);
        stock.put("cobblestone", 256);
        if (iron) {
            stock.put("iron_ingot", 256);
            stock.put("iron_block", 256);
        }
        return stock;
    }

    private static void tick(ServerLevel level, BoardService board, SpiritEntity spirit, Map<String, Integer> stock) {
        FirstStepsRunner.tick(level, board, List.of(spirit), new StockView(Map.copyOf(stock)), level.getGameTime());
    }

    private static void craftTool(
            BoardService board, SpiritEntity spirit, Map<String, Integer> stock, String ticketId, String item) {
        board.board().claim(ticketId, spirit.getUUID(), board.board().now());
        board.board().complete(ticketId);
        stock.merge(item, 1, Integer::sum);
    }

    private static boolean hasPosted(List<BoardLogEntry> log, String ticketId) {
        return log.stream()
                .anyMatch(entry -> entry.event() == BoardLog.Event.POSTED
                        && entry.ticketId().equals(ticketId));
    }

    private static StockView stocked() {
        return new StockView(Map.of("oak_planks", 64, "stick", 64, "oak_fence", 64, "torch", 64, "cobblestone", 64));
    }

    private static BoardService isolatedBoard(ServerLevel level) {
        return isolatedBoard(level, "gametest-first-steps-gate.jsonl");
    }

    private static BoardService isolatedBoard(ServerLevel level, String journalFile) {
        Board board = new Board(level::getGameTime);
        Path journalPath = FMLPaths.CONFIGDIR.get().resolve("theminespirit/" + journalFile);
        try {
            Files.deleteIfExists(journalPath);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        JournalService journal = new JournalService(journalPath);
        return new BoardService(
                board,
                new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(),
                journal);
    }

    private static BoardService journaledBoard(ServerLevel level, Path journalPath) {
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

    private static StockView stockOf(BoardService board) {
        Map<String, Integer> items = new HashMap<>(board.stock().available());
        items.putIfAbsent("oak_planks", 256);
        items.putIfAbsent("stick", 256);
        items.putIfAbsent("oak_fence", 256);
        items.putIfAbsent("torch", 256);
        items.putIfAbsent("cobblestone", 256);
        items.putIfAbsent("iron_ingot", 256);
        items.putIfAbsent("iron_block", 256);
        return new StockView(items);
    }

    @GameTest(template = "arena", batch = "board_first_steps_nohome", timeoutTicks = 100)
    public static void firstSteps_SpiritWithoutHome_NoTickets(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-first-steps-nohome.jsonl");

        FirstStepsRunner.tick(level, board, List.of(spirit), stocked(), level.getGameTime());

        List<BoardTicket> active = board.board().all().stream()
                .filter(ticket -> ticket.id().startsWith("first:"))
                .filter(ticket -> {
                    BoardStatus status = board.board().statusOf(ticket.id()).orElse(null);
                    return status == BoardStatus.PENDING
                            || status == BoardStatus.WAITING_RESOURCES
                            || status == BoardStatus.ASSIGNED;
                })
                .toList();
        helper.assertTrue(
                active.isEmpty(),
                "Дух без дома не начинает первых шагов: цепочку задаёт дом, а не выдуманная точка. "
                        + "Активных заданий: "
                        + active.stream().map(BoardTicket::id).toList());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static boolean foreignTreesPresent(GameTestHelper helper) {
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        ServerLevel level = helper.getLevel();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                if (dx * dx + dz * dz > SCAN_RADIUS * SCAN_RADIUS) {
                    continue;
                }
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos check = home.offset(dx, dy, dz);
                    if (!level.isLoaded(check)) {
                        continue;
                    }
                    var state = level.getBlockState(check);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int chainIndex(BoardCase boardCase) {
        int tool = ToolSet.chain().indexOf(boardCase);
        if (tool >= 0) {
            return FIRST_TOOL_INDEX + tool;
        }
        return switch (boardCase) {
            case CLEAR_GROUNDS -> 0;
            case STOCK_CHESTS -> 1;
            case CRAFTING_STATION -> 2;
            case BUILD_MINE -> 10;
            case BUILD_FENCE -> 11;
            case LIGHTING -> 12;
            case BUILD_HOUSE -> 13;
            case CRAFT_SMITHING_TABLE -> 14;
            case CRAFT_ANVIL -> 15;
            default -> throw new IllegalArgumentException("Не шаг цепочки: " + boardCase);
        };
    }

    private static void satisfy(GameTestHelper helper, BoardService board, SpiritEntity spirit, BoardCase head) {
        if (ToolSet.isTool(head)) {
            int slot = ToolSet.chain().indexOf(head);
            spirit.getInventory().setItem(slot, toolStack(head));
            ServerLevel level = helper.getLevel();
            board.rebuild(level, level.getGameTime());
            return;
        }
        switch (head) {
            case CLEAR_GROUNDS -> placeLogs(helper, true);
            case STOCK_CHESTS -> {
                helper.setBlock(new BlockPos(6, 1, 2), Blocks.CHEST);
                helper.setBlock(new BlockPos(7, 1, 2), Blocks.CHEST);
            }
            case CRAFTING_STATION -> helper.setBlock(new BlockPos(2, 1, 6), Blocks.CRAFTING_TABLE);
            case BUILD_MINE -> {
                helper.setBlock(new BlockPos(6, 1, 6), Blocks.COBBLESTONE);
                helper.setBlock(new BlockPos(6, 2, 6), Blocks.COBBLESTONE);
                helper.setBlock(new BlockPos(6, 1, 5), Blocks.LADDER);
            }
            case BUILD_FENCE -> helper.setBlock(new BlockPos(3, 1, 7), Blocks.OAK_FENCE);
            case LIGHTING -> {
                helper.setBlock(new BlockPos(1, 1, 7), Blocks.TORCH);
                helper.setBlock(new BlockPos(7, 1, 7), Blocks.TORCH);
                helper.setBlock(new BlockPos(1, 1, 3), Blocks.TORCH);
                helper.setBlock(new BlockPos(7, 1, 3), Blocks.TORCH);
            }
            case BUILD_HOUSE -> helper.setBlock(new BlockPos(4, 1, 7), Blocks.OAK_STAIRS);
            case CRAFT_SMITHING_TABLE -> helper.setBlock(new BlockPos(5, 1, 2), Blocks.SMITHING_TABLE);
            case CRAFT_ANVIL -> helper.setBlock(new BlockPos(5, 1, 4), Blocks.ANVIL);
            default -> throw new IllegalArgumentException("Не шаг цепочки: " + head);
        }
    }

    private static ItemStack toolStack(BoardCase boardCase) {
        return switch (boardCase) {
            case CRAFT_WOODEN_PICKAXE -> new ItemStack(Items.WOODEN_PICKAXE, 1);
            case CRAFT_WOODEN_AXE -> new ItemStack(Items.WOODEN_AXE, 1);
            case CRAFT_WOODEN_SHOVEL -> new ItemStack(Items.WOODEN_SHOVEL, 1);
            case CRAFT_STONE_PICKAXE -> new ItemStack(Items.STONE_PICKAXE, 1);
            case CRAFT_STONE_AXE -> new ItemStack(Items.STONE_AXE, 1);
            case CRAFT_STONE_SHOVEL -> new ItemStack(Items.STONE_SHOVEL, 1);
            case CRAFT_STONE_SWORD -> new ItemStack(Items.STONE_SWORD, 1);
            default -> throw new IllegalArgumentException("Не инструмент набора: " + boardCase);
        };
    }

    private static void placeLogs(GameTestHelper helper, boolean clear) {
        BlockPos[] trees = {new BlockPos(2, 1, 2), new BlockPos(3, 1, 2), new BlockPos(2, 1, 3)};
        for (BlockPos tree : trees) {
            helper.setBlock(tree, clear ? Blocks.AIR : Blocks.OAK_LOG);
        }
    }

    private static void closeTicket(BoardService board, String id, SpiritEntity spirit) {
        long now = board.board().now();
        if (board.board().statusOf(id).orElse(null) == BoardStatus.PENDING) {
            board.board().claim(id, spirit.getUUID(), now);
        }
        if (board.board().statusOf(id).orElse(null) == BoardStatus.ASSIGNED) {
            board.board().complete(id);
        } else if (board.board().statusOf(id).orElse(null) == BoardStatus.WAITING_RESOURCES) {
            board.board().cancel(id);
        }
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
