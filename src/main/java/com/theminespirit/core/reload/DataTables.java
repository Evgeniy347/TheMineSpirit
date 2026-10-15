package com.theminespirit.core.reload;

import com.theminespirit.core.armor.ArmorTable;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.SwordTier;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.mining.DropTable;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Единственный держатель данных мода: таблицы ценностей, коэффициентов и правил. Читатели (core и neoforge)
 * берут текущие версии отсюда, а не читают classpath сами. NeoForge-адаптер на {@code AddReloadListenerEvent}
 * вызывает {@link #reload(ContentSource)} — так ванильная {@code /reload} и датапак мира переопределяют любой файл.
 *
 * <p>Таблицы, которых нет в ресурс-менеджере (нет ни в моде, ни в датапаке), не перезагружаются и продолжают
 * работать в classpath-версии: отсутствие файла — не ошибка, ошибкой считается только нечитаемый/битый json.
 */
public final class DataTables {
    public static final DataTables INSTANCE = new DataTables(Clock.systemUTC());

    public static final String TACTICS_ROOT = "data/theminespirit/tactics/";

    private final TableRegistry registry;
    private final AtomicInteger valueSeeds = new AtomicInteger();
    private final AtomicInteger kitSeeds = new AtomicInteger();
    private final AtomicInteger armorSeeds = new AtomicInteger();
    private final TableHolder<TacticsConfig> tactics;
    private final TableHolder<TacticsAdapterSettings> adapter;
    private final TableHolder<ValueTable> values;
    private final TableHolder<BaseKit> kit;
    private final TableHolder<KitFor> kitFor;
    private final TableHolder<ArmorTable> armor;
    private final TableHolder<FeedTable> feed;
    private final TableHolder<Map<String, SwordTier>> swords;
    private final TableHolder<DropTable> drops;
    private final TableHolder<BlockProtection> protection;

    public DataTables(Clock clock) {
        this.registry = new TableRegistry(clock);
        this.tactics = registry.registerLazy(TableDefinition.composite("tactics",
                List.of(TACTICS_ROOT + "enemies.json", TACTICS_ROOT + "weights.json", TACTICS_ROOT + "rules.json",
                        TACTICS_ROOT + "modes.json"),
                contents -> TacticsConfig.fromJson(contents.get(0).json(), contents.get(1).json(),
                        contents.get(2).json(), contents.get(3).json())),
                TacticsConfig::readFromClasspath);
        this.adapter = registry.registerLazy(
                TableDefinition.single("adapter", TACTICS_ROOT + "adapter.json",
                        (json, origin) -> TacticsAdapterSettings.fromJson(json)),
                TacticsAdapterSettings::readFromClasspath);
        this.values = registry.registerLazy(
                TableDefinition.single("item_values", "data/theminespirit/inventory/item_values.json",
                        ValueTable::parse),
                () -> {
                    valueSeeds.incrementAndGet();
                    return ValueTable.load();
                });
        this.kit = registry.registerLazy(
                TableDefinition.single("base_kit", "data/theminespirit/inventory/base_kit.json", BaseKit::parse),
                () -> {
                    kitSeeds.incrementAndGet();
                    return BaseKit.load();
                });
        this.kitFor = registry.registerLazy(
                TableDefinition.single("kit_activities", "data/theminespirit/inventory/kit_activities.json",
                        KitFor::parse),
                KitFor::readClasspath);
        this.armor = registry.registerLazy(
                TableDefinition.single("armor", "data/theminespirit/armor/armor_table.json", ArmorTable::parse),
                () -> {
                    armorSeeds.incrementAndGet();
                    return ArmorTable.load();
                });
        this.feed = registry.registerLazy(
                TableDefinition.single("feed", "data/theminespirit/husbandry/feed.json", FeedTable::loadJson),
                FeedTable::loadFile);
        this.swords = registry.registerLazy(
                TableDefinition.single("sword", "data/theminespirit/husbandry/sword.json", SwordTier::parse),
                SwordTier::loadFile);
        this.drops = registry.registerLazy(
                TableDefinition.single("drops", "data/theminespirit/acquire/drops.json", DropTable::parse),
                DropTable::load);
        this.protection = registry.registerLazy(
                TableDefinition.single("protection", "data/theminespirit/policy/protection.json",
                        ProtectionConfig::parse),
                ProtectionConfig::readClasspath);
    }

    public TableRegistry registry() {
        return registry;
    }

    public int valueSeeds() {
        return valueSeeds.get();
    }

    public int kitSeeds() {
        return kitSeeds.get();
    }

    public int armorSeeds() {
        return armorSeeds.get();
    }

    public TableHolder<TacticsConfig> tactics() {
        return tactics;
    }

    public TableHolder<TacticsAdapterSettings> adapter() {
        return adapter;
    }

    public TableHolder<ValueTable> values() {
        return values;
    }

    public TableHolder<BaseKit> kit() {
        return kit;
    }

    public TableHolder<KitFor> kitFor() {
        return kitFor;
    }

    public TableHolder<ArmorTable> armor() {
        return armor;
    }

    public TableHolder<FeedTable> feed() {
        return feed;
    }

    public TableHolder<Map<String, SwordTier>> swords() {
        return swords;
    }

    public TableHolder<DropTable> drops() {
        return drops;
    }

    public TableHolder<BlockProtection> protection() {
        return protection;
    }

    /** Перезагружает все таблицы из источника. Возвращает отчёт по каждой таблице: обновлена или ошибка. */
    public List<TableRegistry.Report> reload(ContentSource source) {
        return registry.reload(source);
    }
}