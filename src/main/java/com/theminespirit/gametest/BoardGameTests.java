package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
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
public final class BoardGameTests {
    private static final String BATCH = "board_supply";
    private static final String BUILD_ID = "build_house";
    private static final String SUPPLY_ID = "supply:oak_log";
    private static final int GATE_LOGS = 16;

    private BoardGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void stopMobsTargetingSpirits(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = BATCH)
    public static void restoreMobTargeting(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(true);
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 400)
    public static void supply_BuildNeedsWood_WaitsThenIssuesAfterGather(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockCoord home = absolute(helper, new BlockPos(4, 1, 4));
        spirit.setHome(home);
        for (int tree = 0; tree < 4; tree++) {
            for (int height = 1; height <= 4; height++) {
                helper.setBlock(new BlockPos(6 + tree * 2, height, 6), Blocks.OAK_LOG);
            }
        }
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.CHEST);
        BoardService board = BoardRegistry.get(helper.getLevel());
        board.submit(new BoardTicket(BUILD_ID, BoardCase.BUILD_HOUSE, Optional.empty(), 50,
                helper.getLevel().getGameTime(), 6000, List.of(), Map.of("oak_log", GATE_LOGS)),
                TicketTarget.at(home));
        board.rebuild(helper.getLevel(), helper.getLevel().getGameTime());
        AtomicInteger phase = new AtomicInteger(0);
        helper.onEachTick(() -> {
            if (phase.get() == 0) {
                helper.assertTrue(board.board().statusOf(BUILD_ID).orElse(null) == BoardStatus.WAITING_RESOURCES,
                        "Без дерева в запасах стройка обязана висеть в WAITING_RESOURCES, статус "
                                + board.board().statusOf(BUILD_ID).orElse(null) + ": стройка, стартовавшая "
                                + "без ресурсов, ломает гейт доски");
                helper.assertTrue(board.board().statusOf(SUPPLY_ID).isPresent(),
                        "При нехватке доска обязана поставить задание добычи SUPPLY_SITE, его нет на доске");
                helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.RUNNING
                                && spirit.currentTaskScript().orElse("").equals("acquire"),
                        "Задание добычи обязано быть выдано свободному духу как acquire, статус "
                                + spirit.taskStatus().orElse(null) + ", скрипт "
                                + spirit.currentTaskScript().orElse("нет"));
                phase.set(1);
            } else if (phase.get() == 1 && spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED) {
                board.rebuild(helper.getLevel(), helper.getLevel().getGameTime());
                phase.set(2);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(phase.get() == 2,
                    "Добыча не завершилась за отведённые тики: статус духа "
                            + spirit.taskStatus().orElse(null));
            helper.assertTrue(board.board().statusOf(BUILD_ID).orElse(null) == BoardStatus.ASSIGNED,
                    "После добычи стройка обязана выдаться сама, статус "
                            + board.board().statusOf(BUILD_ID).orElse(null) + ": ждущее задание, не "
                            + "выданное после пополнения, — доска не будит свою очередь");
            GameTestFixtures.finish(helper);
        });
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
