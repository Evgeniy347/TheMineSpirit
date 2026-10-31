package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class ContainerPicker {
    private ContainerPicker() {}

    public static ContainerChoice choose(List<ContainerSnapshot> containers, BlockCoord origin, Stack incoming) {
        return choose(containers, origin, incoming, 0);
    }

    public static ContainerChoice choose(
            List<ContainerSnapshot> containers, BlockCoord origin, Stack incoming, int minFreeSlotsPercent) {
        Objects.requireNonNull(containers, "Список контейнеров не может быть null: выбирать не из чего");
        Objects.requireNonNull(origin, "Позиция духа не может быть null: без неё не выбрать ближайший контейнер");
        Objects.requireNonNull(incoming, "Предмет не может быть null: без него непонятно, что докладывать");
        String category = DataTables.INSTANCE.warehouseCategories().current().categoryOf(incoming.item());
        int own = nearestMatching(
                containers,
                origin,
                container -> category.equals(container.category())
                        && (container.hasPartial(incoming)
                                || WarehouseSpace.canTakeNewSlot(container, minFreeSlotsPercent)));
        if (own >= 0) {
            return new ContainerChoice.Chosen(own);
        }
        int partial = nearestMatching(containers, origin, container -> container.hasPartial(incoming));
        if (partial >= 0) {
            return new ContainerChoice.Chosen(partial);
        }
        int present = nearestMatching(
                containers,
                origin,
                container ->
                        WarehouseSpace.canTakeNewSlot(container, minFreeSlotsPercent) && container.hasKind(incoming));
        if (present >= 0) {
            return new ContainerChoice.Chosen(present);
        }
        int free = nearestMatching(
                containers, origin, container -> WarehouseSpace.canTakeNewSlot(container, minFreeSlotsPercent));
        if (free >= 0) {
            return new ContainerChoice.Chosen(free);
        }
        return new ContainerChoice.Refused(refusal(containers, incoming));
    }

    private static String refusal(List<ContainerSnapshot> containers, Stack incoming) {
        if (containers.isEmpty()) {
            return "на складе поселения нет допустимого контейнера: некуда положить '" + incoming.item()
                    + "'. Проверьте, что в поселении есть сундук, бочка или шалкер-бокс";
        }
        int withKind = 0;
        StringBuilder slots = new StringBuilder();
        for (ContainerSnapshot container : containers) {
            if (container.hasKind(incoming)) {
                withKind++;
            }
            if (slots.length() > 0) {
                slots.append("; ");
            }
            slots.append(coord(container.pos()))
                    .append(" — свободно ")
                    .append(container.freeSlots())
                    .append(" из ")
                    .append(container.slotCount());
        }
        return "во всех " + containers.size() + " контейнерах склада нет свободного слота под '"
                + incoming.item() + "': ни одного пустого слота, неполных стопок нет, контейнеров с таким "
                + "предметом " + withKind + " [" + slots + "]. Освободите место, поставьте сундук "
                + "или закажите его на доске";
    }

    private static int nearestMatching(
            List<ContainerSnapshot> containers, BlockCoord origin, Predicate<ContainerSnapshot> test) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 0; index < containers.size(); index++) {
            ContainerSnapshot container = containers.get(index);
            if (!test.test(container)) {
                continue;
            }
            long distance = distanceSqr(origin, container.pos());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    private static String coord(BlockCoord pos) {
        return pos.x() + "," + pos.y() + "," + pos.z();
    }

    private static long distanceSqr(BlockCoord from, BlockCoord to) {
        long dx = (long) from.x() - to.x();
        long dy = (long) from.y() - to.y();
        long dz = (long) from.z() - to.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
