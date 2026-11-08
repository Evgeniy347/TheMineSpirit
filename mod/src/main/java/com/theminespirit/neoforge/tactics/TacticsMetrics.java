package com.theminespirit.neoforge.tactics;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.adapter.DecisionFacts;
import com.theminespirit.core.tactics.adapter.DecisionJournal;
import com.theminespirit.core.tactics.adapter.DecisionObservation;
import com.theminespirit.core.tactics.adapter.DecisionOrder;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.tactics.adapter.DecisionText;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class TacticsMetrics {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final DecisionJournal journal;
    private int stepCalls;
    private int blockReads;
    private int entityQueries;
    private int failures;
    private @Nullable String lastFailure;
    private @Nullable String restoreFailure;

    TacticsMetrics(int journalCapacity, int journalMaxPerSecond) {
        this.journal = new DecisionJournal(journalCapacity, journalMaxPerSecond);
    }

    List<DecisionRecord> decisions(int limit) {
        return journal.last(limit);
    }

    void journalDecision(
            long now,
            String spiritId,
            String spiritName,
            TacticalOutcome outcome,
            @Nullable String targetId,
            DecisionOrder order,
            Situation situation) {
        journal.offer(new DecisionObservation(
                        now,
                        spiritId,
                        spiritName,
                        outcome,
                        Optional.ofNullable(targetId),
                        order,
                        DecisionFacts.of(situation)))
                .ifPresent(record -> LOGGER.info("{}", DecisionText.format(record)));
    }

    void step(int blocks, int entities) {
        stepCalls++;
        blockReads += blocks;
        entityQueries += entities;
    }

    void failed(RuntimeException error) {
        failures++;
        lastFailure = error.toString();
    }

    void failRestore(String reason) {
        restoreFailure = reason;
    }

    int stepCalls() {
        return stepCalls;
    }

    int blockReads() {
        return blockReads;
    }

    int entityQueries() {
        return entityQueries;
    }

    int failures() {
        return failures;
    }

    Optional<String> lastFailure() {
        return Optional.ofNullable(lastFailure);
    }

    Optional<String> restoreFailure() {
        return Optional.ofNullable(restoreFailure);
    }
}
