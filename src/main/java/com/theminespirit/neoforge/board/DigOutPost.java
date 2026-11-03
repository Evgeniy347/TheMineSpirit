package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.DigFacts;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

final class DigOutPost {
    private static final String DIG_OUT = "dig_out";
    private static final String WALK_CAREFUL = "walk_careful";

    private DigOutPost() {}

    static boolean digOut(GraphPost post, BoardService service, GraphPlan.Task task) {
        if (!DigFacts.DIG_OUT_ITEM.equals(task.makes())) {
            return false;
        }
        BlockCoord settlement = SettlementHome.of(post.spirits);
        for (SpiritEntity spirit : post.spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            BlockPos feet = spirit.blockPosition();
            if (!DigOutScript.sealed(post.level, feet)) {
                continue;
            }
            BlockCoord goal = SurfacePoint.of(post.level, settlement);
            service.detachForAction(spirit);
            spirit.startTask(new SequenceNode(List.of(
                    new ScriptCall(DIG_OUT, Map.of("x", goal.x(), "y", goal.y(), "z", goal.z())),
                    new ScriptCall(WALK_CAREFUL, Map.of("x", goal.x(), "y", goal.y(), "z", goal.z())))));
            return true;
        }
        return false;
    }
}
