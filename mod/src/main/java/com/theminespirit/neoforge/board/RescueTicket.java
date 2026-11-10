package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.navigation.PillarDescent;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.PickaxeCheck;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.RouteLevelView;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
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

    static Escape escape(ServerLevel level, SpiritEntity spirit, BlockCoord home, boolean noPath, boolean pathHome) {
        BlockPos feet = spirit.blockPosition();
        if (pathHome) {
            return new Escape(true, RescuePlans.walk(home), "путь домой открыт: идёт пешком, мир не трогает");
        }
        Optional<PitExitPlanner.Plan> pit = PitRescue.trapped(spirit);
        if (pit.isPresent() && pit.orElseThrow().possible()) {
            PitExitPlanner.Plan exit = pit.orElseThrow();
            return new Escape(
                    true,
                    new FallbackNode(List.of(PitRescue.plan(spirit, exit), digOut(exit.exit()))),
                    "яма: " + exit.why());
        }
        BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        PillarDescent.@Nullable Plan descent = ownPillar(level, spirit, at);
        boolean descend = descent != null && noPath;
        if (pit.isPresent() && !descend) {
            PitExitPlanner.Plan exit = pit.orElseThrow();
            return new Escape(false, null, "яма без выхода: " + exit.why());
        }
        boolean tool = PickaxeCheck.has(spirit);
        boolean open = DigOutScript.open(level, feet);
        int blocks = PitRescue.supplies(spirit).blocks();
        RescuePlans.Self method =
                RescuePlans.self(new RescuePlans.Situation(false, false, tool, open, noPath, blocks, descend));
        return switch (method) {
            case PIT ->
                throw new IllegalStateException("Яма без плана выхода: PitRescue обязан назвать способ подъёма");
            case WALK ->
                new Escape(
                        true, new FallbackNode(List.of(gotoPoint(home), digOut(home))), "путь домой есть, идёт пешком");
            case DIG -> dig(home);
            case BRIDGE ->
                new Escape(
                        true,
                        new FallbackNode(List.of(
                                RescuePlans.bridge(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), home, blocks),
                                digOut(home))),
                        "пути домой нет, кирки нет: мостит к поселению своими блоками (" + blocks + ")");
            case DESCEND -> {
                PillarDescent.Plan plan = Objects.requireNonNull(descent, "Спуск без плана: духу нечего разбирать");
                yield new Escape(
                        true,
                        RescuePlans.descent(home),
                        "пути домой нет, но стоит на своём столбе: разбирает " + plan.depth()
                                + " блоков под собой и спускается на " + plan.landing());
            }
            case NONE -> new Escape(false, null, refusal(open, noPath, blocks));
        };
    }

    private static PillarDescent.@Nullable Plan ownPillar(ServerLevel level, SpiritEntity spirit, BlockCoord at) {
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        return PillarDescent.plan(
                        new RouteLevelView(spirit),
                        at,
                        cell -> placed.isOwn(new BlockPos(cell.x(), cell.y(), cell.z())),
                        64)
                .orElse(null);
    }

    private static Escape dig(BlockCoord home) {
        return new Escape(true, RescuePlans.digToHome(home), "пути нет, копает к дому");
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
