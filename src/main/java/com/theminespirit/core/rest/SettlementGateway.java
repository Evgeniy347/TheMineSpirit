package com.theminespirit.core.rest;

import com.theminespirit.core.board.StageView;

public interface SettlementGateway {
    StageView view();

    SettlementStageChange rollback(SettlementChange change);
}
