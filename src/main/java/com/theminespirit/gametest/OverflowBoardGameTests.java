package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OverflowBoardGameTests {
    private static final int SETTLE_TICKS = 20;
    private static final int WAIT_TICKS = 6000;

    private OverflowBoardGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400, board = true)
    public static void overflowDrop_AfterSwap_BoardTaskTakenByAnotherSpirit(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity dropper = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        dropper.setHome(absolute(helper, new BlockPos(1, 1, 1)));
        for (int slot = 0; slot < dropper.getInventory().getContainerSize(); slot++) {
            dropper.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        SpiritEntity taker = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(7, 1, 7));
        BoardService board = BoardRegistry.get(helper.getLevel());
        drainTickets(board);
        GameTestFixtures.dropAt(helper, Items.DIAMOND, 1, 1.5, 1, 1.5);
        SpiritRun run = SpiritRun.launch(
                helper,
                dropper,
                new SequenceNode(List.of(
                        new ScriptCall("collect_drops", Map.of("radius", 6)),
                        new ScriptCall("wait", Map.of("ticks", WAIT_TICKS)))),
                SETTLE_TICKS);
        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    GameTestFixtures.countInInventory(dropper.getInventory(), Items.DIAMOND) == 1,
                    "дух обязан обменять дешёвый булыжник на алмаз, фактически алмазов "
                            + GameTestFixtures.countInInventory(dropper.getInventory(), Items.DIAMOND));
            BoardTicket ticket = dropTicket(board);
            helper.assertTrue(
                    ticket != null, "после выкидывания на доске обязано появиться задание «забрать выкинутые вещи»");
            board.rebuild(helper.getLevel(), helper.getLevel().getGameTime());
            board.rebuild(helper.getLevel(), helper.getLevel().getGameTime());
            ticket = dropTicket(board);
            helper.assertTrue(
                    ticket != null,
                    "задание на выкинутую вещь обязано пережить пересборку, пока предмет лежит в мире: "
                            + "иначе доска снимает его раньше, чем дух успел забрать");
            helper.assertTrue(
                    board.board().statusOf(ticket.id()).orElse(null) == BoardStatus.ASSIGNED,
                    "задание на выкинутую вещь обязано быть выдано свободному духу, статус "
                            + board.board().statusOf(ticket.id()).orElse(null));
            helper.assertTrue(
                    board.board().holderOf(ticket.id()).orElse(null) != null
                            && board.board().holderOf(ticket.id()).orElseThrow().equals(taker.getUUID()),
                    "задание обязан взять другой свободный дух, а не тот, кто выкинул вещь: держатель "
                            + board.board().holderOf(ticket.id()).orElse(null));
            helper.assertTrue(
                    taker.taskStatus().orElse(null) == RunStatus.RUNNING,
                    "взявший задание дух обязан начать его исполнять, статус "
                            + taker.taskStatus().orElse(null));
            board.cancel(helper.getLevel(), ticket.id());
            GameTestFixtures.finish(helper);
        });
    }

    private static BoardTicket dropTicket(BoardService board) {
        for (BoardTicket ticket : board.board().all()) {
            if (ticket.boardCase() == BoardCase.COLLECT_DROPS && ticket.id().startsWith("drop:")) {
                return ticket;
            }
        }
        return null;
    }

    private static void drainTickets(BoardService board) {
        for (BoardTicket ticket : board.board().all()) {
            board.board().cancel(ticket.id());
        }
    }

    private static BlockCoord absolute(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
