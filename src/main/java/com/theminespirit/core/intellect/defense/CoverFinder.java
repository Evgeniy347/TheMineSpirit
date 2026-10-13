package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;

public final class CoverFinder {
    private static final double TIE = 1e-9;

    private CoverFinder() {
    }

    public static Optional<Point> between(Point self, Point threat, List<Point> blockers, double corridor) {
        if (self == null) {
            throw new IllegalArgumentException("self не может быть null: линия укрытия строится от духа");
        }
        if (threat == null) {
            throw new IllegalArgumentException("threat не может быть null: линия укрытия строится до угрозы");
        }
        if (blockers == null) {
            throw new IllegalArgumentException("blockers не может быть null: нет кандидатов — пустой список");
        }
        if (!Double.isFinite(corridor)) {
            throw new IllegalArgumentException(
                    "corridor = " + corridor + ": ширина коридора обязана быть конечным числом");
        }
        double dx = threat.x() - self.x();
        double dy = threat.y() - self.y();
        double dz = threat.z() - self.z();
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared == 0) {
            return Optional.empty();
        }
        Point best = null;
        double bestAcross = 0;
        double bestAlong = 0;
        for (Point blocker : blockers) {
            if (blocker == null) {
                throw new IllegalArgumentException("blockers содержит null: кандидат без позиции не проверяется");
            }
            double rx = blocker.x() - self.x();
            double ry = blocker.y() - self.y();
            double rz = blocker.z() - self.z();
            double projection = (rx * dx + ry * dy + rz * dz) / lengthSquared;
            if (projection < 0 || projection > 1) {
                continue;
            }
            double acrossSquared = (rx * rx + ry * ry + rz * rz) - projection * projection * lengthSquared;
            double across = Math.sqrt(Math.max(0, acrossSquared));
            if (across > corridor) {
                continue;
            }
            double along = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (best == null || across < bestAcross - TIE || (Math.abs(across - bestAcross) <= TIE
                    && (along < bestAlong - TIE
                            || (Math.abs(along - bestAlong) <= TIE && before(blocker, best))))) {
                best = blocker;
                bestAcross = across;
                bestAlong = along;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean before(Point candidate, Point best) {
        if (candidate.x() != best.x()) {
            return candidate.x() < best.x();
        }
        if (candidate.y() != best.y()) {
            return candidate.y() < best.y();
        }
        return candidate.z() < best.z();
    }
}
