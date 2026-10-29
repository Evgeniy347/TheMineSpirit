package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class StoragePoints {

    public record Spot(BlockCoord pos, String type, ContainerSnapshot snapshot) {
        public Spot {
            Objects.requireNonNull(pos, "Позиция контейнера не может быть null: по ней дух ищет точку");
            Objects.requireNonNull(type, "Тип контейнера не может быть null: в лог пишется, что нашли");
            Objects.requireNonNull(snapshot, "Снимок контейнера не может быть null: по нему видно место");
        }
    }

    public record Choice(
            @Nullable BlockCoord point, @Nullable String type, String log) {
        public Choice {
            Objects.requireNonNull(log, "Строка выбора не может быть null: без неё провал не объяснить");
        }

        public boolean found() {
            return point != null;
        }
    }

    private StoragePoints() {}

    public static Choice deposit(List<Spot> spots, BlockCoord origin, Stack incoming, int radius) {
        Objects.requireNonNull(spots, "Список контейнеров не может быть null: пустой задают пустым списком");
        Objects.requireNonNull(origin, "Позиция духа не может быть null: без неё не выбрать ближайший контейнер");
        Objects.requireNonNull(incoming, "Предмет не может быть null: без него непонятно, что сдавать");
        List<ContainerSnapshot> snapshots = spots.stream().map(Spot::snapshot).toList();
        ContainerChoice choice = ContainerPicker.choose(snapshots, origin, incoming);
        if (choice instanceof ContainerChoice.Chosen chosen) {
            Spot spot = spots.get(chosen.index());
            return new Choice(
                    spot.pos(), spot.type(), describe("точка сдачи", spots, origin, radius, incoming.item(), spot));
        }
        String reason = ((ContainerChoice.Refused) choice).reason();
        return new Choice(
                null,
                null,
                "точка сдачи не выбрана: " + reason + "; "
                        + describe("точка сдачи", spots, origin, radius, incoming.item(), null));
    }

    public static Choice withdraw(List<Spot> spots, BlockCoord origin, String item, int count, int radius) {
        Objects.requireNonNull(spots, "Список контейнеров не может быть null: пустой задают пустым списком");
        Objects.requireNonNull(origin, "Позиция духа не может быть null: без неё не выбрать ближайший контейнер");
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Предмет еды не назван: без него нечего искать в контейнерах");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("Количество еды " + count + ": берут хотя бы один предмет");
        }
        Spot best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Spot spot : spots) {
            if (count(spot.snapshot(), item) <= 0) {
                continue;
            }
            long distance = distanceSqr(origin, spot.pos());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = spot;
            }
        }
        String head = "точка еды: нужно '" + item + "' ×" + count;
        if (best == null) {
            return new Choice(
                    null,
                    null,
                    head + "; не выбрана: ни в одном контейнере нет '" + item + "'; "
                            + describe("осмотр", spots, origin, radius, item, null));
        }
        return new Choice(best.pos(), best.type(), head + "; " + describe("выбор", spots, origin, radius, item, best));
    }

    public static int count(ContainerSnapshot snapshot, String item) {
        Objects.requireNonNull(snapshot, "Снимок контейнера не может быть null: по нему считают предметы");
        int total = 0;
        for (Stack stack : snapshot.slots()) {
            if (stack != null && stack.item().equals(item)) {
                total += stack.count();
            }
        }
        return total;
    }

    private static String describe(
            String title, List<Spot> spots, BlockCoord origin, int radius, String item, @Nullable Spot chosen) {
        List<String> parts = new ArrayList<>();
        for (Spot spot : spots) {
            String verdict = spot.equals(chosen) ? "выбрана" : "не подходит под '" + item + "'";
            parts.add(coord(spot.pos()) + " " + spot.type() + " — " + verdict);
        }
        return title + " от " + origin + ", радиус " + radius + ": [" + String.join("; ", parts) + "]";
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
