package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFailureMemory;
import com.theminespirit.core.board.graph.GraphFeedback;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseCell;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardStateStore;
import com.theminespirit.neoforge.lease.LeaseData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardGameTests {
    private static final String PICKAXE_ID = "test:craft_stone_pickaxe";

    private BoardGameTests() {}

    // [флак] замер ~300 тиков: подход, добыча материалов и крафт; бюджет 800 ≈ 2×
    @SceneStep(template = "arena", timeoutTicks = 800, board = true)
    public static void craftStonePickaxe_BoardTicket_GivesSpiritTool(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        CraftStationCheck.unreachableStationSkipsToAnother(helper);
        BoardService board = BoardRegistry.get(helper.getLevel());
        for (BoardTicket ticket : board.board().all()) {
            board.board().cancel(ticket.id());
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        spirit.getInventory().setItem(1, new ItemStack(Items.STICK, 4));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.CRAFTING_TABLE);
        BlockCoord home = absolute(helper, new BlockPos(1, 1, 1));
        TicketTarget target = TicketTarget.at(home).withToolCount(1);
        board.submit(
                new BoardTicket(
                        PICKAXE_ID,
                        BoardCase.CRAFT_STONE_PICKAXE,
                        Optional.of(spirit.getUUID()),
                        90,
                        helper.getLevel().getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                target);
        assertTargetSurvivesReload(helper, PICKAXE_ID, target);
        assertTargetSurvivesReload(
                helper,
                "world:backfill:test",
                TicketTarget.at(home)
                        .withCraterBlocks(3)
                        .withLeveling(new PlotLeveling(
                                List.of(),
                                List.of(new PlotLeveling.Box(
                                        home.x(), home.y(), home.z(), home.x(), home.y(), home.z())),
                                PlotLeveling.DEFAULT_BLOCK)));
        assertFailuresSurviveReload(helper);
        assertLeasesSurviveReload(helper);
        LeaseUndoScene.Check undo = LeaseUndoScene.submit(helper, spirit);

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.STONE_PICKAXE) >= 1,
                    "Дух без кирки обязан получить её по заданию доски: без этого шахта ждёт инструмент, "
                            + "которого никто не делает; инвентарь " + spirit.getInventory());
            helper.assertTrue(
                    board.board().statusOf(PICKAXE_ID).orElse(null) == BoardStatus.DONE,
                    "Задание крафта кирки обязано закрыться по факту предмета, статус "
                            + board.board().statusOf(PICKAXE_ID).orElse(null));
            LeaseUndoScene.assertDone(helper, undo);
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] добыча одного бревна вручную, путь к сундуку и ожидание пересборки: под нагрузкой
    // машины не укладывается в 400 тиков, запас двойной.
    @SceneStep(template = "arena", timeoutTicks = 800, board = true, calmMobs = true)
    public static void supply_BuildNeedsWood_WaitsThenIssuesAfterStock(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BoardService board = BoardRegistry.get(helper.getLevel());
        drainForeignTickets(board);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        BlockCoord home = absolute(helper, new BlockPos(4, 1, 4));
        for (int tree = 0; tree < 2; tree++) {
            for (int height = 1; height <= 4; height++) {
                helper.setBlock(new BlockPos(6 + tree * 2, height, 6), Blocks.OAK_LOG);
            }
        }
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.CHEST);
        board.submit(
                new BoardTicket(
                        BoardSupplyScene.BUILD_ID,
                        BoardCase.BUILD_HOUSE,
                        Optional.of(spirit.getUUID()),
                        50,
                        helper.getLevel().getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of("oak_log", BoardSupplyScene.GATE_LOGS)),
                TicketTarget.at(home).withSupply("oak_log", BoardSupplyScene.GATE_LOGS));
        board.rebuild(helper.getLevel(), helper.getLevel().getGameTime());
        AtomicInteger phase = new AtomicInteger(0);
        AtomicInteger supplyWaited = new AtomicInteger(0);
        helper.onEachTick(() -> BoardSupplyScene.tick(helper, board, spirit, home, phase, supplyWaited));
    }

    private static void assertTargetSurvivesReload(GameTestHelper helper, String id, TicketTarget target) {
        BoardStateStore store = new BoardStateStore();
        store.updateTargets(Map.of(id, target));

        CompoundTag saved = store.save(new CompoundTag(), helper.getLevel().registryAccess());
        BoardStateStore loaded = BoardStateStore.load(saved, helper.getLevel().registryAccess());

        helper.assertTrue(
                target.equals(loaded.targets().get(id)),
                "Цель задания " + id + " обязана пережить сохранение доски: иначе после перезапуска яма "
                        + "ставится второй раз или продолжение теряет клетки; в NBT "
                        + loaded.targets().get(id));
    }

    private static void assertLeasesSurviveReload(GameTestHelper helper) {
        LeaseData data = new LeaseData();
        UUID spirit = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Lease active = data.book().open(spirit, "ticket-restart", "scaffold", 10);
        data.book()
                .record(
                        active.id(),
                        List.of(
                                LeaseCell.placed(new BlockCoord(0, 64, 0), "minecraft:dirt"),
                                LeaseCell.broken(new BlockCoord(1, 64, 0), "minecraft:stone")));
        Lease orphan = data.book().open(spirit, null, "bridge", 20);
        data.book().record(orphan.id(), LeaseCell.state(new BlockCoord(2, 64, 0), "open", "closed"));
        data.book().orphan(orphan.id());
        data.migrateLegacy(List.of(new BlockCoord(3, 64, 0)), 30);

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        LeaseData loaded = LeaseData.load(saved, helper.getLevel().registryAccess());

        helper.assertTrue(
                loaded.book().all().equals(data.book().all()),
                "Аренды временных правок обязаны пережить перезапуск: иначе брошенные подмости и мосты исчезнут "
                        + "из скана, а действующие потеряют владельца; в NBT "
                        + loaded.book().all());
        helper.assertTrue(
                loaded.migrated(),
                "Признак миграции старых T-меток обязан пережить перезапуск: иначе они мигрируют снова");
    }

    private static void assertFailuresSurviveReload(GameTestHelper helper) {
        GraphFailureMemory memory = new GraphFailureMemory();
        memory.note("chop_jungle_log", "10,64,20", new GraphBlock.Path("10,64,20"), 100);
        memory.note("chop_jungle_log", "10,64,20", new GraphBlock.Path("10,64,20"), 120);
        BoardStateStore store = new BoardStateStore();
        store.updateFailures(memory.saved());

        CompoundTag saved = store.save(new CompoundTag(), helper.getLevel().registryAccess());
        BoardStateStore loaded = BoardStateStore.load(saved, helper.getLevel().registryAccess());
        GraphFailureMemory restored = new GraphFailureMemory();
        restored.restore(loaded.failures());

        GraphFeedback.Failure fact = restored.recent(
                        "chop_jungle_log", "10,64,20", 120, GraphFailureMemory.DEFAULT_HALF_LIFE_TICKS)
                .orElseThrow();
        helper.assertTrue(
                fact.count() == 2 && fact.need() instanceof GraphBlock.Path,
                "Память неудач обязана пережить перезапуск с числом и причиной: иначе недостижимая точка "
                        + "выбирается снова; в NBT " + fact);
    }

    private static void drainForeignTickets(BoardService board) {
        for (BoardTicket ticket : board.board().all()) {
            board.board().cancel(ticket.id());
        }
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
