package com.theminespirit.neoforge;

import com.theminespirit.core.building.ChestPair;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

final class ChestPairCheck {
    record Result(ChestPair.Decision decision, BlockPlacer.RefuseReason reason, String message) {
        static Result ok() {
            return new Result(ChestPair.Decision.PLACE, BlockPlacer.RefuseReason.OCCUPIED, "");
        }

        boolean refused() {
            return decision == ChestPair.Decision.REFUSE;
        }
    }

    private ChestPairCheck() {}

    static Result inspect(SpiritEntity spirit, Level level, BlockPos target, BlockState state) {
        BlockPos partner = target.relative(ChestBlock.getConnectedDirection(state));
        BlockState other = level.getBlockState(partner);
        ChestType wanted = state.getValue(ChestBlock.TYPE);
        ChestType otherType = other.hasProperty(ChestBlock.TYPE) ? other.getValue(ChestBlock.TYPE) : null;
        boolean paired = other.getBlock() instanceof ChestBlock
                && otherType != null
                && otherType != ChestType.SINGLE
                && otherType != wanted
                && level.getBlockEntity(partner) instanceof ChestBlockEntity;
        boolean stale =
                level.getBlockEntity(partner) instanceof ChestBlockEntity && !(other.getBlock() instanceof ChestBlock);
        boolean free = other.isAir() || other.canBeReplaced();
        boolean clear =
                free && BlockPlacer.livingEntities(level, partner, spirit).isEmpty();
        boolean supported = paired || PlaceSupport.hasSupport(level, partner, state);
        BlockPos feetPos = spirit.blockPosition();
        boolean reachable = paired
                || SpiritReach.current()
                        .reaches(
                                new BlockCoord(feetPos.getX(), feetPos.getY(), feetPos.getZ()),
                                new BlockCoord(partner.getX(), partner.getY(), partner.getZ()));
        ChestPair check = new ChestPair(paired, clear, supported, reachable, stale);
        if (check.decision() == ChestPair.Decision.PLACE) {
            return Result.ok();
        }
        BlockPlacer.RefuseReason reason = stale || !clear
                ? BlockPlacer.RefuseReason.OCCUPIED
                : !supported ? BlockPlacer.RefuseReason.NO_SUPPORT : BlockPlacer.RefuseReason.OUT_OF_REACH;
        return new Result(
                ChestPair.Decision.REFUSE,
                reason,
                "вторая половина " + target.toShortString() + " у " + partner.toShortString() + " недоступна ("
                        + check.refusal()
                        + "): половину двойного сундука не ставлю, иначе останется битый сундук");
    }
}
