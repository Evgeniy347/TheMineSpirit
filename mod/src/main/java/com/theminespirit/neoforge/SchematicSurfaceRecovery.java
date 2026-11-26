package com.theminespirit.neoforge;

import com.theminespirit.core.board.StandSearch;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class SchematicSurfaceRecovery {
    private final BuildSchematicScript host;
    private @Nullable BlockCoord stand;

    SchematicSurfaceRecovery(BuildSchematicScript host) {
        this.host = host;
    }

    Optional<ScriptStatus> tick() {
        if (!SchematicGround.tilled(host.site.schematic())
                || host.feet().y() >= host.site.origin().y()) {
            stand = null;
            return Optional.empty();
        }
        if (stand == null || !host.approach.isActive()) {
            host.approach.cancel();
            host.approachStand = null;
            BlockCoord top = new BlockCoord(
                    host.feet().x(), host.site.origin().y(), host.feet().z());
            BlockCoord surface = StandSearch.nearest(
                    top, cell -> cell.y() >= host.site.origin().y() && host.site.standable(cell));
            if (surface.y() < host.site.origin().y() || !host.site.standable(surface)) {
                stand = null;
                return Optional.of(host.report.fail("нет безопасной стоянки на поверхности пашни у " + top));
            }
            stand = surface;
            host.beginApproach(surface);
            return Optional.of(ScriptStatus.RUNNING);
        }
        return Optional.of(approachTick());
    }

    private ScriptStatus approachTick() {
        switch (host.approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                host.approach.cancel();
                host.approachStand = null;
                stand = null;
                return ScriptStatus.RUNNING;
            }
            case STUCK, TIMEOUT -> {
                String reason = "не удалось выйти на поверхность пашни: путь заблокирован или слишком далеко"
                        + (host.approach.refusal() == null ? "" : " (" + host.approach.refusal() + ")");
                host.approach.cancel();
                host.approachStand = null;
                stand = null;
                return host.report.fail(reason);
            }
        }
        return ScriptStatus.RUNNING;
    }
}
