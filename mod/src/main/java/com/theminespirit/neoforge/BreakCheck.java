package com.theminespirit.neoforge;

import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.tactics.adapter.Visibility;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class BreakCheck {
    record Request(
            SpiritEntity spirit,
            BlockPos target,
            BlockBreaker.Intent intent,
            boolean reachLimited,
            boolean leavesPass,
            boolean escaping,
            boolean rescue,
            BreakRefusal refusals) {}

    private BreakCheck() {}

    static BlockBreaker.@Nullable Begin refusal(Request request) {
        SpiritEntity spirit = request.spirit();
        BlockPos target = request.target();
        BlockBreaker.Intent intent = request.intent();
        boolean reachLimited = request.reachLimited();
        boolean leavesPass = request.leavesPass();
        boolean escaping = request.escaping();
        boolean rescue = request.rescue();
        BreakRefusal refusals = request.refusals();
        Level level = spirit.level();
        if (level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) {
            return refusals.refused(
                    target,
                    BlockBreaker.RefuseReason.CHUNK_NOT_LOADED,
                    "чанк с блоком " + target.toShortString() + " не загружен: дух не грузит чанки ради ломки");
        }
        BlockState targetState = level.getBlockState(target);
        if (targetState.isAir()) {
            return refusals.refused(
                    target, BlockBreaker.RefuseReason.AIR, "в " + target.toShortString() + " воздух: ломать нечего");
        }
        double hardness = targetState.getDestroySpeed(level, target);
        if (hardness < 0) {
            return refusals.refused(
                    target,
                    BlockBreaker.RefuseReason.UNBREAKABLE,
                    "блок " + BreakNames.blockId(targetState) + " в " + target.toShortString() + " неразрушим: "
                            + hardness);
        }
        if (reachLimited) {
            double distance = spirit.getEyePosition().distanceTo(Vec3.atCenterOf(target));
            if (distance > BlockBreaker.REACH) {
                return refusals.refused(
                        target,
                        BlockBreaker.RefuseReason.OUT_OF_REACH,
                        "блок " + target.toShortString() + " дальше " + BlockBreaker.REACH + " от глаз (" + distance
                                + "): подойти");
            }
        }
        boolean chop = intent == BlockBreaker.Intent.CHOP || intent == BlockBreaker.Intent.CHOP_HARVEST;
        if (!chop && BreakSight.visible(spirit, target, leavesPass) != Visibility.CLEAR) {
            return refusals.refused(
                    target,
                    BlockBreaker.RefuseReason.NO_SIGHT,
                    "блок " + target.toShortString() + " не виден из глаз: подойти или убрать мешающий блок");
        }
        if (BreakProtection.check(spirit, intent, target, targetState, escaping, rescue)
                instanceof BlockProtection.Refused refused) {
            return refusals.protectedBlock(target, refused.policy(), refused.reason());
        }
        return null;
    }
}
