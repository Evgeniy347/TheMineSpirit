package com.theminespirit.neoforge;

import com.theminespirit.core.script.PourBucketParams;
import com.theminespirit.core.script.api.FillBucketParams;

final class BucketRegistrations {
    private BucketRegistrations() {}

    static void register(SpiritScriptPack pack) {
        pack.put("fill_bucket", spirit -> params -> new FillBucketScript(spirit, FillBucketParams.from(params)));
        pack.put("pour_bucket", spirit -> params -> new PourBucketScript(spirit, PourBucketParams.from(params)));
    }
}
