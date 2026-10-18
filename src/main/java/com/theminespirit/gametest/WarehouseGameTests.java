package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.WarehouseLayout;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.inventory.WarehouseCategories;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.SignTexts;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import com.theminespirit.neoforge.board.WarehouseWatch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Склады в мире: уровень 1 строится духом и все сундуки открываются, табличка уровня 2 несёт категорию,
 * переход 1→2 ставит задачу переноса и сноса, а автосдача кладёт руду в сундук «руда».
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarehouseGameTests {
    private static final String BATCH = "warehouse";
    private static final int SETTLE_TICKS = 5;

    private WarehouseGameTests() {
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        for (int i = 0; i < stacks.length; i++) {
            spirit.getInventory().setItem(i, stacks[i]);
        }
        return spirit;
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static ScriptCall build(GameTestHelper helper, String file, BlockPos anchor) {
        BlockPos pos = helper.absolutePos(anchor);
        return new ScriptCall("build_schematic", Map.of("file", file, "x", pos.getX(), "y", pos.getY(),
                "z", pos.getZ()));
    }

    private static void place(GameTestHelper helper, Schematic schematic, BlockPos origin, int maxX, int maxZ) {
        BlockPos base = helper.absolutePos(origin);
        for (Schematic.PlacedEntry entry : schematic.entries(
                new BlockCoord(base.getX(), base.getY(), base.getZ()))) {
            if (entry.pos().x() - base.getX() > maxX || entry.pos().z() - base.getZ() > maxZ) {
                continue;
            }
            BlockPos pos = new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
            helper.getLevel().setBlockAndUpdate(pos, BlockStates.stateFor(entry.block()));
            if (!entry.text().isBlank()) {
                SignTexts.write(helper.getLevel(), pos, entry.text());
            }
        }
    }

    // [флак] 138 блоков склада дух ставит по одному за тик с подходом к каждой клетке; запас двойной,
    // иначе тест меряет скорость стройки, а не её результат.
    @GameTest(template = "arena", batch = "warehouse_build", timeoutTicks = 12000)
    public static void warehouse_LevelOneBuilt_AllChestsOpenable(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Schematic schematic = SchematicResources.load("storage_v1");
        SpiritEntity spirit = spawnWith(helper, new BlockPos(7, 1, 4),
                new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.OAK_PLANKS, 64),
                new ItemStack(Items.OAK_LOG, 16),
                new ItemStack(Items.CHEST, 18));
        BlockPos anchor = new BlockPos(1, 0, 1);
        SpiritRun run = SpiritRun.launch(helper, spirit, build(helper, "storage_v1", anchor), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Склад уровня 1 обязан построиться целиком: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", прогресс "
                            + spirit.taskProgress().orElse(null));
            int chests = 0;
            int blocked = 0;
            List<String> missing = new ArrayList<>();
            for (Schematic.PlacedEntry entry : schematic.entries(absolute(helper, anchor))) {
                if (!entry.block().equals("minecraft:chest")) {
                    continue;
                }
                chests++;
                BlockPos pos = new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
                if (!helper.getLevel().getBlockState(pos).is(Blocks.CHEST)) {
                    missing.add(pos.toShortString() + "=" + helper.getLevel().getBlockState(pos).getBlock()
                            + "@local(" + (entry.pos().x() - helper.absolutePos(anchor).getX()) + ","
                            + (entry.pos().y() - helper.absolutePos(anchor).getY()) + ","
                            + (entry.pos().z() - helper.absolutePos(anchor).getZ()) + ")");
                }
                if (ChestBlock.isChestBlockedAt(helper.getLevel(), pos)) {
                    blocked++;
                }
            }
            helper.assertTrue(missing.isEmpty(),
                    "Сундук схемы не поставлен: " + missing + "; статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", дух в "
                            + spirit.blockPosition().toShortString() + ", сундуков в инвентаре "
                            + BlockItems.countIn(spirit.getInventory(), "minecraft:chest"));
            helper.assertTrue(chests == 18,
                    "Склад уровня 1 обязан вмещать 18 блоков сундуков (9 двойных), фактически " + chests);
            helper.assertTrue(blocked == 0,
                    "Все сундуки склада, включая верхний, обязаны открываться: заблокировано " + blocked
                            + " из " + chests);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 200)
    public static void warehouse_LevelTwoSign_StandsWithCategory(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Schematic schematic = SchematicResources.load("storage_v2");
        WarehouseLayout layout = WarehouseLayout.of(schematic);
        helper.assertTrue(!layout.signs().isEmpty(), "У склада уровня 2 обязаны быть таблички категорий");
        place(helper, schematic, new BlockPos(1, 1, 1), 5, 5);
        WarehouseCategories categories = WarehouseCategories.load();
        int placed = 0;
        int expected = 0;
        for (Schematic.Entry entry : schematic.blocks()) {
            if (!entry.block().endsWith("_sign") || entry.dx() > 5 || entry.dz() > 5) {
                continue;
            }
            expected++;
            BlockPos pos = helper.absolutePos(new BlockPos(1 + entry.dx(), 1 + entry.dy(), 1 + entry.dz()));
            BlockEntity entity = helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(entity instanceof SignBlockEntity,
                    "Табличка схемы обязана стоять в " + pos.toShortString() + ": иначе категорию не прочитать");
            String text = ((SignBlockEntity) entity).getFrontText().getMessage(0, false).getString();
            helper.assertTrue(categories.known(text),
                    "Табличка обязана нести категорию из данных, фактически '" + text + "'");
            placed++;
        }
        helper.assertTrue(expected > 0, "В проверяемом углу уровня 2 должна быть хотя бы одна табличка");
        helper.assertTrue(placed == expected,
                "Все таблички проверяемого угла уровня 2 обязаны встать: " + placed + " из " + expected);
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 200)
    @SettlementSystems(board = true)
    public static void warehouse_TransitionOneToTwo_QueuesRelocation(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 2));
        Path log = journalPath(helper);
        BoardService board = isolated(level, log);
        long now = level.getGameTime();
        complete(board, "house_1", BoardCase.BUILD_HOUSE, 1, spirit, now);

        WarehouseWatch.tick(level, board, List.of(spirit), now + 1);
        BoardTicket first = board.board().find(WarehouseRules.buildTicketId(1)).orElse(null);
        helper.assertTrue(first != null && first.boardCase() == BoardCase.BUILD_WAREHOUSE
                        && first.level() == 1,
                "После дома доска обязана заказать первый склад, фактически " + first);
        complete(board, WarehouseRules.buildTicketId(1), BoardCase.BUILD_WAREHOUSE, 1, spirit, now + 2);

        WarehouseWatch.tick(level, board, List.of(spirit), now + 3);
        BoardTicket second = board.board().find(WarehouseRules.buildTicketId(2)).orElse(null);
        helper.assertTrue(second != null && second.level() == 2,
                "После первого склада доска обязана заказать второй уровень, фактически " + second);
        complete(board, WarehouseRules.buildTicketId(2), BoardCase.BUILD_WAREHOUSE, 2, spirit, now + 4);

        WarehouseWatch.tick(level, board, List.of(spirit), now + 5);
        String relocate = WarehouseRules.relocateTicketId(1);
        BoardTicket task = board.board().find(relocate).orElse(null);
        helper.assertTrue(task != null && task.boardCase() == BoardCase.RELOCATE_BUILDING,
                "Переход 1→2 обязан поставить задачу переноса и сноса прежнего склада, фактически " + task);
        helper.assertTrue(board.board().statusOf(relocate).orElse(null) != BoardStatus.CANCELLED,
                "Задача переноса обязана остаться на доске: без неё старый склад не разберут");
        close(board, log, helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 400)
    public static void warehouse_DepositRawIron_GoesToOreChest(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 5, 3), Blocks.OAK_SIGN);
        SignTexts.write(helper.getLevel(), helper.absolutePos(new BlockPos(2, 5, 3)), "руда");
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.CHEST);
        helper.setBlock(new BlockPos(5, 5, 3), Blocks.OAK_SIGN);
        SignTexts.write(helper.getLevel(), helper.absolutePos(new BlockPos(5, 5, 3)), "еда");

        SpiritEntity spirit = spawnWith(helper, new BlockPos(5, 1, 5),
                new ItemStack(Items.RAW_IRON, 5));
        ScriptCall deposit = new ScriptCall("deposit", Map.of("container", "auto", "radius", 8,
                "item", "raw_iron", "count", 5));
        SpiritRun run = SpiritRun.launch(helper, spirit, deposit, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Автосдача обязана завершиться успехом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(count(helper, new BlockPos(2, 1, 3), Items.RAW_IRON) == 5,
                    "Руда обязана лежать в сундуке «руда»: фактически "
                            + count(helper, new BlockPos(2, 1, 3), Items.RAW_IRON));
            helper.assertTrue(count(helper, new BlockPos(5, 1, 3), Items.RAW_IRON) == 0,
                    "В сундук «еда» руда попасть не должна: раскладка по категориям сломана");
            GameTestFixtures.finish(helper);
        });
    }

    private static int count(GameTestHelper helper, BlockPos relative, Item item) {
        BlockPos pos = helper.absolutePos(relative);
        BlockEntity entity = helper.getLevel().getBlockEntity(pos);
        if (!(entity instanceof Container container)) {
            return -1;
        }
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void complete(BoardService board, String id, BoardCase boardCase, int level,
            SpiritEntity spirit, long now) {
        if (board.board().find(id).isEmpty()) {
            board.submit(new BoardTicket(id, boardCase, Optional.empty(), BoardRules.priorityOf(boardCase),
                    now, level, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                    TicketTarget.at(new BlockCoord(0, 64, 0)));
        }
        if (board.board().statusOf(id).orElse(null) == BoardStatus.PENDING) {
            board.board().claim(id, spirit.getUUID(), now);
        }
        board.board().complete(id);
    }

    private static Path journalPath(GameTestHelper helper) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("theminespirit").resolve("warehouse-" + UUID.randomUUID() + ".jsonl");
    }

    private static BoardService isolated(ServerLevel level, Path log) {
        JournalService journal = new JournalService(log);
        Board board = new Board(level::getGameTime,
                (event, ticket, holder, tick) -> journal.record(event, ticket, holder, tick));
        return new BoardService(board, new HerdCycles(board),
                new StockReader(BoardTuning.stockReadBudget(), BoardTuning.stockBlockBudget()),
                BoardSources.create(), journal);
    }

    private static void close(BoardService board, Path log, GameTestHelper helper) {
        board.close();
        try {
            Files.deleteIfExists(log);
        } catch (IOException ignored) {
            // Журнал теста во временном каталоге мира: его удалит очистка прогона.
        }
        GameTestFixtures.finish(helper);
    }
}
