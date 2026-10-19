package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@SettlementSystems(board = true)
public final class BoardGameTests {
    private static final String BATCH = "board_supply";
    private static final String BUILD_ID = "build_house";
    private static final String SUPPLY_ID = "supply:oak_log";
    private static final int GATE_LOGS = 1;
    private static final int SUPPLY_WAIT_LIMIT = 200;

    private BoardGameTests() {}

    @BeforeBatch(batch = BATCH)
    public static void stopMobsTargetingSpirits(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = BATCH)
    public static void restoreMobTargeting(ServerLevel level) {
        GameTestFilter.setMobsTargetSpirits(true);
    }

    // [флак] добыча одного бревна вручную, путь к сундуку и ожидание пересборки: под нагрузкой
    // машины не укладывается в 400 тиков, запас двойной.
    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 800)
    public static void supply_BuildNeedsWood_WaitsThenIssuesAfterGather(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BoardService board = BoardRegistry.get(helper.getLevel());
        drainForeignTickets(board);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockCoord home = absolute(helper, new BlockPos(4, 1, 4));
        spirit.setHome(home);
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
                TicketTarget.at(home));
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
                    helper.fail("При нехватке доска не выдала задание добычи SUPPLY_SITE за "
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
            } else {
                helper.assertTrue(
                        board.board().statusOf(BUILD_ID).orElse(null) == BoardStatus.ASSIGNED,
                        "После добычи стройка обязана выдаться сама, статус "
                                + board.board().statusOf(BUILD_ID).orElse(null) + ": ждущее задание, не "
                                + "выданное после пополнения, — доска не будит свою очередь");
                helper.assertTrue(
                        spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                        "Стройка обязана быть выдана духу-владельцу: статус духа "
                                + spirit.taskStatus().orElse(null));
                board.board().cancel(BUILD_ID);
                board.board().cancel(SUPPLY_ID);
                helper.succeed();
                GameTestFixtures.finish(helper);
            }
        });
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
