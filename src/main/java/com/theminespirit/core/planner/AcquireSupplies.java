package com.theminespirit.core.planner;

import com.theminespirit.core.plan.ScriptCall;
import java.util.List;

public interface AcquireSupplies {
    int inInventory(String item);

    List<ScriptCall> withdraw(String item, int maxCount);
}
