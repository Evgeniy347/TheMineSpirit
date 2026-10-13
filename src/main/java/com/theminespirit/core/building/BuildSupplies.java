package com.theminespirit.core.building;

import com.theminespirit.core.plan.ScriptCall;
import java.util.List;

public interface BuildSupplies {
    int inInventory(String block);

    List<ScriptCall> withdraw(String block, int maxCount);
}
