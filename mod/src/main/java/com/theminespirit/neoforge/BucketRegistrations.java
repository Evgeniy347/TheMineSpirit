package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.script.FillBucketParams;
import com.theminespirit.core.script.PourBucketParams;
import java.util.Map;
import java.util.function.Function;

final class BucketRegistrations {
    private BucketRegistrations() {}

    static void register(Map<String, Function<SpiritEntity, ScriptFactory>> table) {
        table.put("fill_bucket", spirit -> params -> new FillBucketScript(spirit, FillBucketParams.from(params)));
        table.put("pour_bucket", spirit -> params -> new PourBucketScript(spirit, PourBucketParams.from(params)));
    }
}
