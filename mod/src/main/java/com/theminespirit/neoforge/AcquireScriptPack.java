package com.theminespirit.neoforge;

import com.theminespirit.core.script.AcquireParams;

final class AcquireScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "acquire";
    }

    @Override
    protected void register() {
        put("acquire", spirit -> params -> SpiritScripts.acquire(spirit, AcquireParams.from(params)));
        put("acquire_at", spirit -> params -> SpiritScripts.acquire(spirit, AcquireParams.fromPlace(params)));
    }
}
