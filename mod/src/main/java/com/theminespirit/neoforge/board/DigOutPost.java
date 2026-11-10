package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.DigFacts;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;

final class DigOutPost {
    private static final String DIG_OUT = "dig_out";
    private static final String WALK_CAREFUL = "walk_careful";

    private DigOutPost() {}

    static boolean digOut(GraphPost post, BoardService service, GraphPlan.Task task) {
        String makes = task.makes();
        boolean dig = DigFacts.DIG_OUT_ITEM.equals(makes);
        boolean bridge = DigFacts.BRIDGE_ITEM.equals(makes);
        boolean pillar = DigFacts.PILLAR_ITEM.equals(makes);
        if (!dig && !bridge && !pillar) {
            return false;
        }
        boolean matched = false;
        for (SpiritEntity spirit : post.spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            String id = spirit.getUUID().toString();
            GraphFacts.Spirit fact = post.facts.spirits().get(id);
            BlockPos feet = spirit.blockPosition();
            boolean matches = (dig && (DigOutScript.sealed(post.level, feet) || DigOutScript.embedded(spirit)))
                    || (bridge && water(post.level, feet) && !DigOutScript.sealed(post.level, feet))
                    || (pillar && PitRescue.trapped(spirit).isPresent());
            if (!matches) {
                continue;
            }
            matched = true;
            if ((fact != null && fact.stuck()) || post.open("rescue:" + id)) {
                continue;
            }
            BlockCoord goal = DigOutAction.homeOf(post.level, spirit, post.spirits);
            service.detachForAction(spirit);
            spirit.startTask(new SequenceNode(List.of(
                    new ScriptCall(DIG_OUT, Map.of("x", goal.x(), "y", goal.y(), "z", goal.z(), "rescue", true)),
                    new ScriptCall(WALK_CAREFUL, Map.of("x", goal.x(), "y", goal.y(), "z", goal.z())))));
            return true;
        }
        if (matched) {
            post.waiting().add("graph:" + DIG_OUT);
        }
        return false;
    }

    private static boolean water(Level level, BlockPos feet) {
        return level.getFluidState(feet).is(FluidTags.WATER)
                || level.getFluidState(feet.above()).is(FluidTags.WATER);
    }
}
