package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.theminespirit.core.board.api.ToolFate;
import com.theminespirit.core.board.api.ToolsConfig;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.api.TableHolder;
import com.theminespirit.core.reload.api.TableStatus;
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
                "Изношенный деревянный инструмент не чинят: он дешевле нового крафта"
                        + " Иначе тест fate_DamagedWoodenAndStone_Replaced перестанет ловить эту поломку. При смене fate_DamagedWoodenAndStone_Replaced проверь это утверждение.");
        assertEquals(
                ToolFate.REPLACE,
                DEFAULTS.fate("minecraft:stone_axe", true),
                "Изношенный каменный инструмент не чинят: он дешевле нового крафта"
                        + " Иначе тест fate_DamagedWoodenAndStone_Replaced перестанет ловить эту поломку. При смене fate_DamagedWoodenAndStone_Replaced проверь это утверждение.");
    }

    @Test
    void fate_DamagedIronAndDiamond_Repaired() {
        assertEquals(
                ToolFate.REPAIR,
                DEFAULTS.fate("minecraft:iron_pickaxe", true),
                "Изношенный железный инструмент чинят: новый дороже ремонта"
                        + " Иначе тест fate_DamagedIronAndDiamond_Repaired перестанет ловить эту поломку. При смене fate_DamagedIronAndDiamond_Repaired проверь это утверждение.");
        assertEquals(
                ToolFate.REPAIR,
                DEFAULTS.fate("minecraft:diamond_shovel", true),
                "Изношенный алмазный инструмент чинят: новый дороже ремонта"
                        + " Иначе тест fate_DamagedIronAndDiamond_Repaired перестанет ловить эту поломку. При смене fate_DamagedIronAndDiamond_Repaired проверь это утверждение.");
    }

    @Test
    void fate_UndamagedOrNotTool_Kept() {
        assertEquals(
                ToolFate.KEEP,
                DEFAULTS.fate("minecraft:iron_pickaxe", false),
                "Целый инструмент не чинят и не заменяют: повод появляется только у изношенного"
                        + " Иначе тест fate_UndamagedOrNotTool_Kept перестанет ловить эту поломку. При смене fate_UndamagedOrNotTool_Kept проверь это утверждение.");
        assertEquals(
                ToolFate.KEEP,
                DEFAULTS.fate("minecraft:iron_chestplate", true),
                "Броня не инструмент набора: решение о её ремонте не в таблице инструментов"
                        + " Иначе тест fate_UndamagedOrNotTool_Kept перестанет ловить эту поломку. При смене fate_UndamagedOrNotTool_Kept проверь это утверждение.");
    }

    @Test
    void fate_RepairTiersFromConfig_DefinesWhatIsRepaired() {
        ToolsConfig stoneOnly = new ToolsConfig(List.of("pickaxe"), "stone", List.of("stone"));

        assertEquals(
                ToolFate.REPAIR,
                stoneOnly.fate("minecraft:stone_pickaxe", true),
                "Уровень из repairTiers чинится: набор уровней ремонта — данные, а не код"
                        + " Иначе тест fate_RepairTiersFromConfig_DefinesWhatIsRepaired перестанет ловить эту поломку. При смене fate_RepairTiersFromConfig_DefinesWhatIsRepaired проверь это утверждение.");
        assertEquals(
                ToolFate.REPLACE,
                stoneOnly.fate("minecraft:iron_pickaxe", true),
                "Уровень вне repairTiers заменяется: таблица решает, что чинить"
                        + " Иначе тест fate_RepairTiersFromConfig_DefinesWhatIsRepaired перестанет ловить эту поломку. При смене fate_RepairTiersFromConfig_DefinesWhatIsRepaired проверь это утверждение.");
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
                    "После /reload набор духа читается из новой таблицы: меч вместо кирки, топора и лопаты"
                            + " Иначе тест reload_NewBoardTools_ChangesSpiritSet перестанет ловить эту поломку. При смене reload_NewBoardTools_ChangesSpiritSet проверь это утверждение.");
        } finally {
            holder.replace(original, TableStatus.Source.MOD, "classpath (мод)", "restore", Clock.systemUTC());
        }
        assertFalse(
                before.contains("sword"),
                "До подмены таблицы меч не входил в набор: тест доказывает смену набора, а не случайное совпадение"
                        + " Иначе тест reload_NewBoardTools_ChangesSpiritSet перестанет ловить эту поломку. При смене reload_NewBoardTools_ChangesSpiritSet проверь это утверждение.");
    }
}
