package com.theminespirit.neoforge;

import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.GatherParams;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class GatherScript implements ScriptInstance {
    private static final String DIRT_PATH = "dirt_path";

    private final @Nullable HarvestSession session;
    private final @Nullable TramplePathScript paths;

    GatherScript(SpiritEntity spirit, GatherParams params) {
        if (DIRT_PATH.equals(SchematicBlocks.path(params.block()))) {
            this.session = null;
            this.paths = new TramplePathScript(spirit, params.count(), params.radius());
        } else {
            this.session = HarvestSession.gather(spirit, params.block(), params.count(), params.radius());
            this.paths = null;
        }
    }

    @Override
    public ScriptStatus tick() {
        if (paths != null) {
            return paths.tick();
        }
        return switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> ScriptStatus.FAILED;
        };
    }

    @Override
    public Optional<Progress> progress() {
        return paths != null ? paths.progress() : Optional.of(session.progress());
    }

    @Override
    public void cancel() {
        if (paths != null) {
            paths.cancel();
            return;
        }
        session.cancel();
    }

    @Override
    public void suspend() {
        if (paths != null) {
            paths.suspend();
            return;
        }
        session.suspend();
    }

    @Override
    public String failureReason() {
        return paths != null ? paths.failureReason() : session.failureReason();
    }

    @Override
    public Map<String, Object> saveState() {
        return paths != null ? Map.of() : session.saveState();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (paths == null) {
            session.restoreState(state);
        }
    }
}
