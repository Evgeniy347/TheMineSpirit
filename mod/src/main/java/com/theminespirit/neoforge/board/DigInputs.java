package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.DigFacts;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;

final class DigInputs {
    private DigInputs() {}

    static GraphFacts apply(ServerLevel level, List<SpiritEntity> spirits, GraphFacts facts) {
        Map<String, GraphFacts.Spirit> known = new LinkedHashMap<>(facts.spirits());
        for (SpiritEntity spirit : spirits) {
            String id = spirit.getUUID().toString();
            GraphFacts.Spirit fact = known.get(id);
            if (fact == null || !spirit.currentActivity().isEmpty()) {
                continue;
            }
            BlockPos feet = spirit.blockPosition();
            DigFacts.Cause situation = situation(level, spirit, feet);
            String cause = DigFacts.trap(situation);
            if (cause != null) {
                known.put(
                        id,
                        new GraphFacts.Spirit(
                                fact.id(),
                                fact.position(),
                                fact.health(),
                                fact.maxHealth(),
                                fact.recentHurt(),
                                fact.food(),
                                fact.air(),
                                fact.inWater(),
                                true,
                                fact.stuck(),
                                fact.activity(),
                                fact.inventory(),
                                fact.armor(),
                                fact.tools(),
                                cause));
            }
        }
        return facts.withSpirits(known);
    }

    private static DigFacts.Cause situation(Level level, SpiritEntity spirit, BlockPos feet) {
        if (DigOutScript.embedded(spirit) || DigOutScript.sealed(level, feet)) {
            return DigFacts.Cause.SEALED;
        }
        if (PitRescue.trapped(spirit).isPresent()) {
            return DigFacts.Cause.PIT;
        }
        if (water(level, feet)) {
            return DigFacts.Cause.WATER;
        }
        return DigOutScript.open(level, feet) ? DigFacts.Cause.OPEN : DigFacts.Cause.WALL;
    }

    private static boolean water(Level level, BlockPos feet) {
        return level.getFluidState(feet).is(FluidTags.WATER)
                || level.getFluidState(feet.above()).is(FluidTags.WATER);
    }
}
