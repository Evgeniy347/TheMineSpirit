package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.movement.PathNetworkService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class SchematicResult {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BuildSchematicScript host;

    SchematicResult(BuildSchematicScript host) {
        this.host = host;
    }

    ScriptStatus finish() {
        BuildStats stats = noteStats();
        host.reservations.releaseAll(host.spirit.getUUID());
        List<Schematic.PlacedEntry> missing = host.site.missing();
        List<BlockCoord> clear = host.site.toClear();
        LOGGER.info(
                "Стройка {}: подходов {} (план {}), стоянок выбрано {} (план {}), на крыше {}, отказов правила отхода {}, "
                        + "заходов {} (план {}), клеток плана на обычную стройку {}",
                host.buildKey,
                stats.approaches(),
                stats.planApproaches(),
                host.standsChosen,
                host.planStands,
                host.roofStands,
                host.retreatRefusals,
                host.trips,
                host.planTrips,
                host.planDeviations);
        if (missing.isEmpty() && clear.isEmpty()) {
            leaveSquad();
            if (host.level instanceof ServerLevel server) {
                PathNetworkService.endSite(server, host.buildKey);
            }
            host.tally.supplies(host.placed, 0);
            logRemovals();
            host.outcome = ScriptStatus.SUCCEEDED;
            return host.outcome;
        }
        for (int i = 0; i < host.blocked.size(); i++) {
            host.tally.skipped();
        }
        return fail("не совпало со схемой " + missing.size() + " из "
                + host.site.entries().size() + " клеток"
                + (clear.isEmpty() ? "" : ", не очищено " + clear.size()) + ": "
                + BuildSamples.of(missing, clear) + "; дух " + host.feet() + "; поставлено " + host.placed
                + ", очищено " + host.cleared + "; причины: " + reasons());
    }

    ScriptStatus noMaterial(String blockId, @Nullable String refusal) {
        if (host.trips < BuildSupplyTrip.MAX_TRIPS) {
            List<ScriptCall> steps = BuildSupplyTrip.plan(host.spirit, host.site, host.phaseBand, host.bottomY);
            if (!steps.isEmpty()) {
                host.trips++;
                LOGGER.info(
                        "Стройка {}: заход {} за материалами ({} шагов withdraw), поставлено {} из {}, не хватило '{}'",
                        host.buildKey,
                        host.trips,
                        steps.size(),
                        host.placed,
                        host.site.primaries().size(),
                        blockId);
                return host.startSupply(steps);
            }
        }
        if (SchematicActions.toolKind(blockId) != null) {
            BuildToolRestock.Answer answer = host.toolRestock.answer(host.spirit, blockId, refusal);
            if (answer.plan() != null && host.crafts < BuildSchematicScript.MAX_OWN_CRAFTS) {
                host.crafts++;
                LOGGER.info("Стройка {}: нужен инструмент ({}) — сундук, иначе крафт графа", host.buildKey, blockId);
                return host.startSupply(answer.plan());
            }
            host.failed++;
            host.tally.failed();
            return fail(answer.reason());
        }
        int needed = countMissing(blockId);
        if (host.crafts < BuildSchematicScript.MAX_OWN_CRAFTS && "minecraft:torch".equals(blockId)) {
            int held = BlockItems.countIn(host.spirit.getInventory(), blockId);
            int count = Math.min(CatalogLimits.COUNT_MAX, held + Math.max(needed - held, 1));
            int coal = host.spirit.getInventory().countItem(Items.COAL)
                    + host.spirit.getInventory().countItem(Items.CHARCOAL);
            if (coal * BuildSchematicScript.TORCHES_PER_COAL >= count - held) {
                host.crafts++;
                LOGGER.info("Стройка {}: крафт своих факелов ×{} из угля духа", host.buildKey, count - held);
                return host.startSupply(SelfCraftSteps.of(host.spirit, blockId, count));
            }
        }
        if (host.crafts < BuildSchematicScript.MAX_OWN_CRAFTS
                && !OwnWood.species(blockId).isEmpty()) {
            int held = BlockItems.countIn(host.spirit.getInventory(), blockId);
            int count = Math.min(CatalogLimits.COUNT_MAX, held + Math.max(needed - held, 1));
            if (OwnWood.planksHeld(host.spirit.getInventory(), OwnWood.species(blockId))
                    >= OwnWood.plankCost(blockId, count - held)) {
                host.crafts++;
                LOGGER.info("Стройка {}: крафт своего {} ×{} из брёвен духа", host.buildKey, blockId, count - held);
                return host.startSupply(SelfCraftSteps.of(host.spirit, blockId, count));
            }
        }
        host.failed++;
        host.tally.failed();
        int have = BlockItems.countIn(host.spirit.getInventory(), blockId);
        host.tally.supplies(host.placed + have, Math.max(needed - have, 0));
        return fail("не хватило материала '" + blockId + "': поставлено " + host.placed + " из "
                + host.site.primaries().size() + "; нужно " + needed + ", есть " + have);
    }

    ScriptStatus fail(String reason) {
        logRemovals();
        noteStats();
        host.failureReason = reason;
        host.outcome = ScriptStatus.FAILED;
        cancel();
        return host.outcome;
    }

    Optional<ScriptInstance.Progress> progress() {
        int total = Math.max(host.site.entries().size(), 1);
        return Optional.of(new ScriptInstance.Progress(
                host.site.entries().size() - host.site.missing().size(), total));
    }

    Optional<ScriptResult> result() {
        return host.outcome == ScriptStatus.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(host.tally.toData()))
                : Optional.empty();
    }

    void cancel() {
        host.supplyWalk = null;
        if (host.planPending != null) {
            host.planPending.cancel(false);
            host.planPending = null;
        }
        stopSupply();
        host.approach.cancel();
        host.breaker.cancel();
        host.breaking = null;
        host.reservations.releaseAll(host.spirit.getUUID());
        leaveSquad();
    }

    void suspend() {
        stopSupply();
        host.approach.suspend();
        host.breaker.cancel();
        host.breaking = null;
    }

    String failureReason() {
        return host.failureReason;
    }

    Map<String, Object> saveState() {
        return Map.copyOf(
                new BuildState(host.placed, 0, host.blocked.size(), host.failed, host.outcome == ScriptStatus.RUNNING)
                        .toMap());
    }

    void restoreState(Map<String, Object> state) {
        BuildState snapshot = BuildState.fromMap(state);
        host.placed = Math.max(host.placed, snapshot.placed());
        host.failed = Math.max(host.failed, snapshot.failed());
        String kind =
                host.site.entries().isEmpty() ? "" : host.site.entries().get(0).block();
        for (int i = 0; i < snapshot.placed() && !kind.isEmpty(); i++) {
            host.tally.placed(kind);
        }
        for (int i = 0; i < snapshot.failed(); i++) {
            host.tally.failed();
        }
    }

    private String reasons() {
        List<Schematic.PlacedEntry> missing = host.site.missing();
        if (!missing.isEmpty()) {
            Schematic.PlacedEntry first = missing.get(0);
            String why = host.blocked.get(first.pos());
            String state = "в мире "
                    + host.level
                            .getBlockState(SchematicSite.toPos(first.pos()))
                            .getBlock()
                            .getDescriptionId()
                    + ", свободна " + host.site.freeForPlacing(first) + ", опора " + host.site.supported(first)
                    + ", материал " + BlockItems.countIn(host.spirit.getInventory(), first.block())
                    + ", основная часть " + host.site.primaries().contains(first) + ", занята другим "
                    + host.reservations.foreign(first.pos(), host.spirit.getUUID());
            if (why != null || host.blocked.isEmpty()) {
                return first.pos() + " — " + (why == null ? "не отложена" : why) + " (" + state + ")"
                        + (host.lastRefusal.isBlank() ? "" : "; последний отказ: " + host.lastRefusal);
            }
        }
        if (host.blocked.isEmpty()) {
            return host.lastRefusal.isBlank() ? "нет" : host.lastRefusal;
        }
        Map.Entry<BlockCoord, String> first = host.blocked.entrySet().iterator().next();
        return first.getKey() + " — " + first.getValue()
                + (host.blocked.size() > 1 ? " и ещё " + (host.blocked.size() - 1) : "");
    }

    private int countMissing(String blockId) {
        int total = 0;
        for (Schematic.PlacedEntry entry : host.site.primaries()) {
            if (entry.block().equals(blockId) && !host.site.matches(entry)) {
                total++;
            }
        }
        return Math.max(total, 1);
    }

    private BuildStats noteStats() {
        BuildStats stats =
                new BuildStats(host.planStands, host.planTrips, host.standsChosen, host.trips, host.planDeviations);
        host.spirit.noteBuildStats(stats);
        return stats;
    }

    private void logRemovals() {
        if (!host.removals.isEmpty()) {
            LOGGER.info(
                    "[clear] стройка {} духом {} сняла: {}",
                    host.buildKey,
                    host.spirit.displayName(),
                    host.removals.summary());
        }
    }

    private void stopSupply() {
        if (host.supplyRunner != null) {
            host.supplyRunner.cancel();
            host.supplyRunner = null;
        }
    }

    private void leaveSquad() {
        BuildSquadRegistry.leave(host.level, host.buildKey, host.spirit.getUUID());
    }
}
