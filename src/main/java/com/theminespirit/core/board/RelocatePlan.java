package com.theminespirit.core.board;

import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import java.util.Objects;
import java.util.Set;

/**
 * Задание «перенос»: какие кейсы вообще постройки и какие из них неподвижны.
 * Неподвижный список — данные таблицы board ({@code immovable}); источник и REST
 * проверяют его до постановки RELOCATE_BUILDING, иначе доска снесла бы вход в шахту.
 */
public final class RelocatePlan {
    public static final Set<BoardCase> BUILDING_CASES = Set.of(
            BoardCase.BUILD_MINE,
            BoardCase.BUILD_PEN,
            BoardCase.BUILD_HOUSE,
            BoardCase.BUILD_WAREHOUSE,
            BoardCase.ENCHANT_ROOM,
            BoardCase.BUILD_FENCE);

    private RelocatePlan() {
    }

    public static boolean buildingCase(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс постройки не может быть null: без него неизвестно, "
                + "строение ли переносят");
        return BUILDING_CASES.contains(boardCase);
    }

    public static boolean immovable(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс неподвижности не может быть null: без него неизвестно, "
                + "можно ли переносить постройку");
        return DataTables.INSTANCE.board().current().immovable().contains(boardCase.name());
    }

    public static String schemeFor(BoardCase boardCase) {
        assertMovable(boardCase);
        return switch (boardCase) {
            case BUILD_PEN -> "pen_7x7";
            case BUILD_HOUSE -> "house";
            case BUILD_WAREHOUSE -> WarehouseRules.scheme(WarehouseRules.LAST_LEVEL);
            case ENCHANT_ROOM -> "enchant_room_v1";
            case BUILD_FENCE -> "fence";
            case BUILD_MINE -> "mine_entrance_v1";
            default -> throw new IllegalArgumentException("Кейс " + boardCase + " не постройка: "
                    + "схемы для переноса нет");
        };
    }

    public static Set<String> immovableNames() {
        return Set.copyOf(DataTables.INSTANCE.board().current().immovable());
    }

    public static void assertMovable(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс переноса не может быть null: без него непонятно, "
                + "что проверяем");
        if (!buildingCase(boardCase)) {
            throw new IllegalArgumentException("Кейс " + boardCase + " не постройка: переносить можно "
                    + "только строения " + BUILDING_CASES + ", иначе доска переносила бы работу");
        }
        if (immovable(boardCase)) {
            throw new IllegalArgumentException("Постройка " + boardCase + " неподвижна: вход в шахту "
                    + "не переносят, иначе доска снесла бы уже работающую шахту");
        }
    }

    public static TaskNode plan(BoardCase boardCase, TicketTarget target) {
        assertMovable(boardCase);
        return TicketPlans.plan(BoardCase.RELOCATE_BUILDING, target);
    }
}
