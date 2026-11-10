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
import com.theminespirit.neoforge.lease.LeaseStates;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

final class LeaseUndoScene {
    static final String TICKET = "test:undo_lease";

    record StateCell(BlockCoord pos, BlockState before) {}

    record Check(LeaseData leases, String id, List<BlockCoord> cells, List<StateCell> states) {}

    private LeaseUndoScene() {}

    static Check submit(GameTestHelper helper, SpiritEntity spirit) {
        assertRoundTrip(helper);
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
        List<StateCell> states = new ArrayList<>();
        List<LeaseCell> rollback = new ArrayList<>(cells.stream()
                .map(cell -> LeaseCell.placed(cell, "minecraft:dirt"))
                .toList());
        rollback.add(recordState(
                helper,
                states,
                new BlockPos(3, 2, 2),
                Blocks.OAK_STAIRS.defaultBlockState(),
                Blocks.OAK_STAIRS
                        .defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                        .setValue(BlockStateProperties.HALF, Half.TOP)
                        .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.INNER_LEFT)
                        .setValue(BlockStateProperties.WATERLOGGED, true)));
        rollback.add(recordState(
                helper,
                states,
                new BlockPos(4, 2, 2),
                Blocks.COMPOSTER.defaultBlockState(),
                Blocks.COMPOSTER.defaultBlockState().setValue(BlockStateProperties.LEVEL_COMPOSTER, 7)));
        rollback.add(recordState(
                helper,
                states,
                new BlockPos(3, 2, 3),
                Blocks.REDSTONE_LAMP.defaultBlockState(),
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true)));
        leases.book().record(lease.id(), rollback);
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
        return new Check(leases, lease.id(), List.copyOf(cells), List.copyOf(states));
    }

    private static void assertRoundTrip(GameTestHelper helper) {
        List<BlockState> states = List.of(
                Blocks.STONE.defaultBlockState(),
                Blocks.OAK_STAIRS
                        .defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                        .setValue(BlockStateProperties.HALF, Half.TOP)
                        .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.OUTER_RIGHT)
                        .setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.OAK_FENCE
                        .defaultBlockState()
                        .setValue(BlockStateProperties.NORTH, true)
                        .setValue(BlockStateProperties.EAST, false)
                        .setValue(BlockStateProperties.SOUTH, true)
                        .setValue(BlockStateProperties.WEST, false)
                        .setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true),
                Blocks.COMPOSTER.defaultBlockState().setValue(BlockStateProperties.LEVEL_COMPOSTER, 6),
                Blocks.OAK_LEAVES
                        .defaultBlockState()
                        .setValue(BlockStateProperties.DISTANCE, 3)
                        .setValue(BlockStateProperties.PERSISTENT, true)
                        .setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.HOPPER
                        .defaultBlockState()
                        .setValue(BlockStateProperties.FACING_HOPPER, Direction.WEST)
                        .setValue(BlockStateProperties.ENABLED, false));
        for (BlockState state : states) {
            String text = LeaseStates.text(state);
            helper.assertTrue(
                    LeaseStates.state(text).equals(state),
                    "Состояние " + state + " не пережило запись строкой '" + text + "' и обратный разбор: "
                            + "откат STATE вернул бы не тот блок или потерял свойство");
        }
    }

    private static LeaseCell recordState(
            GameTestHelper helper, List<StateCell> states, BlockPos local, BlockState before, BlockState after) {
        helper.setBlock(local, after);
        BlockPos at = helper.absolutePos(local);
        BlockCoord cell = new BlockCoord(at.getX(), at.getY(), at.getZ());
        states.add(new StateCell(cell, before));
        return LeaseCell.state(cell, LeaseStates.text(before), LeaseStates.text(after));
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
        for (StateCell cell : check.states()) {
            BlockPos pos =
                    new BlockPos(cell.pos().x(), cell.pos().y(), cell.pos().z());
            helper.assertTrue(
                    helper.getLevel().getBlockState(pos).equals(cell.before()),
                    "Клетка состояния " + cell.pos() + " обязана вернуться в " + cell.before() + ", а в мире "
                            + helper.getLevel().getBlockState(pos)
                            + ": иначе не-дверной STATE после отката остаётся изменённым");
        }
    }
}
