package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.lease.LeaseData;
import com.theminespirit.neoforge.lease.LeaseRun;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

final class DigOutPillarScene {
    record Pillar(SpiritEntity spirit, List<BlockPos> column, int groundY, String leaseId) {}

    private DigOutPillarScene() {}

    static Pillar spawnOnOwnPillar(GameTestHelper helper, BoardService board) {
        BlockPos base = new BlockPos(0, DigOutGameTests.TOP + 1, 0);
        List<BlockPos> column = new ArrayList<>();
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), base.above(5));
        LeaseRun lease = LeaseRun.open(spirit, "scaffold");
        PlacedBlocksData placed = PlacedBlocksData.get(helper.getLevel());
        for (int dy = 0; dy < 5; dy++) {
            BlockPos cell = base.above(dy);
            helper.setBlock(cell, Blocks.DIRT);
            placed.markSpirit(helper.absolutePos(cell));
            lease.placed(helper.absolutePos(cell), "minecraft:dirt");
            column.add(cell);
        }
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_SHOVEL));
        BlockPos home = helper.absolutePos(new BlockPos(8, DigOutGameTests.TOP + 1, 5));
        spirit.setHome(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        BlockPos feet = spirit.blockPosition();
        BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        UUID id = spirit.getUUID();
        long now = helper.getLevel().getGameTime();
        String ticket = "rescue:" + id;
        board.board().stuck().see(id, at, now);
        board.board().stuck().mark(id, now, "сцена: дух на своём столбе", true);
        TaskNode plan = RescuePlans.descent(new BlockCoord(home.getX(), home.getY(), home.getZ()));
        board.submit(
                new BoardTicket(
                        ticket,
                        BoardCase.RESCUE_SPIRIT,
                        Optional.of(id),
                        BoardRules.priorityOf(BoardCase.RESCUE_SPIRIT),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(at).withPlan(plan));
        return new Pillar(spirit, column, helper.absolutePos(base).getY(), lease.id());
    }

    static void assertPillarGone(GameTestHelper helper, BoardService board, Pillar pillar) {
        helper.assertTrue(
                pillar.spirit().getY() <= pillar.groundY() + 0.5,
                "дух на своём столбе обязан спуститься на землю, а не висеть на нём: стоит на "
                        + pillar.spirit().blockPosition() + ", земля на " + pillar.groundY() + ", задача "
                        + pillar.spirit().currentTaskScript().orElse("-") + " "
                        + pillar.spirit().taskStatus().orElse(null) + ", причина "
                        + pillar.spirit().taskFailureReason().orElse("нет"));
        for (BlockPos cell : pillar.column()) {
            helper.assertTrue(
                    helper.getBlockState(cell).isAir(),
                    "свой столб разобран до конца: в " + cell + " остался блок, и блоки не вернулись в работу");
        }
        helper.assertTrue(
                LeaseData.get(helper.getLevel())
                                .book()
                                .require(pillar.leaseId())
                                .status()
                        == LeaseStatus.DONE,
                "успешный разбор столба закрывает аренду подмостей DONE: иначе клетки остались бы в уборке "
                        + "графа, а не вернулись в работу");
        helper.assertFalse(
                board.board()
                        .stuck()
                        .isStuck(pillar.spirit().getUUID(), board.board().now()),
                "выполненный спуск со столба снимает метку «не двигается» по факту, а не по тайм-ауту: "
                        + "иначе дух навсегда выпадает из раздачи");
    }
}
