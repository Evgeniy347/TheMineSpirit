package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptPack;
import com.theminespirit.core.engine.ScriptPacks;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.AcquireScript;
import com.theminespirit.core.planner.AcquireSupplies;
import com.theminespirit.core.planner.FreshSupplies;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.AcquireParams;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamDefaults;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.neoforge.board.SettlementWood;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.inventory.SpiritSupplies;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

public final class SpiritScripts {
    private static final List<ScriptPack<SpiritEntity>> PACKS = SpiritScriptPacks.all();
    private static final Set<String> SCRIPT_IDS = ScriptPacks.ids(PACKS);
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog().restrictedTo(SCRIPT_IDS);

    private SpiritScripts() {}

    static ScriptInstance acquire(SpiritEntity spirit, AcquireParams parsed) {
        {
            SpiritSupplies supplies = new SpiritSupplies(spirit, CatalogLimits.CONTAINER_RADIUS_DEFAULT);
            int count = parsed.count();
            AcquireSupplies source = parsed.gatherAt() == null
                    ? supplies
                    : new SettlementSupplies(
                            spirit,
                            supplies,
                            DataTables.INSTANCE.leveling().current().chestKeep());
            if (parsed.fresh()) {
                count = Math.min(supplies.inInventory(parsed.item()) + count, CatalogLimits.COUNT_MAX);
                source = new FreshSupplies(supplies, parsed.item());
            }
            return new AcquireScript(
                            parsed.item(),
                            count,
                            acquireKnowledge(spirit, supplies.inventory()),
                            source,
                            catalog(),
                            conditionCatalog(),
                            new AcquireScript.Registries(() -> registryFor(spirit), conditions()))
                    .gatherAt(parsed.gatherAt())
                    .gatherAtLeast(DataTables.INSTANCE.leveling().current().tripMin());
        }
    }

    public static ScriptRegistry registryFor(SpiritEntity spirit) {
        Map<String, ScriptFactory> factories = new LinkedHashMap<>();
        ScriptPacks.factories(PACKS, spirit).forEach((id, builder) -> factories.put(id, withDefaults(id, builder)));
        return ScriptRegistry.of(factories);
    }

    private static ScriptFactory withDefaults(String id, ScriptFactory delegate) {
        return new ScriptFactory() {
            @Override
            public ScriptInstance create(Map<String, Object> params) {
                return delegate.create(ParamDefaults.fill(CATALOG, id, params));
            }

            @Override
            public boolean observesWhileTacticsOwns() {
                return delegate.observesWhileTacticsOwns();
            }
        };
    }

    public static ConditionRegistry conditions() {
        return ConditionRegistry.of(Map.of());
    }

    public static ConditionCatalog conditionCatalog() {
        return ConditionCatalog.empty();
    }

    public static ScriptCatalog catalog() {
        return CATALOG;
    }

    private static RecipeKnowledge acquireKnowledge(SpiritEntity spirit, Map<String, Integer> inventory) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(spirit.level());
        if (!(spirit.level() instanceof ServerLevel level)) {
            return knowledge;
        }
        BlockCoord at = spirit.home()
                .orElseGet(() -> new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()));
        return knowledge.preferring(inventory, SettlementWood.of(level, at, inventory));
    }
}
