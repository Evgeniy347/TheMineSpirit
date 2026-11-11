package com.theminespirit.core.board.lease;

import com.theminespirit.core.movement.BodyMotion;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class LeaseUndo {
    public record Step(
            BlockCoord pos, LeaseKind kind, @Nullable String before) {
        public Step {
            Objects.requireNonNull(pos, "Клетка отката не может быть null: ломать нечего");
            Objects.requireNonNull(kind, "Вид отката не может быть null: от него зависит действие");
        }
    }

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private LeaseUndo() {}

    public static String key(Lease lease) {
        Objects.requireNonNull(lease, "Аренда не может быть null: без неё ключ отката не построить");
        long hash = FNV_OFFSET;
        for (Step step : order(lease)) {
            hash = mix(hash, step.pos().x());
            hash = mix(hash, step.pos().y());
            hash = mix(hash, step.pos().z());
            hash = mixText(hash, step.kind().name());
            hash = mixText(hash, step.before());
        }
        return Long.toHexString(hash);
    }

    private static long mix(long hash, int value) {
        for (int shift = 0; shift < 32; shift += 8) {
            hash ^= (value >>> shift) & 0xffL;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static long mixText(long hash, @Nullable String text) {
        if (text == null) {
            return mix(hash, -1);
        }
        for (int at = 0; at < text.length(); at++) {
            hash = mix(hash, text.charAt(at));
        }
        return mix(hash, -2);
    }

    public static List<Step> order(Lease lease) {
        Objects.requireNonNull(lease, "Аренда не может быть null: откатывать нечего");
        List<LeaseCell> cells = new ArrayList<>(lease.rollbackOrder());
        cells.sort(Comparator.comparingInt((LeaseCell cell) -> -cell.pos().y()));
        List<Step> steps = new ArrayList<>(cells.size());
        for (LeaseCell cell : cells) {
            steps.add(new Step(cell.pos(), cell.kind(), cell.before()));
        }
        return List.copyOf(steps);
    }

    public static boolean nothingToDo(Step step, boolean free, String current) {
        Objects.requireNonNull(step, "Клетка отката не может быть null: сравнивать нечего");
        Objects.requireNonNull(current, "Текущий блок не может быть null: без него не видно, откатано ли");
        if (step.kind() == LeaseKind.PLACED) {
            return free;
        }
        return current.equals(step.before());
    }

    public static boolean supportsBody(Step step, BlockCoord feet) {
        Objects.requireNonNull(step, "Клетка отката не может быть null: опору ищут по ней");
        Objects.requireNonNull(feet, "Клетка ног не может быть null: без неё опоры под телом нет");
        return step.kind() == LeaseKind.PLACED && step.pos().equals(BodyMotion.below(feet));
    }

    public static boolean breaksNow(Step step, BlockCoord feet, RouteWorld world, Set<BlockCoord> removed) {
        if (!supportsBody(step, feet)) {
            return true;
        }
        RouteWorld after = after(world, step.pos(), removed);
        BlockCoord landing = BodyMotion.below(feet);
        return BodyMotion.fits(after, landing) && BodyMotion.solid(after, BodyMotion.below(landing));
    }

    private static RouteWorld after(RouteWorld world, BlockCoord removed, Set<BlockCoord> rolledBack) {
        return (x, y, z) -> {
            BlockCoord cell = new BlockCoord(x, y, z);
            if (cell.equals(removed) || rolledBack.contains(cell)) {
                return RouteCell.AIR;
            }
            return world.cell(x, y, z);
        };
    }
}
