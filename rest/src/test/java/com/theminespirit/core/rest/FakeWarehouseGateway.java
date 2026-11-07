package com.theminespirit.core.rest;

import com.theminespirit.core.board.WarehouseOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FakeWarehouseGateway implements WarehouseGateway {
    private WarehouseView view = new WarehouseView(2, 2, 0, true, null, Map.of("oak_planks", 40));
    private final List<WarehouseDecision> decisions = new ArrayList<>();

    void with(WarehouseView value) {
        view = value;
    }

    List<WarehouseDecision> decisions() {
        return List.copyOf(decisions);
    }

    @Override
    public WarehouseView view() {
        return view;
    }

    @Override
    public WarehouseChange decide(WarehouseDecision decision) {
        decisions.add(decision);
        return new WarehouseChange(
                view.order(),
                new WarehouseOrder(decision.level(), decision.site(), decision.by(), decision.reason(), 1),
                true);
    }
}
