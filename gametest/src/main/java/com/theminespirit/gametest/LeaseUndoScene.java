package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseCell;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.lease.LeaseData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

final class LeaseUndoScene {
    static final String TICKET = "test:undo_lease";

    record Check(LeaseData leases, String id, List<BlockCoord> cells) {}

    private LeaseUndoScene() {}

    static Check submit(GameTestHelper helper, SpiritEntity spirit) {
        BoardService board = BoardRegistry.get(helper.getLevel());
        LeaseData leases = LeaseData.get(helper.getLevel());
        Lease lease = leases.book()
                .open(spirit.getUUID(), null, "scaffold", helper.getLevel().getGameTime());
        List<BlockCoord> cells = new ArrayList<>();
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(2, y, 2), Blocks.DIRT);
            BlockPos at = helper.absolutePos(new BlockPos(2, y, 2));
            cells.add(new BlockCoord(at.getX(), at.getY(), at.getZ()));
        }
        leases.book()
                .record(
                        lease.id(),
                        cells.stream()
                                .map(cell -> LeaseCell.placed(cell, "minecraft:dirt"))
                                .toList());
        leases.book().orphan(lease.id());
        BlockPos stand = helper.absolutePos(new BlockPos(1, 1, 1));
        board.submit(
                new BoardTicket(
                        TICKET,
                        BoardCase.ACTIVITY_BUILD,
                        Optional.of(spirit.getUUID()),
                        95,
                        helper.getLevel().getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(new BlockCoord(stand.getX(), stand.getY(), stand.getZ()))
                        .withPlan(new ScriptCall("undo_lease", Map.of("id", lease.id()))));
        return new Check(leases, lease.id(), List.copyOf(cells));
    }

    static void assertDone(GameTestHelper helper, Check check) {
        helper.assertTrue(
                check.leases().book().require(check.id()).status() == LeaseStatus.DONE,
                "Брошенная аренда обязана закрыться после отката: статус "
                        + check.leases().book().require(check.id()).status());
        for (BlockCoord cell : check.cells()) {
            helper.assertTrue(
                    helper.getLevel()
                            .getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                            .isAir(),
                    "Клетка отката " + cell + " обязана стать воздухом: иначе столб остался в мире");
        }
    }
}
