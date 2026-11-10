package com.theminespirit.core.reload;

import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.defense.DefenseProfile;
import com.theminespirit.core.intellect.defense.DefenseProfileParser;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;

record TacticsTables(
        TableHolder<TacticsConfig> tactics,
        TableHolder<TacticsAdapterSettings> adapter,
        TableHolder<DefenseProfile> priorities,
        TableHolder<IntellectTuning> intellect) {
    static TacticsTables register(TableRegistry registry) {
        return new TacticsTables(
                registry.registerLazy(DataTableDefinitions.tactics(), TacticsConfig::readFromClasspath),
                DataTableDefinitions.single(
                        registry,
                        "adapter",
                        DataTables.TACTICS_ROOT + "adapter.json",
                        (json, origin) -> TacticsAdapterSettings.fromJson(json),
                        TacticsAdapterSettings::readFromClasspath),
                DataTableDefinitions.single(
                        registry,
                        "priorities",
                        DefenseProfile.RESOURCE,
                        (json, origin) -> DefenseProfileParser.fromJson(json),
                        DefenseProfile::readFromClasspath),
                DataTableDefinitions.single(
                        registry,
                        "intellect",
                        IntellectTuning.RESOURCE,
                        IntellectTuning::parse,
                        IntellectTuning::defaults));
    }
}
