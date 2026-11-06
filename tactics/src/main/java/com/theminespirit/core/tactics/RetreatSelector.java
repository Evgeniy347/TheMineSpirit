package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.List;

public final class RetreatSelector {
    private final RetreatTable table;
    private final RuleCounter counter;

    public RetreatSelector(RetreatTable table, RuleCounter counter) {
        this.table = table;
        this.counter = counter;
    }

    public RetreatOptions evaluate(FactMap facts) {
        List<RetreatCandidate> candidates = new ArrayList<>();
        for (RetreatEntry entry : table.entries()) {
            counter.check();
            if (entry.requires().matches(facts)) {
                candidates.add(new RetreatCandidate(entry, entry.utility(facts)));
            }
        }
        return new RetreatOptions(candidates);
    }

    public boolean callHelp(FactMap facts) {
        counter.check();
        return table.callHelp().matches(facts);
    }
}
