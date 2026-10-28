package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoardV2GameTests {
    private static final int NOON_TIME = 6_000;

    private BoardV2GameTests() {}

    // [флак] перенос: путь, разбор старого загона и стройка нового 4×5; под нагрузкой
    // машины 400 тиков мало, запас двойной.
    @SceneStep(template = "arena", timeoutTicks = 800, board = true, calmMobs = true)
    public static void relocate_PenMoved_OldClearedNewStands(GameTestHelper helper) {
        GameTestFixtures.holdDayTime(helper, NOON_TIME);
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        BoardService board = BoardRegistry.get(level);
        drainForeignTickets(board);
        SpiritEntity spirit = spawnAt(helper, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.OAK_FENCE, 32));
        spirit.getInventory().setItem(1, new ItemStack(Items.OAK_FENCE_GATE, 4));
        spirit.getInventory().setItem(2, new ItemStack(Items.OAK_LOG, 4));
        spirit.getInventory().setItem(3, new ItemStack(Items.TORCH, 4));
        BlockPos oldOne = new BlockPos(1, 1, 5);
        BlockPos oldTwo = new BlockPos(2, 1, 5);
        BlockPos oldThree = new BlockPos(3, 1, 5);
        helper.setBlock(oldOne, Blocks.OAK_FENCE);
        helper.setBlock(oldTwo, Blocks.OAK_FENCE);
        helper.setBlock(oldThree, Blocks.OAK_FENCE);
        markSpirit(helper, oldOne);
        markSpirit(helper, oldTwo);
        markSpirit(helper, oldThree);
        BlockCoord from = absolute(helper, new BlockPos(2, 1, 6));
        BlockCoord to = absolute(helper, new BlockPos(4, 1, 1));
        String id = "relocate_pen";
        board.submit(
                new BoardTicket(
                        id,
                        BoardCase.RELOCATE_BUILDING,
                        Optional.of(spirit.getUUID()),
                        BoardRules.priorityOf(BoardCase.RELOCATE_BUILDING),
                        level.getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(to).withRelocate(from, to).withScheme("pen_4x5"));
        board.rebuild(level, level.getGameTime());
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            BoardStatus status = board.board().statusOf(id).orElse(null);
            if (status == BoardStatus.FAILED) {
                helper.fail("Перенос провалился: " + spirit.taskFailureReason().orElse("причина не записана")
                        + "; прогресс " + spirit.taskProgress().orElse(null));
            }
            if (status != BoardStatus.DONE || finished[0]) {
                return;
            }
            finished[0] = true;
            helper.assertTrue(
                    helper.getBlockState(oldOne).isAir()
                            && helper.getBlockState(oldTwo).isAir()
                            && helper.getBlockState(oldThree).isAir(),
                    "Старый загон обязан быть снесён в from: остались "
                            + helper.getBlockState(oldOne) + ", " + helper.getBlockState(oldTwo)
                            + ", " + helper.getBlockState(oldThree));
            int fence = countFence(helper, to, 4, 5);
            helper.assertTrue(
                    fence > 0,
                    "Новый загон обязан стоять в to: клеток забора в 4×5 — " + fence
                            + "; в from " + countFence(helper, from, 4, 5)
                            + "; в руках осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_FENCE)
                            + " забора и "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_FENCE_GATE)
                            + " калиток; клетка to "
                            + helper.getLevel().getBlockState(new BlockPos(to.x(), to.y(), to.z()))
                            + ": без новой постройки перенос теряет загон");
            helper.assertTrue(
                    hasGate(helper, to, 4, 5), "Новый загон обязан сохранить калитку: без неё животное не загнать");
            GameTestFixtures.finish(helper);
            helper.succeed();
        });
    }

    private static void drainForeignTickets(BoardService board) {
        for (BoardTicket ticket : board.board().all()) {
            BoardStatus status = board.board().statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.DONE && status != BoardStatus.CANCELLED) {
                board.board().cancel(ticket.id());
            }
        }
    }

    private static void markSpirit(GameTestHelper helper, BlockPos relative) {
        PlacedBlocksData.get(helper.getLevel()).markSpirit(helper.absolutePos(relative));
    }

    private static int countFence(GameTestHelper helper, BlockCoord origin, int width, int depth) {
        int count = 0;
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                if (helper.getLevel()
                                .getBlockState(new BlockPos(origin.x() + dx, origin.y(), origin.z() + dz))
                                .getBlock()
                        instanceof FenceBlock) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean hasGate(GameTestHelper helper, BlockCoord origin, int width, int depth) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                if (helper.getLevel()
                                .getBlockState(new BlockPos(origin.x() + dx, origin.y(), origin.z() + dz))
                                .getBlock()
                        instanceof FenceGateBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private static SpiritEntity spawnAt(GameTestHelper helper, BlockPos relative, BlockPos home) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        spirit.setHome(absolute(helper, home));
        return spirit;
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
