package com.theminespirit.gametest;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryPoint;
import com.theminespirit.core.perception.MemoryType;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class TacticsScene {
    static final int SIZE = 11;
    static final float LOW_HP = 4.0F;
    static final int FOREVER = 1_000_000;
    private static final int SWEEP_MARGIN = 45;
    private static final int SWEEP_DEPTH = 8;
    private static final int SWEEP_HEIGHT = 24;

    private TacticsScene() {
    }

    static void prepare(GameTestHelper helper) {
        sweep(helper);
        GameTestFixtures.reset(helper);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    static SpiritEntity spirit(GameTestHelper helper, BlockPos at) {
        return helper.spawn(ModEntities.SPIRIT.get(), at);
    }

    static <T extends Mob> T watcher(GameTestHelper helper, EntityType<T> type, BlockPos at) {
        T mob = helper.spawn(type, at);
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FOREVER, 0, false, false));
        mob.setPersistenceRequired();
        mob.setNoAi(true);
        return mob;
    }

    static final int SETTLE_TICKS = 60;

    static PolicyScene ambush(GameTestHelper helper, SpiritEntity spirit, Mob enemy, BlockPos attackFrom,
            boolean ai, float health) {
        return PolicyScene.launch(helper, spirit, SETTLE_TICKS, () -> {
            spirit.setHealth(health);
            Vec3 at = helper.absoluteVec(new Vec3(attackFrom.getX() + 0.5, attackFrom.getY(), attackFrom.getZ() + 0.5));
            enemy.setPos(at);
            enemy.setNoAi(!ai);
            enemy.setTarget(spirit);
        });
    }

    static void finish(GameTestHelper helper, SpiritEntity spirit) {
        spirit.discard();
        sweep(helper);
        GameTestFixtures.reset(helper);
    }

    static void sweep(GameTestHelper helper) {
        AABB around = new AABB(helper.absoluteVec(new Vec3(-SWEEP_MARGIN, -SWEEP_DEPTH, -SWEEP_MARGIN)),
                helper.absoluteVec(new Vec3(SIZE + SWEEP_MARGIN, SWEEP_HEIGHT, SIZE + SWEEP_MARGIN)));
        for (Entity entity : helper.getLevel().getEntities((Entity) null, around, e -> !(e instanceof Player))) {
            entity.discard();
        }
    }

    static void giveHome(GameTestHelper helper, SpiritEntity spirit, BlockPos home) {
        BlockPos absolute = helper.absolutePos(home);
        spirit.updateMemory(spirit.memory().remember(new MemoryPoint("home", MemoryType.BASE,
                helper.getLevel().dimension().location().toString(),
                new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()))));
    }

    static void wearIronArmor(SpiritEntity spirit) {
        spirit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        spirit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        spirit.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        spirit.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }

    static void slowDown(Mob mob, double speed) {
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
    }

    static double distance(GameTestHelper helper, SpiritEntity spirit, BlockPos relative) {
        Vec3 here = helper.relativeVec(spirit.position());
        return here.distanceTo(new Vec3(relative.getX() + 0.5, relative.getY(), relative.getZ() + 0.5));
    }
}
