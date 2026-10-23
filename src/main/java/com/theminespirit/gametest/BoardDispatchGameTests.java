package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HerdCycles;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardSources;
import com.theminespirit.neoforge.board.JournalService;
import com.theminespirit.neoforge.board.StockReader;
import com.theminespirit.neoforge.intellect.TaskStarter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Блокеры живого прогона 2: выдача доски при паузе своего задания (INC-23), ремонт без наковальни и
 * причина провала чужой задачи (INC-24), верстак у соседа для крафта печи (INC-26).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardDispatchGameTests {
    private static final String OWN = "dispatch:own";
    private static final String FREE = "dispatch:free";
    private static final BoardCase PAUSED_CASE = BoardCase.STOCK_CHESTS;

    private BoardDispatchGameTests() {}

    @GameTest(template = "arena", batch = "board_dispatch_paused_holder", timeoutTicks = 100)
    public static void dispatch_OwnTicketPaused_SpiritTakesFreeTicket(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-dispatch-paused.jsonl");
        long now = board.board().now();
        String failed = "dispatch:failed";
        board.submit(ticket(failed, PAUSED_CASE, Optional.empty(), now), at(helper));
        board.board().claim(failed, spirit.getUUID(), now);
        board.board().fail(failed);
        helper.assertTrue(
                !board.board().availableTo(spirit.getUUID(), PAUSED_CASE, now),
                "Провал ставит паузу кейса духу: без неё сцена не воспроизводит INC-23");
        board.submit(ticket(OWN, PAUSED_CASE, Optional.of(spirit.getUUID()), now), at(helper));
        board.submit(
                ticket(FREE, BoardCase.GATHER_MATERIALS, Optional.empty(), now),
                at(helper).withSupply("dirt", 1));

        board.rebuild(level, level.getGameTime());

        helper.assertTrue(
                board.board().holderOf(FREE).filter(spirit.getUUID()::equals).isPresent(),
                "Дух на паузе по своему заданию " + OWN + " берёт свободное " + FREE + ": держатель "
                        + board.board().holderOf(FREE)
                        + " — иначе все духи простаивают, пока их тикеты ремонта висят PENDING (INC-23)");
        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "board_dispatch_repair_anvil", timeoutTicks = 100)
    public static void repair_NoAnvilAtHome_TicketOnlyAfterAnvil(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        BlockPos homePos = helper.absolutePos(new BlockPos(4, 1, 4));
        spirit.setHome(new BlockCoord(homePos.getX(), homePos.getY(), homePos.getZ()));
        ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() / 2);
        spirit.getInventory().setItem(0, pickaxe);
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 8));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-dispatch-repair.jsonl");
        String repair = "repair_tool:" + spirit.getUUID();

        board.rebuild(level, level.getGameTime());
        helper.assertTrue(
                board.board().find(repair).isEmpty(),
                "Без наковальни у дома ремонт материалом невыполним — задание не ставится: иначе цикл "
                        + "FAILED→POSTED каждые 5 с (INC-24); статус "
                        + board.board().statusOf(repair));

        helper.setBlock(new BlockPos(6, 1, 4), Blocks.ANVIL);
        board.rebuild(level, level.getGameTime() + BoardTuning.boardIntervalTicks());
        helper.assertTrue(
                board.board().find(repair).isPresent(),
                "Наковальня у дома появилась — ремонт изношенной кирки ставится");
        spirit.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "board_dispatch_foreign_failure", timeoutTicks = 200)
    public static void starter_ForeignTaskFails_NotBlamedOnOwnScript(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        TaskStarter starter = new TaskStarter();
        long tick = helper.getLevel().getGameTime();
        helper.assertTrue(
                starter.start(spirit, "wait", new ScriptCall("wait", Map.of("ticks", 400)), tick),
                "Интеллект выдал своё занятие: без него нечему приписать чужой провал");
        spirit.startTask(new ScriptCall("eat", Map.of()));
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "Чужая задача (доска/REST) ещё не провалилась: " + spirit.taskStatus());
            long now = helper.getLevel().getGameTime();
            starter.start(spirit, "wait", new ScriptCall("wait", Map.of("ticks", 1)), now);
            starter.start(spirit, "wait", new ScriptCall("wait", Map.of("ticks", 1)), now + 1);
            helper.assertTrue(
                    starter.failures() == 0,
                    "Провал чужой задачи (" + spirit.taskFailureReason().orElse("?") + ") не засчитан занятию "
                            + "интеллекта: иначе «нет наковальни» приписывалось unload/hunt и они вставали на откат "
                            + "(INC-24); провалов " + starter.failures());
            spirit.discard();
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "board_dispatch_table_transfer", timeoutTicks = 100)
    public static void furnace_TableAtNeighbour_TransferredToCrafter(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity crafter = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        SpiritEntity holder = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(9, 1, 9));
        crafter.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        holder.getInventory().setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        ServerLevel level = helper.getLevel();
        BoardService board = isolatedBoard(level, "gametest-dispatch-table.jsonl");
        String furnace = "dispatch:furnace";
        board.submit(
                ticket(
                        furnace,
                        BoardCase.CRAFT_FURNACE,
                        Optional.empty(),
                        board.board().now()),
                at(helper));

        board.rebuild(level, level.getGameTime());

        boolean tableTransfer = board.board().all().stream()
                .anyMatch(t -> t.boardCase() == BoardCase.TRANSFER_ITEM
                        && t.modifiers().stream().anyMatch(m -> m.contains("crafting_table")));
        helper.assertTrue(
                tableTransfer,
                "Верстака у места крафта нет, он у соседа — доска ставит передачу верстака крафтеру печи: иначе "
                        + "«нужен верстак: рядом (16) нет» и BLOCKED (INC-26); задания "
                        + board.board().all());
        crafter.discard();
        holder.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static TicketTarget at(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        return TicketTarget.at(new BlockCoord(at.getX(), at.getY(), at.getZ()));
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, Optional<UUID> assignee, long now) {
        return new BoardTicket(
                id,
                boardCase,
                assignee,
                BoardRules.priorityOf(boardCase),
                now,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
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
