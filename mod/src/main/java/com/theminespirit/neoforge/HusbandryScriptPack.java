package com.theminespirit.neoforge;

import com.theminespirit.core.script.BreedParams;
import com.theminespirit.core.script.CaptureParams;
import com.theminespirit.core.script.CullParams;
import com.theminespirit.core.script.FeedParams;
import com.theminespirit.core.script.HerdCheckParams;
import com.theminespirit.core.script.HuntParams;
import com.theminespirit.neoforge.husbandry.BreedScript;
import com.theminespirit.neoforge.husbandry.CaptureScript;
import com.theminespirit.neoforge.husbandry.CullScript;
import com.theminespirit.neoforge.husbandry.FeedScript;
import com.theminespirit.neoforge.husbandry.HerdCheckScript;
import com.theminespirit.neoforge.husbandry.HuntScript;

final class HusbandryScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "husbandry";
    }

    @Override
    protected void register() {
        put("breed", spirit -> params -> new BreedScript(spirit, BreedParams.from(params)));
        put("feed", spirit -> params -> new FeedScript(spirit, FeedParams.from(params)));
        AnimalProduceScripts.register(this);
        put("cull", spirit -> params -> new CullScript(spirit, CullParams.from(params)));
        put("herd_check", spirit -> params -> new HerdCheckScript(spirit, HerdCheckParams.from(params)));
        put("hunt", spirit -> params -> new HuntScript(spirit, HuntParams.from(params)));
        put("capture", spirit -> params -> new CaptureScript(spirit, CaptureParams.from(params)));
    }
}
