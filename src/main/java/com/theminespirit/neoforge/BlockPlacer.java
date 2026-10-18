package com.theminespirit.neoforge;

import com.theminespirit.core.building.BlockSupport;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public final class BlockPlacer {
    public enum RefuseReason {
        CHUNK_NOT_LOADED,
        OCCUPIED,
        NO_SUPPORT,
        OUT_OF_REACH,
        NO_MATERIAL
    }

    public sealed interface Begin permits Started, Refused, Protected {
    }

    public record Started(BlockPos pos) implements Begin {
    }

    public record Refused(RefuseReason reason, String message) implements Begin {
    }

    public record Protected(PolicyId policy, String reason) implements Begin {
    }

    private final SpiritEntity spirit;

    public BlockPlacer(SpiritEntity spirit) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, кто ставит");
    }

    public Begin begin(BlockPos target, BlockState state, String blockId) {
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, куда ставить");
        Objects.requireNonNull(state, "Состояние не может быть null: без него неизвестно, что ставить");
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, что списывать");
        Level level = spirit.level();
        if (level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) {
            return new Refused(RefuseReason.CHUNK_NOT_LOADED,
                    "чанк с клеткой " + target.toShortString() + " не загружен: дух не грузит чанки ради стройки");
        }
        BlockState current = level.getBlockState(target);
        if (!current.isAir() && !current.canBeReplaced()) {
            return new Refused(RefuseReason.OCCUPIED,
                    "клетка " + target.toShortString() + " занята блоком " + BlockStates.idOf(current) + ": ставить некуда");
        }
        double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(target));
        if (distance > BlockBreaker.REACH) {
            return new Refused(RefuseReason.OUT_OF_REACH, "клетка " + target.toShortString() + " дальше " + BlockBreaker.REACH
                    + " от глаз духа (" + distance + "): сначала подойти");
        }
        if (spirit.protectionGate().checkPlace(target) instanceof BlockProtection.Refused refused) {
            return new Protected(refused.policy(), refused.reason());
        }
        if (!hasSupport(level, target)) {
            return new Refused(RefuseReason.NO_SUPPORT,
                    "под клеткой " + target.toShortString() + " нет опоры: блок без опоры обвалится или повиснет");
        }
        Item wanted = BlockItems.itemFor(blockId);
        if (spirit.getInventory().countItem(wanted) < 1) {
            return new Refused(RefuseReason.NO_MATERIAL,
                    "нет материала '" + blockId + "': нужно 1, есть 0");
        }
        spirit.getInventory().removeItemType(wanted, 1);
        level.setBlock(target, state, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, target, GameEvent.Context.of(spirit, state));
        if (level instanceof ServerLevel server) {
            PlacedBlocksData.get(server).markSpirit(target);
        }
        return new Started(target.immutable());
    }

    private static boolean hasSupport(Level level, BlockPos target) {
        return BlockSupport.hasSupport((x, y, z, face) -> {
            BlockPos pos = new BlockPos(x, y, z);
            return level.getBlockState(pos).isFaceSturdy(level, pos, direction(face));
        }, target.getX(), target.getY(), target.getZ());
    }

    private static Direction direction(BlockSupport.Face face) {
        return switch (face) {
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
        };
    }
}
