package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WithdrawSpreadLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/withdraw-split/warehouse.json");

    @Test
    void liveWarehouse_BeansOnlyInFarChest_PlanPicksChestByContents() throws IOException {
        JsonNode live = live();
        List<ContainerSnapshot> chests = chests(live);

        WithdrawSpread.Plan plan = WithdrawSpread.plan(chests, origin(live), "minecraft:cocoa_beans", 15);

        assertEquals(
                0,
                plan.left(),
                "cocoa_beans 15 лежат только в -181/-180, а ближайший к духу -178/-179 их не содержит: "
                        + "план обязан выбрать сундук по содержимому, а не по расстоянию. Шаги: " + plan.steps()
                        + ". При смене plan проверь выбор по содержимому");
        assertEquals(
                List.of(-181),
                plan.steps().stream()
                        .map(step -> chests.get(step.index()).pos().x())
                        .toList(),
                "забор идёт из -181/-180: только там есть cocoa_beans (живой NBT сундука), иначе план тянет "
                        + "пустой ближний сундук. При смене фикстуры проверь содержимое -181/-180");
        assertEquals(
                15,
                plan.steps().get(0).count(),
                "в сундуке ровно 15 cocoa_beans: иначе план просит больше, чем есть, и перенос обрывается. "
                        + "При смене фикстуры проверь живой NBT");
    }

    @Test
    void liveWarehouse_DirtSplitAcrossChests_PlanTakesBoth() throws IOException {
        JsonNode live = live();
        List<ContainerSnapshot> chests = chests(live);

        WithdrawSpread.Plan plan = WithdrawSpread.plan(chests, origin(live), "minecraft:dirt", 1000);

        assertEquals(
                1000,
                plan.steps().stream().mapToInt(WithdrawSpread.Step::count).sum(),
                "dirt разложен по двум сундукам (384 и 744): за один сундук 1000 не взять, план обязан "
                        + "продолжить в следующем. Шаги: " + plan.steps()
                        + ". При смене plan проверь переход к следующему сундуку");
        assertEquals(
                0,
                plan.left(),
                "после обоих сундуков запрос закрыт: остаток означает недобор и срыв задания, иначе дух уходит "
                        + "без ресурса. При смене plan проверь закрытие запроса на двух сундуках");
        assertEquals(
                2,
                plan.steps().stream().map(WithdrawSpread.Step::index).distinct().count(),
                "шаги идут в разные сундуки: " + plan.steps()
                        + ", иначе план дважды берёт из одного и не добирает dirt. При смене проверь уникальность сундуков");
    }

    @Test
    void liveWarehouse_NbtCounts_MatchServer() throws IOException {
        JsonNode live = live();
        List<ContainerSnapshot> chests = chests(live);

        assertEquals(
                54,
                chests.get(0).slotCount(),
                "-181/-180 — двойной сундук на 54 слота, как в мире: иное число значит, что парсер NBT потерял слоты, "
                        + "иначе план недоберёт ресурс. При смене фикстуры проверь размер");
        assertEquals(
                54,
                chests.get(1).slotCount(),
                "-178/-179 — двойной сундук на 54 слота, как в мире: иное число искажает раскладку и остаток, "
                        + "иначе расчёт переноса врёт. При смене фикстуры проверь размер");
        assertEquals(
                744,
                dirt(chests.get(1)),
                "в -178/-179 живой NBT показывает 11 стопок и 40 dirt = 744: иначе счёт переноса врёт и остаток "
                        + "не сходится. При смене фикстуры проверь dirt");
        assertEquals(
                384,
                dirt(chests.get(0)),
                "в -181/-180 живой NBT показывает 6 стопок dirt = 384: иначе расчёт остатка неверен и план "
                        + "недобирает. При смене фикстуры проверь dirt");
        assertEquals(
                15,
                WithdrawSpread.count(chests.get(0), IdFilter.of(List.of("minecraft:cocoa_beans"))),
                "cocoa_beans 15 есть только в -181/-180, как в мире: иначе план берёт из пустого сундука "
                        + "и возвращается ни с чем. При смене фикстуры проверь бобы");
        assertEquals(
                0,
                WithdrawSpread.count(chests.get(1), IdFilter.of(List.of("minecraft:cocoa_beans"))),
                "в ближайшем -178/-179 cocoa_beans нет: прошлый withdraw и переносил 0, иначе повтор повторит "
                        + "ошибку выбора по расстоянию. При смене фикстуры проверь ближний сундук");
    }

    private static int dirt(ContainerSnapshot snapshot) {
        int total = 0;
        for (Stack stack : snapshot.slots()) {
            if (stack != null && "minecraft:dirt".equals(stack.item())) {
                total += stack.count();
            }
        }
        return total;
    }

    private static JsonNode live() throws IOException {
        return new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
    }

    private static BlockCoord origin(JsonNode live) {
        JsonNode value = live.get("origin");
        return new BlockCoord(
                value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt());
    }

    private static List<ContainerSnapshot> chests(JsonNode live) {
        List<ContainerSnapshot> chests = new ArrayList<>();
        for (JsonNode container : live.get("containers")) {
            List<Stack> slots = new ArrayList<>();
            for (JsonNode item : container.get("items")) {
                String id = item.get("item").asText();
                int left = item.get("count").asInt();
                int max = item.has("maxStack") ? item.get("maxStack").asInt() : 64;
                while (left > 0) {
                    int count = Math.min(left, max);
                    slots.add(new Stack(id, 0, count, max));
                    left -= count;
                }
            }
            while (slots.size() < 54) {
                slots.add(null);
            }
            chests.add(new ContainerSnapshot(coord(container.get("pos")), slots));
        }
        return chests;
    }

    private static BlockCoord coord(JsonNode value) {
        return new BlockCoord(
                value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt());
    }
}
