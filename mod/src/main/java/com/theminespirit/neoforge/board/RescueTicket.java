package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.PickaxeCheck;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class RescueTicket {
    private static final String GOTO = "goto";
    private static final String DIG_OUT = "dig_out";

    record Escape(boolean self, @Nullable TaskNode plan, String why) {
        Escape {
            if (self) {
                Objects.requireNonNull(plan, "Самоспасение без плана: духу нечего выполнять");
            }
            Objects.requireNonNull(why, "Причина выхода не может быть null: лог жизненного цикла без неё нем");
        }
    }

    private RescueTicket() {}

    static Escape escape(ServerLevel level, SpiritEntity spirit, BlockCoord home, boolean noPath) {
        BlockPos feet = spirit.blockPosition();
        Optional<PitExitPlanner.Plan> pit = PitRescue.trapped(spirit);
        if (pit.isPresent()) {
            PitExitPlanner.Plan exit = pit.orElseThrow();
            if (!exit.possible()) {
                return new Escape(false, null, "яма без выхода: " + exit.why());
            }
            return new Escape(
                    true,
                    new FallbackNode(List.of(PitRescue.plan(spirit, exit), digOut(exit.exit()))),
                    "яма: " + exit.why());
        }
        boolean tool = PickaxeCheck.has(spirit);
        boolean open = DigOutScript.open(level, feet);
        int blocks = PitRescue.supplies(spirit).blocks();
        RescuePlans.Self method = RescuePlans.self(new RescuePlans.Situation(false, false, tool, open, noPath, blocks));
        return switch (method) {
            case PIT ->
                throw new IllegalStateException("Яма без плана выхода: PitRescue обязан назвать способ подъёма");
            case WALK ->
                new Escape(
                        true, new FallbackNode(List.of(gotoPoint(home), digOut(home))), "путь домой есть, идёт пешком");
            case DIG -> dig(level, feet, home, open);
            case BRIDGE ->
                new Escape(
                        true,
                        new FallbackNode(List.of(
                                RescuePlans.bridge(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), home, blocks),
                                digOut(home))),
                        "пути домой нет, кирки нет: мостит к поселению своими блоками (" + blocks + ")");
            case NONE -> new Escape(false, null, refusal(open, noPath, blocks));
        };
    }

    private static Escape dig(ServerLevel level, BlockPos feet, BlockCoord home, boolean open) {
        BlockCoord goal = open
                ? climbOutPoint(level, feet, home)
                : SurfacePoint.of(level, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()));
        if (goal.equals(home)) {
            return new Escape(true, digOut(home), "пути нет, копает к дому");
        }
        return new Escape(
                true, new FallbackNode(List.of(digOut(goal), gotoPoint(home))), "под землёй, копает на поверхность");
    }

    private static BlockCoord climbOutPoint(ServerLevel level, BlockPos feet, BlockCoord home) {
        if (home.y() >= feet.getY()) {
            return home;
        }
        int dx = Integer.compare(home.x(), feet.getX()) * 4;
        int dz = Integer.compare(home.z(), feet.getZ()) * 4;
        if (dx == 0 && dz == 0) {
            dx = 4;
        }
        return SurfacePoint.of(level, new BlockCoord(feet.getX() + dx, feet.getY(), feet.getZ() + dz));
    }

    private static String refusal(boolean open, boolean noPath, int blocks) {
        if (!open) {
            return "под землёй нужна кирка, чтобы выйти";
        }
        if (noPath) {
            return "пути домой нет, а кирки и блоков для моста нет (" + blocks + ")";
        }
        return "выход не найден: нет ступеней и кирки";
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall(GOTO, Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static ScriptCall digOut(BlockCoord goal) {
        return new ScriptCall(DIG_OUT, Map.of("x", goal.x(), "y", goal.y(), "z", goal.z(), "rescue", true));
    }
}
