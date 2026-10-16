package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.FirstStepsRunner;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Первые шаги на доске: новое поселение → CLEAR_GROUNDS; дальше задания идут строго по цепочке,
 * каждое следующее — после выполнения предыдущего; выполненная цепочка новых заданий не плодит.
 * Всё синхронно: генератор — чистая функция от фактов мира, тело теста атомарно.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardFirstStepsGameTests {
    private static final String BATCH = "board_first_steps";

    private BoardFirstStepsGameTests() {
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
    public static void firstSteps_NewSettlement_ChainInOrderWithoutDuplicates(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.setHome(absolute(helper, new BlockPos(4, 1, 4)));
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        placeLogs(helper, false);
        int prevIndex = -1;
        Set<String> seen = new HashSet<>();

        // Чужие шаги из общей доски (поставлены глобальным тиком для других сцен) закрываем
        // помечая выполненными: иначе активных больше одного и тест видит чужую работу.
        for (BoardTicket ticket : board.board().all()) {
            if (ticket.id().startsWith("first:")) {
                closeTicket(board, ticket.id(), spirit);
            }
        }

        for (int step = 0; step < 8; step++) {
            long now = level.getGameTime();
            FirstStepsRunner.tick(level, board, List.of(spirit), now);
            List<BoardTicket> active = board.board().all().stream()
                    .filter(ticket -> ticket.id().startsWith("first:"))
                    .filter(ticket -> {
                        BoardStatus status = board.board().statusOf(ticket.id()).orElse(null);
                        return status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED;
                    })
                    .toList();
            helper.assertTrue(active.size() <= 1,
                    "Задание первых шагов всегда одно: активных " + active.size()
                            + ": иначе поселение строится вразнобой");
            if (active.isEmpty()) {
                break;
            }
            BoardTicket head = active.get(0);
            int index = chainIndex(head.boardCase());
            helper.assertTrue(index > prevIndex,
                    "Следующий шаг " + head.boardCase() + " идёт после предыдущего (индекс "
                            + prevIndex + "): цепочка не идёт назад и не топчется на месте");
            helper.assertTrue(seen.add(head.id()),
                    "Задание " + head.id()
                            + " уже выполнялось: выполненные шаги повторно не ставятся");
            satisfy(helper, board, spirit, head.boardCase());
            closeTicket(board, head.id(), spirit);
            prevIndex = index;
        }
        helper.assertTrue(prevIndex >= chainIndex(BoardCase.BUILD_MINE),
                "Цепочка сошлась не раньше шахты: последний шаг " + prevIndex
                        + ": ранний конец означает пропуск шагов, а не выполненную цепочку "
                        + "(соседние сцены видят те же блоки и могут заранее закрыть "
                        + "поздние факты — порядок и конец цепочки от этого не меняются)");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static int chainIndex(BoardCase boardCase) {
        return switch (boardCase) {
            case CLEAR_GROUNDS -> 0;
            case STOCK_CHESTS -> 1;
            case CRAFTING_STATION -> 2;
            case CRAFT_TOOLS -> 3;
            case BUILD_MINE -> 4;
            case BUILD_FENCE -> 5;
            case LIGHTING -> 6;
            case BUILD_HOUSE -> 7;
            default -> throw new IllegalArgumentException("Не шаг цепочки: " + boardCase);
        };
    }

    private static void satisfy(GameTestHelper helper, BoardService board, SpiritEntity spirit,
            BoardCase head) {
        switch (head) {
            case CLEAR_GROUNDS -> placeLogs(helper, true);
            case STOCK_CHESTS -> {
                helper.setBlock(new BlockPos(6, 1, 2), Blocks.CHEST);
                helper.setBlock(new BlockPos(7, 1, 2), Blocks.CHEST);
            }
            case CRAFTING_STATION -> helper.setBlock(new BlockPos(2, 1, 6), Blocks.CRAFTING_TABLE);
            case CRAFT_TOOLS -> {
                spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE, 1));
                ServerLevel level = helper.getLevel();
                board.rebuild(level, level.getGameTime());
            }
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
            default -> throw new IllegalArgumentException("Не шаг цепочки: " + head);
        }
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
