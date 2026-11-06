package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.movement.GateCrossing;
import com.theminespirit.core.movement.Passage;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class PassageWalk {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double REACH = 4.0;
    private static final double ABANDONED = 2 * REACH;
    private static final int NODES_AHEAD = 3;
    private static final int SEARCH = 3;
    private static final double OCCUPIED_INFLATE = 0.1;

    private final SpiritEntity spirit;
    private final Map<BlockCoord, GateCrossing> opened = new HashMap<>();

    public PassageWalk(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public boolean tick() {
        closePassed();
        return pathPassage().map(this::open).orElse(false);
    }

    public boolean openToward(BlockCoord target) {
        BlockCoord feet = blockCoord(spirit.blockPosition());
        List<Passage.Cell> candidates = new ArrayList<>();
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Passage.Cell cell =
                            passageAt(spirit.level(), new BlockCoord(feet.x() + dx, feet.y() + dy, feet.z() + dz));
                    if (cell != null && !cell.open()) {
                        candidates.add(cell);
                    }
                }
            }
        }
        return Passage.towardTarget(feet, target, candidates)
                .map(cell -> open(cell.coord()))
                .orElse(false);
    }

    public void settle() {
        closeAll(coord -> !occupied(coord));
    }

    private Optional<BlockCoord> pathPassage() {
        Path path = spirit.getNavigation().getPath();
        if (path == null) {
            return Optional.empty();
        }
        int from = path.getNextNodeIndex();
        int to = Math.min(path.getNodeCount(), from + NODES_AHEAD);
        for (int i = from; i < to; i++) {
            Node node = path.getNode(i);
            if (node.type != PathType.WALKABLE_DOOR) {
                continue;
            }
            BlockCoord coord = new BlockCoord(node.x, node.y, node.z);
            Passage.Cell cell = passageAt(spirit.level(), coord);
            if (cell != null && !cell.open() && withinReach(coord)) {
                return Optional.of(coord);
            }
        }
        return Optional.empty();
    }

    private boolean open(BlockCoord coord) {
        BlockPos pos = toPos(coord);
        BlockState state = spirit.level().getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN)) {
            return false;
        }
        if (!use(pos, state)) {
            return false;
        }
        opened.put(coord, new GateCrossing(coord, blockCoord(spirit.blockPosition())));
        LOGGER.info(
                "[move] {} открыл {} в {}: закрытая преграда на пути",
                spirit.getName().getString(),
                label(coord),
                pos.toShortString());
        return true;
    }

    private void closePassed() {
        BlockCoord feet = blockCoord(spirit.blockPosition());
        closeAll(coord -> (passed(coord, feet) || abandoned(coord)) && !occupied(coord));
    }

    private void closeAll(Predicate<BlockCoord> ready) {
        if (opened.isEmpty()) {
            return;
        }
        List<BlockCoord> closing = new ArrayList<>();
        for (BlockCoord coord : opened.keySet()) {
            Passage.Cell cell = passageAt(spirit.level(), coord);
            if (cell == null || !cell.open()) {
                closing.add(coord);
            } else if (ready.test(coord)) {
                closing.add(coord);
            }
        }
        for (BlockCoord coord : closing) {
            close(coord);
        }
    }

    private void close(BlockCoord coord) {
        opened.remove(coord);
        BlockPos pos = toPos(coord);
        Passage.Cell cell = passageAt(spirit.level(), coord);
        if (cell == null || !cell.open() || occupied(coord)) {
            return;
        }
        if (!use(pos, spirit.level().getBlockState(pos))) {
            return;
        }
        LOGGER.info(
                "[move] {} закрыл {} в {}: прошёл, створка больше не нужна",
                spirit.getName().getString(),
                label(coord),
                pos.toShortString());
    }

    private boolean use(BlockPos pos, BlockState state) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return false;
        }
        Player presser = FakePlayerFactory.getMinecraft(level);
        presser.moveTo(spirit.getX(), spirit.getY(), spirit.getZ(), spirit.getYRot(), spirit.getXRot());
        InteractionResult result = state.useWithoutItem(
                level, presser, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return result.consumesAction();
    }

    public static Passage.@Nullable Cell passageAt(Level level, BlockCoord coord) {
        BlockState state = level.getBlockState(toPos(coord));
        if (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()) {
            if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                return null;
            }
            return new Passage.Cell(coord, Passage.Kind.DOOR, state.getValue(DoorBlock.OPEN));
        }
        if (state.getBlock() instanceof FenceGateBlock) {
            return new Passage.Cell(coord, Passage.Kind.FENCE_GATE, state.getValue(BlockStateProperties.OPEN));
        }
        return null;
    }

    private boolean withinReach(BlockCoord coord) {
        Vec3 center = Vec3.atCenterOf(toPos(coord));
        return spirit.getEyePosition().distanceToSqr(center) <= REACH * REACH;
    }

    private boolean passed(BlockCoord coord, BlockCoord feet) {
        GateCrossing crossing = opened.get(coord);
        crossing.observe(feet);
        return crossing.closable();
    }

    private boolean abandoned(BlockCoord coord) {
        Vec3 center = Vec3.atCenterOf(toPos(coord));
        return spirit.position().distanceToSqr(center) > ABANDONED * ABANDONED;
    }

    private boolean occupied(BlockCoord coord) {
        AABB box = new AABB(toPos(coord)).inflate(OCCUPIED_INFLATE);
        return !spirit.level()
                .getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)
                .isEmpty();
    }

    private String label(BlockCoord coord) {
        Passage.Cell cell = passageAt(spirit.level(), coord);
        if (cell == null) {
            return "проход";
        }
        return cell.kind() == Passage.Kind.DOOR ? "дверь" : "калитку";
    }

    private static BlockCoord blockCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
