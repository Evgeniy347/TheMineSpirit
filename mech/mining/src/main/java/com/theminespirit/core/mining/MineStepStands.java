package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class MineStepStands {
    public static final int MAX_STANDS = 6;
    public static final int MAX_ALTERNATIVES = 64;

    public record Choice(List<BlockCoord> stands, String reason) {
        public Choice {
            stands = List.copyOf(
                    Objects.requireNonNull(stands, "Стоянки не могут быть null: пустой список — стоянки нет"));
            Objects.requireNonNull(reason, "Причина не может быть null: без неё дух не скажет, почему не подошёл");
        }

        public boolean found() {
            return !stands.isEmpty();
        }
    }

    private MineStepStands() {}

    public static Choice of(MineColumn column, @Nullable BlockCoord parentStand, BlockCoord from, SafetyView view) {
        Objects.requireNonNull(column, "Колонка не может быть null: без неё неизвестно, к какой ступени идти");
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней сортируются стоянки");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяется стоянка и видимость");
        Set<BlockCoord> reachable = MineReach.stands(from, view);
        Set<BlockCoord> stands = new LinkedHashSet<>();
        consider(column, parentStand, view, reachable, stands);
        for (BlockCoord stand : StandSpots.of(column.stand(), view, from)) {
            consider(column, stand, view, reachable, stands);
            if (stands.size() >= MAX_STANDS) {
                break;
            }
        }
        if (stands.isEmpty()) {
            addReachable(column, from, view, reachable, stands);
        }
        if (stands.isEmpty()) {
            addVisible(column, from, view, stands);
        }
        if (stands.isEmpty()) {
            return new Choice(List.of(), reason(column, parentStand, view, reachable));
        }
        return new Choice(new ArrayList<>(stands), "");
    }

    private static void consider(
            MineColumn column,
            @Nullable BlockCoord stand,
            SafetyView view,
            Set<BlockCoord> reachable,
            Set<BlockCoord> stands) {
        if (stand == null || shaft(column, stand) || !reachable.contains(stand)) {
            return;
        }
        if (!MiningSafety.canStand(stand, view) || !visibleFrom(column, stand, view)) {
            return;
        }
        stands.add(stand);
    }

    private static void addReachable(
            MineColumn column, BlockCoord from, SafetyView view, Set<BlockCoord> reachable, Set<BlockCoord> stands) {
        List<BlockCoord> ordered = new ArrayList<>(reachable);
        ordered.sort(Comparator.comparingLong((BlockCoord stand) -> distanceSquared(from, stand))
                .thenComparing(Comparator.naturalOrder()));
        int scanned = 0;
        for (BlockCoord stand : ordered) {
            if (stands.size() >= MAX_STANDS || scanned++ >= MAX_ALTERNATIVES) {
                return;
            }
            consider(column, stand, view, reachable, stands);
        }
    }

    private static void addVisible(MineColumn column, BlockCoord from, SafetyView view, Set<BlockCoord> stands) {
        for (BlockCoord stand : StandSpots.of(column.stand(), view, from)) {
            if (stands.size() >= MAX_STANDS) {
                return;
            }
            if (!shaft(column, stand) && MiningSafety.canStand(stand, view) && visibleFrom(column, stand, view)) {
                stands.add(stand);
            }
        }
    }

    public static boolean shaft(MineColumn column, BlockCoord at) {
        Objects.requireNonNull(column, "Колонка не может быть null: по ней проверяется клетка прохода");
        Objects.requireNonNull(at, "Клетка не может быть null: её проверяют на принадлежность проходу");
        BlockCoord floor = column.floor();
        return at.x() == floor.x() && at.z() == floor.z();
    }

    public static boolean visibleFrom(MineColumn column, BlockCoord feet, SafetyView view) {
        Objects.requireNonNull(column, "Колонка не может быть null: без неё неизвестно, что видно ломать");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: с них проверяется видимость клетки");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяется проход луча");
        List<BlockCoord> cells = column.cells();
        for (int index = cells.size() - 1; index >= 0; index--) {
            if (MineDigOrder.visible(feet, cells.get(index), List.of(), view)) {
                return true;
            }
        }
        return MineDigOrder.visible(feet, column.floor(), List.of(), view);
    }

    private static String reason(
            MineColumn column, @Nullable BlockCoord parentStand, SafetyView view, Set<BlockCoord> reachable) {
        BlockCoord stand = column.stand();
        if (parentStand != null && !reachable.contains(parentStand)) {
            return "стоянка " + parentStand + " за преградой: к ступени " + stand
                    + " нет достижимой клетки с прямой видимостью — расчистить подход";
        }
        if (!MiningSafety.canStand(stand, view)) {
            return "клетка ступени " + stand
                    + " не годится для стоянки: над ней нет прохода или под ней нет пола — вход завален";
        }
        if (parentStand != null) {
            return "ступень выше " + parentStand + " не годится для стоянки, а вокруг ступени " + stand
                    + " нет свободной клетки с прямой видимостью: преграда у входа";
        }
        return "у входа нет свободной клетки с прямой видимостью на ступень " + stand + ": вход завален или загорожен";
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
