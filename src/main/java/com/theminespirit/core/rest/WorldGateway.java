package com.theminespirit.core.rest;

import com.theminespirit.core.board.graph.WorldReport;

@FunctionalInterface
public interface WorldGateway {
    WorldReport view();
}
