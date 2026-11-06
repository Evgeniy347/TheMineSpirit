package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableHolder;
import com.theminespirit.core.reload.TableStatus;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolsConfigTests {

    private static final ToolsConfig DEFAULTS = ToolsConfig.defaults();

    @Test
    void fate_DamagedWoodenAndStone_Replaced() {
        assertEquals(
                ToolFate.REPLACE,
                DEFAULTS.fate("minecraft:wooden_pickaxe", true),
                "Изношенный деревянный инструмент не чинят: он дешевле нового крафта");
        assertEquals(
                ToolFate.REPLACE,
                DEFAULTS.fate("minecraft:stone_axe", true),
                "Изношенный каменный инструмент не чинят: он дешевле нового крафта");
    }

    @Test
    void fate_DamagedIronAndDiamond_Repaired() {
        assertEquals(
                ToolFate.REPAIR,
                DEFAULTS.fate("minecraft:iron_pickaxe", true),
                "Изношенный железный инструмент чинят: новый дороже ремонта");
        assertEquals(
                ToolFate.REPAIR,
                DEFAULTS.fate("minecraft:diamond_shovel", true),
                "Изношенный алмазный инструмент чинят: новый дороже ремонта");
    }

    @Test
    void fate_UndamagedOrNotTool_Kept() {
        assertEquals(
                ToolFate.KEEP,
                DEFAULTS.fate("minecraft:iron_pickaxe", false),
                "Целый инструмент не чинят и не заменяют: повод появляется только у изношенного");
        assertEquals(
                ToolFate.KEEP,
                DEFAULTS.fate("minecraft:iron_chestplate", true),
                "Броня не инструмент набора: решение о её ремонте не в таблице инструментов");
    }

    @Test
    void fate_RepairTiersFromConfig_DefinesWhatIsRepaired() {
        ToolsConfig stoneOnly = new ToolsConfig(List.of("pickaxe"), "stone", List.of("stone"));

        assertEquals(
                ToolFate.REPAIR,
                stoneOnly.fate("minecraft:stone_pickaxe", true),
                "Уровень из repairTiers чинится: набор уровней ремонта — данные, а не код");
        assertEquals(
                ToolFate.REPLACE,
                stoneOnly.fate("minecraft:iron_pickaxe", true),
                "Уровень вне repairTiers заменяется: таблица решает, что чинить");
    }

    @Test
    void reload_NewBoardTools_ChangesSpiritSet() {
        TableHolder<BoardConfig> holder = DataTables.INSTANCE.board();
        BoardConfig original = holder.current();
        List<String> before = holder.current().tools().set();
        try {
            holder.replace(
                    BoardConfig.fromJson(
                            "{\"tools\":{\"set\":[\"sword\"],\"tier\":\"iron\",\"repairTiers\":[\"diamond\"]}}",
                            "test datapack"),
                    TableStatus.Source.DATAPACK,
                    "test datapack",
                    "test-hash",
                    Clock.systemUTC());

            assertEquals(
                    List.of("sword"),
                    holder.current().tools().set(),
                    "После /reload набор духа читается из новой таблицы: меч вместо кирки, топора и лопаты");
        } finally {
            holder.replace(original, TableStatus.Source.MOD, "classpath (мод)", "restore", Clock.systemUTC());
        }
        assertFalse(
                before.contains("sword"),
                "До подмены таблицы меч не входил в набор: тест доказывает смену набора, а не случайное совпадение");
    }
}
