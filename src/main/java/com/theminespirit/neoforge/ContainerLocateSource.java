package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ContainerRef;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.perception.ScanFocus;
import java.util.List;

final class ContainerLocateSource implements LocateSource {
    private final WorldScanner.Session session;

    ContainerLocateSource(SpiritEntity spirit, BlockCoord center, ScanRadius radius) {
        this.session = new WorldScanner.Session(spirit.level(), spirit.getUUID().toString(), center, radius,
                IdFilter.matchAll(), spirit.getInventory(), ScanFocus.CONTAINERS);
    }

    @Override
    public List<BlockCoord> advance() {
        session.step(ScanTuning.SLICES_PER_TICK);
        if (!session.done()) {
            return List.of();
        }
        return session.result().observation().containers().stream().map(ContainerRef::pos).toList();
    }

    @Override
    public boolean done() {
        return session.done();
    }

    @Override
    public int examinedPositions() {
        return session.examinedPositions();
    }
}
