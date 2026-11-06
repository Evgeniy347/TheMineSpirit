package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class HarvestReasonText {
    private HarvestReasonText() {}

    record Tally(
            int mined,
            int count,
            int unsafe,
            int noTool,
            int protectedSkipped,
            int unreachable,
            BreakSkipTally skips) {}

    static String stopReason(
            Tally tally,
            int radius,
            boolean vein,
            boolean gather,
            @Nullable BlockCoord lastUnreachable,
            @Nullable String tunnelRefusal) {
        String base = summary(tally) + "; подходящих блоков в радиусе " + radius + " не осталось";
        if (vein && tunnelRefusal != null) {
            return base + "; проход к руде не пройден: " + tunnelRefusal;
        }
        if (vein && tally.skips().count(BreakSkipTally.Reason.NO_STAND) > 0) {
            return base + "; руда закрыта, нужен проход: блоки руды есть, но нет места для духа — прокопайте "
                    + "проход к руде (dig_descent или dig_area) и повторите mine_vein";
        }
        if (gather && tally.mined() == 0 && lastUnreachable != null) {
            return "нет пути к блоку " + coordText(lastUnreachable) + ": " + base + "; " + pathCause(tally);
        }
        return base;
    }

    static String stallReason(
            Tally tally, int limitTicks, boolean searching, String step, @Nullable BlockCoord target) {
        String timing = "нет прогресса " + limitTicks + " тиков (" + (limitTicks / 20) + " с): " + summary(tally);
        if (searching) {
            return "нет пути к блоку " + coordText(target) + " — " + timing + "; " + pathCause(tally);
        }
        return timing + "; текущий шаг " + step + " — проверьте доступность блоков и подходящий инструмент";
    }

    static String pathCause(Tally tally) {
        BreakSkipTally skips = tally.skips();
        if (skips.count(BreakSkipTally.Reason.NO_STAND) > 0) {
            return "блок есть, но к нему нет места для стоянки духа — преграда вплотную к цели";
        }
        if (skips.count(BreakSkipTally.Reason.OUT_OF_REACH) > 0) {
            return "блок есть, но дух не дотянулся с выбранной стоянки — нужен другой подход";
        }
        if (skips.count(BreakSkipTally.Reason.NO_SIGHT) > 0) {
            return "блок есть, но не виден с выбранной стоянки — нужен другой подход";
        }
        if (skips.count(BreakSkipTally.Reason.CHUNK_NOT_LOADED) > 0) {
            return "блок есть, но чанк с ним не загружен — подойдите ближе";
        }
        if (tally.unreachable() > 0) {
            return "блок есть, но все стоянки у него недостижимы — путь перекрыт";
        }
        return "блок есть, но дух не смог к нему подойти: путь перекрыт или цель недостижима";
    }

    static String summary(Tally tally) {
        String counts = "добыто " + tally.mined() + " из " + tally.count()
                + "; пропущено небезопасных: " + tally.unsafe()
                + ", без подходящего инструмента: " + tally.noTool()
                + ", пропущено защищённых: " + tally.protectedSkipped();
        if (!tally.skips().text().isEmpty()) {
            counts += "; причины пропуска: " + tally.skips().text();
        }
        if (tally.unreachable() > 0) {
            counts += "; недостижимо: " + tally.unreachable();
        }
        return counts;
    }

    static String tierReason(BlockState state, String label) {
        String kind = RequiredTier.kindOf(state);
        String tier = RequiredTier.of(state);
        String need = kind.isEmpty() ? "нужен инструмент" : "нужен инструмент (" + kind + ")";
        if (tier.isEmpty()) {
            return need + " для " + label + ": подходящего в инвентаре нет или он изношен";
        }
        return need + " для " + label + ": " + tier + " или лучше в инвентаре нет или он изношен";
    }

    static String coordText(@Nullable BlockCoord target) {
        return target == null
                ? "(координата неизвестна)"
                : "(" + target.x() + ", " + target.y() + ", " + target.z() + ")";
    }
}
