package com.theminespirit.neoforge;

import com.theminespirit.core.movement.LookAngles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;

/**
 * Жест работы с блоком: дух поворачивает голову и тело к блоку и машет рукой — как игрок при постановке и ломании.
 * Общий для стройки, шахты, рубки, копки и посадки: игрок видит, кто и над чем работает.
 */
public final class SpiritGesture {
    private static final double CENTER = 0.5;

    private SpiritGesture() {}

    public static void workOn(SpiritEntity spirit, BlockPos target) {
        face(spirit, target);
        spirit.swing(InteractionHand.MAIN_HAND, true);
    }

    public static void keepWorking(SpiritEntity spirit, BlockPos target) {
        face(spirit, target);
        if (!spirit.swinging) {
            spirit.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    public static void face(SpiritEntity spirit, BlockPos target) {
        double x = target.getX() + CENTER;
        double y = target.getY() + CENTER;
        double z = target.getZ() + CENTER;
        LookAngles angles = LookAngles.toward(x - spirit.getX(), y - spirit.getEyeY(), z - spirit.getZ());
        if (spirit.getNavigation().isDone()) {
            // mc: тело стоящего моба не поворачивается следом за головой больше чем на 75° — повернуть и тело.
            spirit.setYRot(angles.yaw());
            spirit.setYBodyRot(angles.yaw());
        }
        spirit.setYHeadRot(angles.yaw());
        spirit.setXRot(angles.pitch());
        spirit.getLookControl().setLookAt(x, y, z, 180.0F, 180.0F);
    }
}
