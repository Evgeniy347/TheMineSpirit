package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFailureMemory;
import com.theminespirit.core.board.graph.GraphFeedback;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.BoardStateStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardGameTests {
    private static final String BUILD_ID = "build_house";
    private static final String SUPPLY_ID = "supply_gather:oak_log";
    private static final String PICKAXE_ID = "test:craft_stone_pickaxe";
    private static final int GATE_LOGS = 1;
    private static final int SUPPLY_WAIT_LIMIT = 200;

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

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.STONE_PICKAXE) >= 1,
                    "Дух без кирки обязан получить её по заданию доски: без этого шахта ждёт инструмент, "
                            + "которого никто не делает; инвентарь " + spirit.getInventory());
            helper.assertTrue(
                    board.board().statusOf(PICKAXE_ID).orElse(null) == BoardStatus.DONE,
                    "Задание крафта кирки обязано закрыться по факту предмета, статус "
                            + board.board().statusOf(PICKAXE_ID).orElse(null));
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] добыча одного бревна вручную, путь к сундуку и ожидание пересборки: под нагрузкой
    // машины не укладывается в 400 тиков, запас двойной.
    @SceneStep(template = "arena", timeoutTicks = 800, board = true, calmMobs = true)
    public static void supply_BuildNeedsWood_WaitsThenIssuesAfterGather(GameTestHelper helper) {
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
                        BUILD_ID,
                        BoardCase.BUILD_HOUSE,
                        Optional.of(spirit.getUUID()),
                        50,
                        helper.getLevel().getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of("oak_log", GATE_LOGS)),
                TicketTarget.at(home).withSupply("oak_log", GATE_LOGS));
        board.rebuild(helper.getLevel(), helper.getLevel().getGameTime());
        AtomicInteger phase = new AtomicInteger(0);
        AtomicInteger supplyWaited = new AtomicInteger(0);
        helper.onEachTick(() -> {
            if (phase.get() == 0) {
                helper.assertTrue(
                        board.board().statusOf(BUILD_ID).orElse(null) == BoardStatus.WAITING_RESOURCES,
                        "Без дерева в запасах стройка обязана висеть в WAITING_RESOURCES, статус "
                                + board.board().statusOf(BUILD_ID).orElse(null) + ": стройка, стартовавшая "
                                + "без ресурсов, ломает гейт доски");
                BoardStatus supply = board.board().statusOf(SUPPLY_ID).orElse(null);
                if (supply == BoardStatus.ASSIGNED) {
                    phase.set(1);
                } else if (supply == BoardStatus.FAILED) {
                    helper.fail("Добыча провалилась: задание " + SUPPLY_ID + " закрыто с провалом, "
                            + "стройка не может выдаться после неудачной добычи");
                } else if (supplyWaited.incrementAndGet() > SUPPLY_WAIT_LIMIT) {
                    helper.fail("При нехватке доска не выдала задание добычи GATHER_MATERIALS за "
                            + supplyWaited.get() + " тиков, статус " + supply + ": без добычи стройка "
                            + "зависнет навсегда");
                }
            } else if (phase.get() == 1) {
                helper.assertTrue(
                        board.board().statusOf(BUILD_ID).orElse(null) != BoardStatus.FAILED,
                        "Стройка не может провалиться до выдачи: задание закрыто с провалом");
                if (board.board().statusOf(BUILD_ID).orElse(null) == BoardStatus.ASSIGNED) {
                    phase.set(2);
                }
            } else if (phase.get() == 2) {
                helper.assertTrue(
                        board.board().statusOf(BUILD_ID).orElse(null) == BoardStatus.ASSIGNED,
                        "После добычи стройка обязана выдаться сама, статус "
                                + board.board().statusOf(BUILD_ID).orElse(null) + ": ждущее задание, не "
                                + "выданное после пополнения, — доска не будит свою очередь");
                helper.assertTrue(
                        spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                        "Стройка обязана быть выдана духу-владельцу: статус духа "
                                + spirit.taskStatus().orElse(null));
                spirit.setHome(home);
                Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(2, 1, 2));
                husk.setNoAi(true);
                husk.setPersistenceRequired();
                board.submit(
                        new BoardTicket(
                                "test:preempt_retreat",
                                BoardCase.ACTIVITY_RETREAT,
                                Optional.of(spirit.getUUID()),
                                100,
                                helper.getLevel().getGameTime(),
                                1,
                                BoardTicket.ALWAYS_CAUSE,
                                List.of(),
                                Map.of()),
                        TicketTarget.at(home).withPlan(new ScriptCall("retreat", Map.of())));
                phase.set(3);
            } else {
                if (board.board().statusOf(BUILD_ID).orElse(null) != BoardStatus.PENDING) {
                    return;
                }
                String script = spirit.currentTaskScript().orElse(null);
                boolean fleeing = "retreat".equals(script) || "flee".equals(script);
                boolean finished = spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED;
                if (!fleeing && !finished) {
                    return;
                }
                helper.assertTrue(
                        board.board().statusOf("test:preempt_retreat").orElse(null) == BoardStatus.ASSIGNED
                                || board.board()
                                                .statusOf("test:preempt_retreat")
                                                .orElse(null)
                                        == BoardStatus.DONE,
                        "Вытеснение работы обязано отдать духу именно бегство: статус бегства "
                                + board.board().statusOf("test:preempt_retreat").orElse(null));
                board.board().cancel(BUILD_ID);
                board.board().cancel(SUPPLY_ID);
                board.board().cancel("test:preempt_retreat");
                helper.succeed();
                GameTestFixtures.finish(helper);
            }
        });
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
