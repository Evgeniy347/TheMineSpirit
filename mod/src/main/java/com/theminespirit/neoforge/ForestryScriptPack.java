package com.theminespirit.neoforge;

final class ForestryScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "forestry";
    }

    @Override
    protected void register() {
        HarvestScripts.register(this);
    }
}
