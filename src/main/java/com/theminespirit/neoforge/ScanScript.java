package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.perception.PerceptionQuery;
import com.theminespirit.core.perception.PerceptionResult;
import com.theminespirit.core.perception.PerceptionResultCodec;
import com.theminespirit.core.perception.ScanFocus;
import com.theminespirit.core.script.ScanParams;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public final class ScanScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final ScanFocus focus;
    private final ScanRadius radius;
    private final WorldScanner.Session session;
    private String failureReason;
    private PerceptionResult published;

    public ScanScript(SpiritEntity spirit, ScanParams params) {
        this(spirit, ScanFocus.ALL, params);
    }

    public ScanScript(SpiritEntity spirit, PerceptionQuery query) {
        this(spirit, ScanFocus.of(query.type()), new ScanParams(query.radius(), query.filter()));
    }

    private ScanScript(SpiritEntity spirit, ScanFocus focus, ScanParams params) {
        this.spirit = spirit;
        this.focus = focus;
        this.radius = params.radius();
        BlockPos position = spirit.blockPosition();
        this.session = new WorldScanner.Session(spirit.level(), spirit.getUUID().toString(),
                new BlockCoord(position.getX(), position.getY(), position.getZ()), params.radius(), params.filter(),
                spirit.getInventory(), focus);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            failureReason = "дух исчез во время скана";
            return ScriptStatus.FAILED;
        }
        session.step(ScanTuning.SLICES_PER_TICK);
        if (!session.done()) {
            return ScriptStatus.RUNNING;
        }
        WorldObservation observation = session.result().observation();
        focus.resultOf(observation, radius).ifPresentOrElse(result -> {
            published = result;
            spirit.updatePerceptionResult(result);
        }, () -> spirit.updateObservation(observation));
        return ScriptStatus.SUCCEEDED;
    }

    @Override
    public Optional<ScriptResult> result() {
        return published == null ? Optional.empty()
                : Optional.of(new ScriptResult.Data(PerceptionResultCodec.toMap(published)));
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    public int examinedPositions() {
        return session.examinedPositions();
    }

    public int stepCalls() {
        return session.stepCalls();
    }

    public int entityQueries() {
        return session.entityQueries();
    }
}
