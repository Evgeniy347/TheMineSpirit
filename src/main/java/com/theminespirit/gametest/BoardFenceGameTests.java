package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Сломанный забор на доске: поломка ограждения рядом с поселением → REPAIR_FENCE с высшим
 * приоритетом; стройка при этом ждёт, чинит один дух. Природный камень — не кейс.
 * Всё синхронно: событие идёт прямо на шину, тело теста атомарно.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardFenceGameTests {
    private static final String BATCH = "board_fence";

    private BoardFenceGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void stopMobsTargetingSpirits(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(false);
    }

    @AfterBatch(batch = BATCH)
    public static void restoreMobTargeting(ServerLevel level) {
        SpiritConfig.setMobsTargetSpirits(true);
    }

    @GameTest(template = "arena", batch = BATCH, timeoutTicks = 100)
    public static void fence_BrokenNearBase_RepairFirstSingleHolder(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        BlockCoord home = absolute(helper, new BlockPos(4, 1, 4));
        spirit.setHome(home);
        ServerLevel level = helper.getLevel();
        long now = level.getGameTime();
        ServerPlayer witness =
                GameTestFixtures.mockPlayer(helper, new BlockPos(4, 1, 2), "board_fence_witness");
        BlockPos fence = new BlockPos(5, 1, 5);
        helper.setBlock(fence, Blocks.OAK_FENCE);
        BlockPos fenceAbs = helper.absolutePos(fence);
        BlockState fenceState = level.getBlockState(fenceAbs);
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, fenceAbs, fenceState, witness));
        helper.setBlock(fence, Blocks.AIR);
        String repairId = "repair_fence:" + fenceAbs.getX() + "," + fenceAbs.getY() + ","
                + fenceAbs.getZ();
        BlockPos stone = new BlockPos(2, 1, 6);
        helper.setBlock(stone, Blocks.STONE);
        BlockPos stoneAbs = helper.absolutePos(stone);
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, stoneAbs,
                level.getBlockState(stoneAbs), witness));
        String stoneId = "repair_fence:" + stoneAbs.getX() + "," + stoneAbs.getY() + ","
                + stoneAbs.getZ();
        BoardService board = BoardRegistry.get(level);
        board.submit(new BoardTicket("board_test_build_low", BoardCase.BUILD_HOUSE, Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE), now, 6000, List.of(),
                Map.of()), TicketTarget.at(home));

        BoardTicket repair = board.board().find(repairId).orElse(null);
        helper.assertTrue(repair != null,
                "Сломанный забор рядом с поселением обязан поднять REPAIR_FENCE " + repairId
                        + ": иначе дыра в ограждении стоит открытой");
        helper.assertTrue(repair.boardCase() == BoardCase.REPAIR_FENCE,
                "Задание сломанного забора — кейс REPAIR_FENCE, фактически " + repair.boardCase());
        helper.assertTrue(BoardRules.priorityOf(BoardCase.REPAIR_FENCE) == 100,
                "Приоритет ремонта ограждений — 100, высший на доске, фактически "
                        + BoardRules.priorityOf(BoardCase.REPAIR_FENCE));
        helper.assertTrue(board.board().find(stoneId).isEmpty(),
                "Сломанный природный камень — не ограждение: задание " + stoneId
                        + " поднялось зря");
        List<String> queue = board.board().claimable(new StockView(Map.of()), now).stream()
                .map(BoardTicket::id)
                .filter(id -> id.equals(repairId) || id.equals("board_test_build_low"))
                .toList();
        helper.assertTrue(List.of(repairId, "board_test_build_low").equals(queue),
                "Ремонт выше стройки: очередь " + queue + ": стройка, обошедшая ремонт, "
                        + "оставляет поселение без забора");
        helper.assertTrue(board.board().claim(repairId, spirit.getUUID(), now)
                        == BoardOutcome.CLAIMED,
                "Ремонт берёт свободный дух");
        helper.assertTrue(board.board().claim(repairId, UUID.randomUUID(), now)
                        == BoardOutcome.TAKEN_BY_OTHER,
                "Второй дух тот же ремонт не берёт: чинит один, остальные прикрывают "
                        + "существующим механизмом координатора, а не толпой");
        helper.assertTrue(spirit.getUUID().equals(board.board().holderOf(repairId).orElse(null)),
                "Держатель ремонта — взявший его дух: держатель "
                        + board.board().holderOf(repairId).orElse(null));
        helper.assertTrue(board.board().statusOf("board_test_build_low").orElse(null)
                        == BoardStatus.PENDING,
                "Пока ремонт не закрыт, стройка ждёт: статус "
                        + board.board().statusOf("board_test_build_low").orElse(null));
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
