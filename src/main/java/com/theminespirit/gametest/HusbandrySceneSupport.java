package com.theminespirit.gametest;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class HusbandrySceneSupport {
    private static final int BABY_AGE = -24000;

    private HusbandrySceneSupport() {}

    static void assertSucceeded(GameTestHelper helper, SpiritEntity spirit, String expectation) {
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                expectation + ": статус "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + spirit.taskFailureReason().orElse("нет"));
    }

    static void assertFailedWith(GameTestHelper helper, SpiritEntity spirit, String fragment) {
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                "задача обязана провалиться, а статус "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
        String reason = spirit.taskFailureReason().orElse("");
        helper.assertTrue(
                reason.contains(fragment),
                "причина провала обязана содержать «" + fragment + "», фактически: " + reason);
    }

    static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        for (int slot = 0; slot < stacks.length; slot++) {
            spirit.getInventory().setItem(slot, stacks[slot]);
        }
        return spirit;
    }

    static <T extends Entity> List<T> alive(GameTestHelper helper, Class<T> type) {
        Vec3 first = helper.absoluteVec(new Vec3(-2, 0, -2));
        Vec3 second = helper.absoluteVec(new Vec3(11, 8, 11));
        return helper.getLevel()
                .getEntitiesOfClass(type, new AABB(first, second), entity -> !entity.isRemoved() && entity.isAlive());
    }

    static <T extends Mob> T spawnStill(GameTestHelper helper, EntityType<T> type, BlockPos relative) {
        T mob = helper.spawn(type, relative);
        mob.setNoAi(true);
        return mob;
    }

    static void spawnBaby(GameTestHelper helper, EntityType<? extends AgeableMob> type, BlockPos relative) {
        spawnBaby(helper, type, relative, false);
    }

    static void spawnBaby(
            GameTestHelper helper, EntityType<? extends AgeableMob> type, BlockPos relative, boolean still) {
        AgeableMob baby = type.create(helper.getLevel());
        baby.setAge(BABY_AGE);
        baby.setNoAi(still);
        Vec3 at = helper.absoluteVec(new Vec3(relative.getX() + 0.5, relative.getY(), relative.getZ() + 0.5));
        baby.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        helper.getLevel().addFreshEntity(baby);
    }

    static void fencePerimeter(GameTestHelper helper) {
        for (int i = 0; i <= 8; i++) {
            helper.setBlock(new BlockPos(i, 1, 0), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(i, 1, 8), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(0, 1, i), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(8, 1, i), Blocks.OAK_FENCE);
        }
    }
}
