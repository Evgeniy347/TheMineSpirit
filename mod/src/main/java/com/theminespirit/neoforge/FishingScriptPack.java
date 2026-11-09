package com.theminespirit.neoforge;

import com.theminespirit.core.script.BucketFishParams;
import com.theminespirit.core.script.FishingParams;

final class FishingScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "fishing";
    }

    @Override
    protected void register() {
        put("fish", spirit -> params -> new FishScript(spirit, FishingParams.from(params)));
        put("bucket_fish", spirit -> params -> new BucketFishScript(spirit, BucketFishParams.from(params)));
    }
}
