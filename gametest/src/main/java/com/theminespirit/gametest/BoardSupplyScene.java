package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

final class BoardSupplyScene {
    static final String BUILD_ID = "build_house";
    static final int GATE_LOGS = 1;
    private static final int SUPPLY_WAIT_TICKS = 60;

    private BoardSupplyScene() {}

    static void tick(
            GameTestHelper helper,
            BoardService board,
            SpiritEntity spirit,
            BlockCoord home,
            AtomicInteger phase,
            AtomicInteger supplyWaited) {
        if (phase.get() == 0) {
            waiting(helper, board, phase, supplyWaited);
        } else if (phase.get() == 1) {
            stocked(helper, board, phase);
        } else if (phase.get() == 2) {
            assigned(helper, board, spirit, home, phase);
        } else {
            preempt(helper, board, spirit);
        }
    }

    private static void waiting(
            GameTestHelper helper, BoardService board, AtomicInteger phase, AtomicInteger supplyWaited) {
        helper.assertTrue(
                board.board().statusOf(BUILD_ID).orElse(null) == BoardStatus.WAITING_RESOURCES,
                "Без дерева в запасах стройка обязана висеть в WAITING_RESOURCES, статус "
                        + board.board().statusOf(BUILD_ID).orElse(null) + ": стройка, стартовавшая "
                        + "без ресурсов, ломает гейт доски");
        helper.assertTrue(
                board.board().all().stream().noneMatch(ticket -> ticket.id().startsWith("supply_gather:")),
                "Нехватка стройки уходит в граф блокировкой, а не задачей «добыть» мимо графа: "
                        + board.board().all().stream().map(BoardTicket::id).toList());
        if (supplyWaited.incrementAndGet() > SUPPLY_WAIT_TICKS
                && helper.getBlockEntity(new BlockPos(2, 1, 3)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.OAK_LOG, GATE_LOGS));
            phase.set(1);
        }
    }

    private static void stocked(GameTestHelper helper, BoardService board, AtomicInteger phase) {
        helper.assertTrue(
                board.board().statusOf(BUILD_ID).orElse(null) != BoardStatus.FAILED,
                "Стройка не может провалиться до выдачи: задание закрыто с провалом");
        if (board.board().statusOf(BUILD_ID).orElse(null) == BoardStatus.ASSIGNED) {
            phase.set(2);
        }
    }

    private static void assigned(
            GameTestHelper helper, BoardService board, SpiritEntity spirit, BlockCoord home, AtomicInteger phase) {
        helper.assertTrue(
                board.board().statusOf(BUILD_ID).orElse(null) == BoardStatus.ASSIGNED,
                "После пополнения стройка обязана выдаться сама, статус "
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
    }

    private static void preempt(GameTestHelper helper, BoardService board, SpiritEntity spirit) {
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
                        || board.board().statusOf("test:preempt_retreat").orElse(null) == BoardStatus.DONE,
                "Вытеснение работы обязано отдать духу именно бегство: статус бегства "
                        + board.board().statusOf("test:preempt_retreat").orElse(null));
        board.board().cancel(BUILD_ID);
        board.board().all().stream()
                .filter(ticket -> ticket.id().contains("oak_log"))
                .forEach(ticket -> board.board().cancel(ticket.id()));
        board.board().cancel("test:preempt_retreat");
        helper.succeed();
        GameTestFixtures.finish(helper);
    }
}
