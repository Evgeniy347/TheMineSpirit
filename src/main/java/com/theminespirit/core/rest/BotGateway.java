package com.theminespirit.core.rest;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import java.util.List;
import java.util.Optional;

public interface BotGateway {
    List<BotInfo> bots();

    Optional<BotInfo> bot(String id);

    WorldObservation observe(String id, ObserveRequest request);

    List<DecisionRecord> decisions(String id, int limit);
}
