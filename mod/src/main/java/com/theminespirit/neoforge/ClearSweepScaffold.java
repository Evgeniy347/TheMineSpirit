package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

final class ClearSweepScaffold {
    private final ClearAreaSession session;
    private final Runnable afterDismantle;
    private @Nullable SweepPillar pillar;
    private @Nullable PillarDismantler dismantler;
    private boolean finishAfterDescent;

    ClearSweepScaffold(ClearAreaSession session, Runnable afterDismantle) {
        this.session = session;
        this.afterDismantle = afterDismantle;
    }

    void pillarTick() {
        SweepPillar current = pillar;
        if (current == null) {
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        switch (current.tick()) {
            case RUNNING -> {}
            case PLACED -> session.state = ClearAreaSession.State.PICK;
            case FAILED -> session.fail(current.failureReason(), current.failureFact());
        }
    }

    void descentTick() {
        PillarDismantler current = dismantler;
        if (current == null) {
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        switch (current.tick()) {
            case RUNNING -> {}
            case DONE -> {
                dismantler = null;
                pillar = null;
                afterDismantle.run();
                if (finishAfterDescent) {
                    finishAfterDescent = false;
                    session.finish();
                    return;
                }
                session.state = ClearAreaSession.State.PICK;
            }
            case FAILED -> session.fail("разбор своего столба: " + current.failureReason());
        }
    }

    void startStep() {
        if (!SweepPillar.hasMaterial(session.spirit)) {
            session.neededMaterial = true;
            session.state = ClearAreaSession.State.PICK;
            return;
        }
        if (pillar == null) {
            pillar = SweepPillar.start(session.spirit);
        }
        session.state = ClearAreaSession.State.PILLAR;
    }

    boolean teardown() {
        if (dismantler != null || pillar == null || pillar.cells().isEmpty()) {
            return false;
        }
        finishAfterDescent = true;
        session.remaining.clear();
        startDescent();
        return true;
    }

    boolean canRise() {
        if (pillar != null && pillar.cells().size() >= ClearAnchor.MAX_RISE) {
            return false;
        }
        BlockPos feet = session.spirit.blockPosition();
        int reach = ClearStands.horizontalReach();
        for (BlockCoord cell : session.remaining) {
            if (cell.y() > feet.getY()
                    && Math.abs(cell.x() - feet.getX()) <= reach
                    && Math.abs(cell.z() - feet.getZ()) <= reach) {
                return true;
            }
        }
        return false;
    }

    void startDescent() {
        SweepPillar current = pillar;
        if (current == null) {
            dismantler = PillarDownScript.leftoverDismantler(session.spirit);
        } else {
            List<BlockCoord> order = new ArrayList<>(current.cells());
            order.sort(Comparator.comparingInt(BlockCoord::y).reversed());
            dismantler = new PillarDismantler(session.spirit, order, current.lease(), false);
        }
        session.state = dismantler == null ? ClearAreaSession.State.PICK : ClearAreaSession.State.DESCEND;
    }
}
