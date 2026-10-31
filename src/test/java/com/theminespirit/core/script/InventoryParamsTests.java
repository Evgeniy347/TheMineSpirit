package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryParamsTests {
    @Test
    void transferParams_FromValidatedPlan_ReadsContainerItemCountAndRadius() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("container", "nearest");
        params.put("item", "cobblestone");
        params.put("count", 10);
        params.put("radius", 8);

        TransferParams transfer = TransferParams.from(params);

        assertEquals(
                ContainerTarget.NEAREST,
                transfer.container(),
                "container='nearest' из плана даёт ближайший контейнер: именно это делает перенос "
                        + "рабочим без выдумывания координат");
        assertEquals(
                "cobblestone",
                transfer.item(),
                "item переносится в параметры без изменений: по нему фильтр решает, что идёт в контейнер");
        assertEquals(10, transfer.count(), "count переноса — приказ плана: сколько предметов кладём в контейнер");
        assertEquals(
                8,
                transfer.radius(),
                "radius переноса берётся из плана: по умолчанию 8 — ближайший контейнер " + "обычно рядом с духом");
    }

    @Test
    void transferParams_LeftoverAbsent_DefaultsFalse() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("container", "nearest");
        params.put("item", "cobblestone");
        params.put("count", 10);
        params.put("radius", 8);

        assertEquals(
                false,
                TransferParams.from(params).leftover(),
                "обычный deposit не сдаёт весь излишек: иначе сборщик выложил бы в сундук и то, "
                        + "что ещё нужно для постройки");
    }

    @Test
    void transferParams_LeftoverTrue_ReadsFlag() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("container", "auto");
        params.put("item", "minecraft:cobblestone");
        params.put("count", 64);
        params.put("radius", 8);
        params.put("leftover", true);

        assertEquals(
                true,
                TransferParams.from(params).leftover(),
                "store передаёт leftover=true: после названного предмета дух обязан сдать мусор "
                        + "(observe14 N7: 128 cobble и 64 dirt оставались в инвентарях)");
    }

    @Test
    void transferParams_CoordinatesTarget_GivesPosition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("container", "12,64,-7");
        params.put("item", "iron_ingot");
        params.put("count", 1);
        params.put("radius", 4);

        TransferParams transfer = TransferParams.from(params);

        assertEquals(
                new ContainerTarget.Position(12, 64, -7),
                transfer.container(),
                "Координаты контейнера из плана доходят до скрипта: без них дух пойдёт к ближайшему "
                        + "сундуку вместо названного");
    }

    @Test
    void transferParams_RadiusOutsideLimits_ThrowsWithRange() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("container", "nearest");
        params.put("item", "dirt");
        params.put("count", 1);
        params.put("radius", 17);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TransferParams.from(params),
                "Радиус вне [1, 16] обязан быть отказом: план мог обойти валидатор, "
                        + "а дух ушёл бы из базовой зоны");

        assertTrue(
                error.getMessage().contains("Радиус") && error.getMessage().contains("16"),
                "Сообщение обязано называть параметр radius и его предел 16: по нему правят план. " + "Фактическое: "
                        + error.getMessage());
    }

    @Test
    void transferParams_CountOutsideLimits_Throws() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("container", "nearest");
        params.put("item", "dirt");
        params.put("count", 257);
        params.put("radius", 8);

        assertThrows(
                IllegalArgumentException.class,
                () -> TransferParams.from(params),
                "Число предметов вне [1, 256] обязательно отказом: перенос тысяч стопок " + "занял бы серверный тик");
    }

    @Test
    void unloadParams_FromValidatedPlan_ReadsContainerRadiusAndForce() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("container", "nearest");
        params.put("radius", 8);
        params.put("force", false);

        UnloadParams unload = UnloadParams.from(params);

        assertEquals(
                ContainerTarget.NEAREST,
                unload.container(),
                "container разгрузки по умолчанию ближайший: база обычно стоит рядом с духом");
        assertEquals(8, unload.radius(), "radius разгрузки по умолчанию 8: контейнер ищется в базовой зоне");
        assertEquals(
                false,
                unload.force(),
                "force=false значит «разгрузка только при заполненном инвентаре»: "
                        + "иначе дух носил бы в сундук каждый лишний камешек");
    }

    @Test
    void unloadParams_ForceTrue_RequestsUnloadEvenWithEmptyInventory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("container", "1,2,3");
        params.put("radius", 2);
        params.put("force", true);

        UnloadParams unload = UnloadParams.from(params);

        assertEquals(
                true,
                unload.force(),
                "force=true снимает порог занятости: разгрузка выполняется по приказу, "
                        + "даже если инвентарь почти пуст");
    }

    @Test
    void equipParams_FromValidatedPlan_ReadsItemPattern() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("item", "*_pickaxe");

        EquipParams equip = EquipParams.from(params);

        assertEquals(
                "*_pickaxe",
                equip.item(),
                "Шаблон equip доходит до скрипта: по нему выбирается самая ценная подходящая кирка");
    }

    @Test
    void equipParams_MissingItem_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EquipParams.from(Map.of()),
                "Equip без item обязан быть отказом: без шаблона непонятно, что надевать");
    }
}
