package com.theminespirit.core.board;

import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChestClearance {
    public static final int CHEST_PLANKS = 8;

    public enum Action {
        KEEP,
        REMOVE,
        DRAIN,
        ADD_CHEST,
        WAIT
    }

    public record Move(String item, int count) {
        public Move {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("Предмет переноса не назван: без id нечего нести");
            }
            if (count < 1) {
                throw new IllegalArgumentException(
                        "Перенос " + item + " на " + count + " штук: пустой перенос не двигает духа");
            }
        }
    }

    public record Decision(Action action, List<Move> moves, int neededSlots, int freeSlots, String reason) {
        public Decision {
            Objects.requireNonNull(action, "Решение по сундуку не может быть null: без него не выбрать действие");
            moves = List.copyOf(Objects.requireNonNull(moves, "Переносы не могут быть null: пустые — пустой список"));
            if (neededSlots < 0 || freeSlots < 0) {
                throw new IllegalArgumentException(
                        "Слоты " + neededSlots + "/" + freeSlots + ": места не бывает отрицательного, пустое — ноль");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Причина решения по сундуку пуста: без неё не объяснить перенос");
            }
        }

        public boolean breaks() {
            return action == Action.REMOVE || action == Action.DRAIN;
        }
    }

    private ChestClearance() {}

    public static Decision decide(
            ContainerSnapshot chest,
            boolean inWarehousePlan,
            List<ContainerSnapshot> warehouse,
            List<BlockCoord> emptyWarehouseCells,
            int planks) {
        Objects.requireNonNull(chest, "Сундук не может быть null: по нему решают, что с ним делать");
        Objects.requireNonNull(warehouse, "Сундуки склада не могут быть null: пустые задают пустым списком");
        Objects.requireNonNull(
                emptyWarehouseCells, "Свободные клетки склада не могут быть null: пустые задают пустым списком");
        if (planks < 0) {
            throw new IllegalArgumentException("Досок " + planks + " на сундук: количество не бывает отрицательным");
        }
        if (inWarehousePlan) {
            return new Decision(Action.KEEP, List.of(), 0, 0, "сундук стоит в клетке плана склада — не трогаем");
        }
        if (empty(chest)) {
            return new Decision(Action.REMOVE, List.of(), 0, 0, "сундук пуст — ломаем");
        }
        int needed = neededSlots(chest, warehouse);
        int free = freeSlots(warehouse);
        List<Move> moves = moves(chest);
        if (free >= needed) {
            return new Decision(
                    Action.DRAIN,
                    moves,
                    needed,
                    free,
                    "в складе свободно " + free + " слотов, нужно " + needed + " — сначала вынести вещи");
        }
        if (!emptyWarehouseCells.isEmpty() && planks >= CHEST_PLANKS) {
            return new Decision(
                    Action.ADD_CHEST,
                    moves,
                    needed,
                    free,
                    "в складе не хватает места: свободно " + free + " из " + needed
                            + " слотов — сначала ставим сундук в клетку склада");
        }
        return new Decision(
                Action.WAIT,
                moves,
                needed,
                free,
                "склад полон и нового сундука поставить не из чего: свободно " + free + " из " + needed
                        + " слотов, досок " + planks);
    }

    public static boolean empty(ContainerSnapshot chest) {
        Objects.requireNonNull(chest, "Сундук не может быть null: пустоту видно по его слотам");
        for (Stack stack : chest.slots()) {
            if (stack != null) {
                return false;
            }
        }
        return true;
    }

    public static List<Move> moves(ContainerSnapshot chest) {
        Objects.requireNonNull(chest, "Сундук не может быть null: без него нечего выносить");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Stack stack : chest.slots()) {
            if (stack != null) {
                counts.merge(stack.item(), stack.count(), Integer::sum);
            }
        }
        List<Move> moves = new ArrayList<>(counts.size());
        counts.forEach((item, count) -> moves.add(new Move(item, count)));
        return List.copyOf(moves);
    }

    public static int neededSlots(ContainerSnapshot chest, List<ContainerSnapshot> warehouse) {
        Objects.requireNonNull(chest, "Сундук не может быть null: без него нечего выносить");
        Objects.requireNonNull(warehouse, "Сундуки склада не могут быть null: по ним считают место");
        Map<Kind, Integer> counts = new LinkedHashMap<>();
        for (Stack stack : chest.slots()) {
            if (stack != null) {
                counts.merge(new Kind(stack.item(), stack.variant(), stack.maxStack()), stack.count(), Integer::sum);
            }
        }
        int needed = 0;
        for (Map.Entry<Kind, Integer> entry : counts.entrySet()) {
            Kind kind = entry.getKey();
            int free = 0;
            for (ContainerSnapshot container : warehouse) {
                for (Stack stack : container.slots()) {
                    if (stack != null && kind.matches(stack)) {
                        free += stack.freeSpace();
                    }
                }
            }
            int remaining = Math.max(0, entry.getValue() - free);
            needed += (remaining + kind.maxStack() - 1) / kind.maxStack();
        }
        return needed;
    }

    public static int freeSlots(List<ContainerSnapshot> warehouse) {
        Objects.requireNonNull(warehouse, "Сундуки склада не могут быть null: по ним считают место");
        int free = 0;
        for (ContainerSnapshot container : warehouse) {
            free += container.freeSlots();
        }
        return free;
    }

    private record Kind(String item, int variant, int maxStack) {
        Kind {
            if (maxStack < 1) {
                throw new IllegalArgumentException("Максимум стопки " + item + " равен " + maxStack
                        + ": слот обязан вмещать хотя бы один предмет");
            }
        }

        boolean matches(Stack stack) {
            return item.equals(stack.item()) && variant == stack.variant();
        }
    }
}
