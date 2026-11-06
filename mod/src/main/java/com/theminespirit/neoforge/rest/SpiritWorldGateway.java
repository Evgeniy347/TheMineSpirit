package com.theminespirit.neoforge.rest;

import com.theminespirit.core.board.graph.WorldReport;
import com.theminespirit.core.rest.WorldGateway;
import com.theminespirit.neoforge.board.WorldFactsStore;

public final class SpiritWorldGateway implements WorldGateway {
    @Override
    public WorldReport view() {
        return WorldFactsStore.get();
    }
}
