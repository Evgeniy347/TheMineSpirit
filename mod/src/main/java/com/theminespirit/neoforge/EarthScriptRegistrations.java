package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.script.ClearAreaParams;
import com.theminespirit.core.script.DigAreaParams;
import com.theminespirit.core.script.FillParams;
import com.theminespirit.core.script.ScaffoldClearParams;
import java.util.Map;
import java.util.function.Function;

final class EarthScriptRegistrations {
    private EarthScriptRegistrations() {}

    static void register(Map<String, Function<SpiritEntity, ScriptFactory>> table) {
        table.put("fill", spirit -> params -> new FillScript(spirit, FillParams.from(params)));
        table.put("clear_area", spirit -> params -> new ClearAreaScript(spirit, ClearAreaParams.from(params)));
        table.put("clear_high", spirit -> params -> new HighClearScript(spirit, ScaffoldClearParams.from(params)));
        table.put("dig_area", spirit -> params -> new DigAreaScript(spirit, DigAreaParams.from(params)));
    }
}
