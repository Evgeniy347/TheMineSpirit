package com.theminespirit.core.planner;

import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AcquireSupplies {
    int inInventory(String item);

    List<ScriptCall> withdraw(String item, int maxCount);

    Map<String, Integer> inventory();

    Set<String> nearbyStations();
}
