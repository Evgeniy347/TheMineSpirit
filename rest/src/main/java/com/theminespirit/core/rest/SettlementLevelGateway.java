package com.theminespirit.core.rest;

import java.util.Optional;

public interface SettlementLevelGateway {
    Optional<SettlementLevelView> view();

    SettlementLevelPreview preview();

    SettlementLevelChangeResult set(SettlementLevelChange change);
}
