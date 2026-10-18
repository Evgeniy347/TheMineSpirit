package com.theminespirit.core.tactics;

import java.util.List;

public record RetreatTable(List<RetreatEntry> entries, Condition callHelp, RallyTuning rally) {
    public RetreatTable {
        entries = List.copyOf(entries);
        FactChecks.required("RetreatTable.rally", rally);
    }
}
