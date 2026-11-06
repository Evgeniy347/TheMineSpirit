package com.theminespirit.core.rest;

import com.theminespirit.core.board.StageView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FakeSettlementGateway implements SettlementGateway {
    private StageView view = new StageView(1, 3, Map.of(), List.of());
    private final List<SettlementChange> changes = new ArrayList<>();

    void with(StageView value) {
        view = value;
    }

    List<SettlementChange> changes() {
        return List.copyOf(changes);
    }

    @Override
    public StageView view() {
        return view;
    }

    @Override
    public SettlementStageChange rollback(SettlementChange change) {
        changes.add(change);
        return new SettlementStageChange(
                2, change.stage(), true, List.of("building:BUILD_MINE:1"), List.of("graph:building:plaza"));
    }
}
