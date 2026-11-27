package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownFixtures;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SettlementPlotRevisionTests {

    @Test
    void settlementPlanRevision_MutationsAdvance_HomeDoesNot() {
        SettlementPlotStore store = SettlementPlotStore.load(new CompoundTag(), RegistryAccess.EMPTY);
        int start = store.planRevision();

        store.rememberHome(new BlockCoord(1, 64, 1));
        assertEquals(
                start,
                store.planRevision(),
                "Домашняя стоянка не меняет ожидания постройки: иначе аудит"
                        + " перезаливал бы все чанки после каждого возвращения духа. При смене ревизии перепроверь"
                        + " rememberHome и SettlementPlotStore.planRevision.");

        store.settlePlan(TownFixtures.flatPlan());
        assertEquals(
                start + 1,
                store.planRevision(),
                "Первая раскладка плана поднимает ревизию: иначе кэш"
                        + " останется привязан к пустому плану, и группы не соберутся вообще. При смене сигнала"
                        + " перепроверь settlePlan и TownAuditWatch.tick.");

        store.fixTown(TownFixtures.flatPlan());
        assertEquals(
                start + 2,
                store.planRevision(),
                "Замена плана картой тоже поднимает ревизию: иначе"
                        + " ожидания схем останутся от прежнего контура, и дух будет чинить удалённый участок."
                        + " При смене сигнала перепроверь fixTown и TownMapService.");

        store.clear();
        assertEquals(
                start + 3,
                store.planRevision(),
                "Очистка поселения инвалидирует снимок ожиданий:"
                        + " иначе группы исчезнувшего города продолжат висеть в графе и звать духов. При смене"
                        + " правила перепроверь clear и AuditRegistry.plan.");
    }
}
