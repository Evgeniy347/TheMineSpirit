package com.theminespirit.core.tactics;

import java.util.List;

public record RetreatTable(List<RetreatEntry> entries, Condition callHelp) {
    public RetreatTable {
        entries = List.copyOf(entries);
    }
}
