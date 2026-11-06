package com.theminespirit.core.rest;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class FakeBotGateway implements BotGateway {
    private final List<BotInfo> bots;
    private final List<DecisionRecord> decisions = new ArrayList<>();
    private final List<ObserveRequest> observeRequests = new ArrayList<>();
    private ApiException failure;
    private WorldObservation observation;

    FakeBotGateway(List<BotInfo> bots) {
        this.bots = bots;
    }

    FakeBotGateway failingWith(ApiException failure) {
        this.failure = failure;
        return this;
    }

    FakeBotGateway observing(WorldObservation observation) {
        this.observation = observation;
        return this;
    }

    FakeBotGateway deciding(DecisionRecord... records) {
        decisions.addAll(List.of(records));
        return this;
    }

    List<ObserveRequest> observeRequests() {
        return observeRequests;
    }

    List<DecisionRecord> decisionRequests() {
        return decisions;
    }

    @Override
    public List<BotInfo> bots() {
        throwIfFailing();
        return bots;
    }

    @Override
    public Optional<BotInfo> bot(String id) {
        throwIfFailing();
        return bots.stream().filter(bot -> bot.id().equals(id)).findFirst();
    }

    @Override
    public WorldObservation observe(String id, ObserveRequest request) {
        throwIfFailing();
        observeRequests.add(request);
        if (bot(id).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Дух '" + id + "' не найден");
        }
        return observation;
    }

    @Override
    public List<DecisionRecord> decisions(String id, int limit) {
        throwIfFailing();
        if (bot(id).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Дух '" + id + "' не найден");
        }
        return decisions.stream().limit(limit).toList();
    }

    private void throwIfFailing() {
        if (failure != null) {
            throw failure;
        }
    }
}
