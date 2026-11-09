package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptPack;
import java.util.List;

/**
 * lib: перечень наборов скриптов по механикам. Сам по себе он больше не знает реализаций: набор
 * объявляет свои скрипты рядом с ними, а сборку делает контракт базового модуля.
 */
final class SpiritScriptPacks {
    private SpiritScriptPacks() {}

    static List<ScriptPack<SpiritEntity>> all() {
        return List.of(
                new SensingScriptPack(),
                new MovementScriptPack(),
                new CombatScriptPack(),
                new SmallMechanicsScriptPack(),
                new InventoryScriptPack(),
                new FarmingScriptPack(),
                new ForestryScriptPack(),
                new CraftingScriptPack(),
                new HusbandryScriptPack(),
                new FishingScriptPack(),
                new BuildingScriptPack(),
                new MiningScriptPack(),
                new AcquireScriptPack());
    }
}
