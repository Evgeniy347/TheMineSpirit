package com.theminespirit.core.reload;

import com.theminespirit.core.base.RespawnRules;
import com.theminespirit.core.doors.DoorTuning;
import com.theminespirit.core.doors.DoorTuning.DoorRules;
import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.fishing.FishingWait;
import com.theminespirit.core.forestry.ReplantData;
import com.theminespirit.core.hunger.HungerRules;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.SwordTier;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentLevels;
import com.theminespirit.core.pickup.MagnetRules;
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.repair.RepairData;
import java.util.Map;

record WorldTables(
        TableHolder<FeedTable> feed,
        TableHolder<Map<String, SwordTier>> swords,
        TableHolder<MagnetRules> magnet,
        TableHolder<FishingWait> fishingWait,
        TableHolder<FishingData> fishing,
        TableHolder<ReplantData> replant,
        TableHolder<DoorRules> doors,
        TableHolder<HungerRules> hunger,
        TableHolder<RespawnRules> respawn,
        TableHolder<DescentData> descent,
        TableHolder<DescentLevels> descentLevels,
        TableHolder<RepairData> repair,
        TableHolder<ProcessingRules> processing) {
    static WorldTables register(TableRegistry registry) {
        return new WorldTables(
                DataTableDefinitions.single(
                        registry,
                        "feed",
                        "data/theminespirit/husbandry/feed.json",
                        FeedTable::loadJson,
                        FeedTable::loadFile),
                DataTableDefinitions.single(
                        registry,
                        "sword",
                        "data/theminespirit/husbandry/sword.json",
                        SwordTier::parse,
                        SwordTier::loadFile),
                DataTableDefinitions.single(
                        registry, "magnet", MagnetRules.RESOURCE, MagnetRules::parse, MagnetRules::load),
                DataTableDefinitions.single(
                        registry, "fishing_wait", FishingWait.RESOURCE, FishingWait::parse, FishingWait::load),
                DataTableDefinitions.single(
                        registry, "fishing", FishingData.RESOURCE, FishingData::parse, FishingData::load),
                DataTableDefinitions.single(
                        registry, "replant", ReplantData.RESOURCE, ReplantData::parse, ReplantData::load),
                DataTableDefinitions.single(
                        registry,
                        "doors",
                        "data/theminespirit/doors/doors.json",
                        DoorTuning::parse,
                        DoorTuning::loadDefault),
                DataTableDefinitions.single(
                        registry, "hunger", HungerRules.RESOURCE, HungerRules::parse, HungerRules::load),
                DataTableDefinitions.single(
                        registry, "respawn", RespawnRules.RESOURCE, RespawnRules::parse, RespawnRules::load),
                DataTableDefinitions.single(
                        registry, "descent", DescentData.RESOURCE, DescentData::parse, DescentData::load),
                DataTableDefinitions.single(
                        registry, "descent_levels", DescentLevels.RESOURCE, DescentLevels::parse, DescentLevels::load),
                DataTableDefinitions.single(
                        registry, "repair", RepairData.RESOURCE, RepairData::parse, RepairData::load),
                DataTableDefinitions.single(
                        registry,
                        "processing",
                        ProcessingRules.RESOURCE,
                        ProcessingRules::parse,
                        ProcessingRules::load));
    }
}
