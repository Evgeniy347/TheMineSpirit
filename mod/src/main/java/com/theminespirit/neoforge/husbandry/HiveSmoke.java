package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.board.HiveRules;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.crafting.CampfireKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class HiveSmoke {
    private static final int FIRE_WAIT_TICKS = 100;
    private static final String CAMPFIRE_ID = "minecraft:campfire";
    private static final double FIRE_ARRIVAL = 1.2;

    private final HiveSession session;

    HiveSmoke(HiveSession session) {
        this.session = session;
    }

    void smoke() {
        if (!HiveRules.campfireNeeded(CampfireBlock.isSmokeyPos(session.level, session.hive))) {
            session.state = HiveSession.State.HARVEST;
            return;
        }
        if (lightUnlitCampfire()) {
            session.state = HiveSession.State.HARVEST;
            return;
        }
        if (!CampfireKit.ensure(session.spirit)) {
            session.fail("костра под ульем нет и материалов для него не хватает: пчёлы разозлятся, " + "улей "
                    + session.hive.toShortString() + " не тронут");
            return;
        }
        session.fireSpot = campfireSpot();
        if (session.fireSpot == null) {
            session.fail("под ульем " + session.hive.toShortString() + " нет места для костра в "
                    + HiveRules.smokeDepth() + " блоках: собирать без дыма нельзя, пчёлы разозлятся");
            return;
        }
        if (session.distanceTo(session.fireSpot) > BlockBreaker.REACH) {
            session.approach.begin(Vec3.atCenterOf(session.fireSpot), FIRE_ARRIVAL);
            session.state = HiveSession.State.FIRE_APPROACH;
            return;
        }
        session.state = HiveSession.State.FIRE;
    }

    void fireApproachTick() {
        switch (session.approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> session.state = HiveSession.State.FIRE;
            case STUCK, TIMEOUT ->
                session.fail("к месту костра " + session.fireSpot.toShortString() + " не подойти: улей не тронут");
        }
    }

    void fire() {
        BlockPlacer placer = new BlockPlacer(session.spirit, PlotProtection.Ground.NONE);
        BlockPlacer.Begin begin = placer.begin(session.fireSpot, BlockStates.stateFor(CAMPFIRE_ID), CAMPFIRE_ID);
        if (begin instanceof BlockPlacer.Started) {
            if (!CampfireBlock.isSmokeyPos(session.level, session.hive)) {
                session.fail("костёр в " + session.fireSpot.toShortString()
                        + " поставлен, но дым не доходит до улья " + session.hive.toShortString()
                        + ": улей не тронут");
                return;
            }
            session.state = HiveSession.State.HARVEST;
            return;
        }
        if (begin instanceof BlockPlacer.Protected protectedSpot) {
            session.fail("ставить костёр запрещено: " + protectedSpot.reason() + "; улей не тронут");
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        boolean creatureInCell = refused.reason() == BlockPlacer.RefuseReason.OCCUPIED
                && session.level.getBlockState(session.fireSpot).canBeReplaced();
        if (creatureInCell && ++session.fireWaits <= FIRE_WAIT_TICKS) {
            return;
        }
        session.fail("костёр в " + session.fireSpot.toShortString() + " не поставлен (" + refused.reason()
                + (creatureInCell ? ", клетку " + FIRE_WAIT_TICKS + " тиков занимало существо" : "")
                + "): улей не тронут");
    }

    private boolean lightUnlitCampfire() {
        for (int offset = 1; offset <= HiveRules.smokeDepth(); offset++) {
            if (!HiveRules.smokeOffsetWithinDepth(offset)) {
                continue;
            }
            BlockPos pos = session.hive.below(offset);
            BlockState state = session.level.getBlockState(pos);
            if (state.is(Blocks.CAMPFIRE)
                    && state.hasProperty(CampfireBlock.LIT)
                    && !state.getValue(CampfireBlock.LIT)) {
                session.level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), 3);
            }
        }
        return CampfireBlock.isSmokeyPos(session.level, session.hive);
    }

    private BlockPos campfireSpot() {
        for (int offset = 1; offset <= HiveRules.smokeDepth(); offset++) {
            if (!HiveRules.smokeOffsetWithinDepth(offset)) {
                continue;
            }
            BlockPos pos = session.hive.below(offset);
            BlockState state = session.level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced()) {
                continue;
            }
            if (supports(session.level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean supports(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return true;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = pos.relative(side);
            if (level.getBlockState(neighbour).isFaceSturdy(level, neighbour, side.getOpposite())) {
                return true;
            }
        }
        return false;
    }
}
