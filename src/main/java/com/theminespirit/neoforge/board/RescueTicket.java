package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.movement.PitEscape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        Optional<PitEscape.Exit> pit = PitRescue.trapped(spirit);
        int blocks = spirit.getInventory().countItem(Items.DIRT)
                + spirit.getInventory().countItem(Items.COBBLESTONE);
        boolean steps = pit.map(exit -> blocks >= exit.rise()).orElse(false);
        boolean tool = canDig(spirit);
        boolean open = DigOutScript.open(level, feet);
        RescuePlans.Self method =
                RescuePlans.self(new RescuePlans.Situation(pit.isPresent(), steps, tool, open, noPath));
        return switch (method) {
            case PIT -> {
                PitEscape.Exit exit = pit.orElseThrow();
                BlockCoord rim = new BlockCoord(
                        feet.getX() + exit.rimDx(), feet.getY() + exit.rise() + 1, feet.getZ() + exit.rimDz());
                yield new Escape(
                        true,
                        new FallbackNode(List.of(PitRescue.plan(spirit, exit), digOut(rim))),
                        "яма: " + (steps ? "поднимается столбом" : "прокопает край"));
            }
            case WALK ->
                new Escape(
                        true, new FallbackNode(List.of(gotoPoint(home), digOut(home))), "путь домой есть, идёт пешком");
            case DIG -> dig(level, feet, home, open);
            case NONE -> new Escape(false, null, refusal(pit, open, noPath));
        };
    }

    private static Escape dig(ServerLevel level, BlockPos feet, BlockCoord home, boolean open) {
        BlockCoord goal = open ? home : SurfacePoint.of(level, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()));
        if (goal.equals(home)) {
            return new Escape(true, digOut(home), "пути нет, копает к дому");
        }
        return new Escape(
                true, new FallbackNode(List.of(digOut(goal), gotoPoint(home))), "под землёй, копает на поверхность");
    }

    private static String refusal(Optional<PitEscape.Exit> pit, boolean open, boolean noPath) {
        if (pit.isPresent()) {
            return "в яме нужны блоки-ступени (" + pit.orElseThrow().rise() + ") или кирка";
        }
        if (!open) {
            return "под землёй нужна кирка, чтобы выйти";
        }
        if (noPath) {
            return "пути домой нет, а кирки, чтобы прокопать, нет";
        }
        return "выход не найден: нет ступеней и кирки";
    }

    private static boolean canDig(SpiritEntity spirit) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (pickaxe(spirit.getInventory().getItem(slot))) {
                return true;
            }
        }
        return spirit.mainHandOwned() && pickaxe(spirit.getMainHandItem());
    }

    private static boolean pickaxe(ItemStack stack) {
        return !stack.isEmpty()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_pickaxe");
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall(GOTO, Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static ScriptCall digOut(BlockCoord goal) {
        return new ScriptCall(DIG_OUT, Map.of("x", goal.x(), "y", goal.y(), "z", goal.z()));
    }
}
