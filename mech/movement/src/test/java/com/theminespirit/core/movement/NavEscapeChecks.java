package com.theminespirit.core.movement;

import com.theminespirit.core.navigation.NavPillarEscape;
import com.theminespirit.core.navigation.NavWorld;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteWorld;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

final class NavEscapeChecks {
    private NavEscapeChecks() {}

    static boolean canDrop(RouteWorld world, Set<BlockCoord> plateau, int landingY, int maxFall) {
        for (BlockCoord from : plateau) {
            for (int[] side : FuzzWorld.SIDES) {
                BlockCoord to = new BlockCoord(from.x() + side[0], landingY, from.z() + side[1]);
                if (BodyMotion.canMove(world, from, to, maxFall)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void checkSealedSpace(long seed, FuzzWorld world, List<String> failures) {
        if (SealedSpace.sealed(world.start(), FuzzWorldViews.sealedView(world))
                && NavFuzzChecks.bodyWalkReachesSky(world, world.start())) {
            failures.add(seed + " SealedSpace: дух назван замурованным, хотя BodyMotion находит пеший выход к небу");
        }
    }

    static void checkPitWalkOut(long seed, FuzzWorld world, List<String> failures) {
        boolean exists = PitWalkOut.exists(
                FuzzWorldViews.pitWalkWorld(world),
                world.start().x(),
                world.start().z(),
                world.start().y());
        boolean body = NavFuzzChecks.bodyWalkReachesSky(world, world.start());
        if (exists && !body) {
            failures.add(seed + " PitWalkOut: пеший выход назван, а тело до неба не доходит");
        }
        if (body && !exists && world.expectPitWalkOut()) {
            failures.add(seed + " PitWalkOut: тело доходит до неба, а пешего выхода планировщик не видит");
        }
    }

    static void checkNavPillar(long seed, FuzzWorld world, List<String> failures) {
        NavWorld nav = FuzzWorldViews.navWorld(world);
        Set<BlockCoord> plateau = NavPillarEscape.plateau(
                nav, world.start().x(), world.start().y(), world.start().z());
        if (plateau.isEmpty()) {
            return;
        }
        OptionalInt landing = NavPillarEscape.landingFrom(nav, plateau, 12);
        if (landing.isPresent() && !canDrop(world, plateau, landing.getAsInt(), 12)) {
            failures.add(seed + " NavPillarEscape: приземление y=" + landing.getAsInt() + " с узкой опоры " + plateau
                    + " неисполнимо телом");
        }
    }
}
