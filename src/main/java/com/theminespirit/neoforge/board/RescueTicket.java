package com.theminespirit.neoforge.board;

import com.theminespirit.core.movement.PitEscape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class RescueTicket {
    private static final String GOTO = "goto";
    private static final String DIG_OUT = "dig_out";

    private RescueTicket() {}

    static TaskNode plan(ServerLevel level, SpiritEntity spirit, BlockCoord home) {
        BlockPos feet = spirit.blockPosition();
        Optional<PitEscape.Exit> pit = PitRescue.trapped(spirit);
        if (pit.isPresent()) {
            PitEscape.Exit exit = pit.orElseThrow();
            BlockCoord rim = new BlockCoord(
                    feet.getX() + exit.rimDx(), feet.getY() + exit.rise() + 1, feet.getZ() + exit.rimDz());
            return new FallbackNode(List.of(PitRescue.plan(spirit, exit), digOut(rim)));
        }
        if (!DigOutScript.open(level, feet)) {
            return digOut(SurfacePoint.of(level, new BlockCoord(feet.getX(), feet.getY(), feet.getZ())));
        }
        return new ScriptCall(GOTO, Map.of("x", home.x(), "y", home.y(), "z", home.z()));
    }

    private static ScriptCall digOut(BlockCoord goal) {
        return new ScriptCall(DIG_OUT, Map.of("x", goal.x(), "y", goal.y(), "z", goal.z()));
    }
}
