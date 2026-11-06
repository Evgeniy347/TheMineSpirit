package com.theminespirit.core.rest;

import com.theminespirit.core.board.graph.GraphDecisions;

@FunctionalInterface
public interface GraphGateway {
    GraphDecisions decisions();
}
