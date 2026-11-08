package com.theminespirit.core.script;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.List;

final class CoreScriptValues {
    private CoreScriptValues() {}

    static List<String> herdCheckValues() {
        List<String> values = new ArrayList<>(Species.names());
        values.add(Species.ALL);
        return List.copyOf(values);
    }

    static String waterAreaText() {
        var data = DataTables.INSTANCE.fishing().current();
        return data.minWidth() + "x" + data.minWidth() + " глубиной " + data.minDepth();
    }
}
