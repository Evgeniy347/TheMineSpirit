package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class PlotProtection {
    public enum Action {
        BREAK,
        HARVEST,
        CHOP,
        DIG,
        PLACE
    }

    public enum Ground {
        NONE,
        PLAN_DEMOLISH,
        REPAIR,
        PLAN_EXCAVATE,
        PLAN_BUILD,
        ORCHARD_CARE,
        SELF_RESCUE,
        PLAYER_COMMAND,
        TEMPORARY_MOVEMENT
    }

    public sealed interface Decision permits Allowed, Refused {}

    public record Allowed(Ground basis, PlanBuilding building, String area) implements Decision {
        public Allowed {
            Objects.requireNonNull(basis, "Основание не может быть null: по нему пишется журнал");
            Objects.requireNonNull(building, "Постройка не может быть null: по ней пишется журнал");
            Objects.requireNonNull(area, "Место не может быть null: по нему пишется журнал");
        }
    }

    public record Refused(PlanBuilding building, String area, String reason) implements Decision {
        public Refused {
            Objects.requireNonNull(building, "Постройка не может быть null: по ней пишется журнал");
            Objects.requireNonNull(area, "Место не может быть null: по нему пишется журнал");
            Objects.requireNonNull(reason, "Причина отказа не может быть null: без неё действие не объяснит отказ");
        }
    }

    private PlotProtection() {}

    public static Decision check(
            @Nullable StageSite site, BlockCoord cell, Action action, Ground ground, PlanBuilding ownBuilding) {
        return check(site, cell, action, ground, ownBuilding, true);
    }

    public static Decision check(
            @Nullable StageSite site,
            BlockCoord cell,
            Action action,
            Ground ground,
            PlanBuilding ownBuilding,
            boolean structureBlock) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё не проверить защиту");
        Objects.requireNonNull(action, "Действие не может быть null: от него зависит правило");
        Objects.requireNonNull(ground, "Основание не может быть null: без него не отличить законную ломку");
        Objects.requireNonNull(ownBuilding, "Своя постройка не может быть null: без неё не отличить чужую клетку");
        if (site == null || !site.protectionCell(cell.x(), cell.y(), cell.z())) {
            return new Allowed(ground, PlanBuilding.NONE, "");
        }
        PlanBuilding building = site.protectionBuilding(cell.x(), cell.y(), cell.z());
        String area = site.protectionArea(cell.x(), cell.y(), cell.z());
        if (ground == Ground.SELF_RESCUE || ground == Ground.PLAYER_COMMAND || ground == Ground.TEMPORARY_MOVEMENT) {
            return new Allowed(ground, building, area);
        }
        if (ground == Ground.ORCHARD_CARE) {
            if (building == PlanBuilding.ORCHARD && (action == Action.HARVEST || action == Action.CHOP)) {
                return new Allowed(ground, building, area);
            }
            return new Refused(
                    building,
                    area,
                    "уход за садом не распространяется на клетку " + cell + " (" + area
                            + "): сад снимают и чинят только его заданием");
        }
        if (ground == Ground.PLAN_EXCAVATE) {
            if (building == PlanBuilding.MINE) {
                return new Allowed(ground, building, area);
            }
            return new Refused(
                    building,
                    area,
                    "клетка " + cell + " — блок постройки/плана поселения (" + area + "), а прокоп разрешён только "
                            + "в шахте по плану: остальное — снос, ремонт, самоспасение или команда игрока");
        }
        if (ground == Ground.PLAN_BUILD) {
            return new Allowed(ground, building, area);
        }
        if (ground == Ground.PLAN_DEMOLISH || ground == Ground.REPAIR) {
            return demolition(building, area, cell, ground, ownBuilding, structureBlock);
        }
        if (!structureBlock && action != Action.HARVEST && action != Action.DIG && action != Action.PLACE) {
            return new Allowed(ground, building, area);
        }
        if (!structureBlock && action == Action.PLACE) {
            return new Refused(
                    building,
                    area,
                    "клетка " + cell + " — место плана поселения (" + area + "): ставить здесь можно только стройкой "
                            + "по плану, а не засыпкой, мостом или пахотой");
        }
        return new Refused(
                building,
                area,
                "клетка " + cell + " — блок постройки/плана поселения (" + area + "), у действия " + action
                        + " нет законного основания: снос по плану, ремонт к схеме, самоспасение или команда игрока");
    }

    private static Decision demolition(
            PlanBuilding building,
            String area,
            BlockCoord cell,
            Ground ground,
            PlanBuilding ownBuilding,
            boolean structureBlock) {
        if (structureBlock && ownBuilding == PlanBuilding.NONE) {
            return new Refused(
                    building,
                    area,
                    "клетка " + cell + " принадлежит постройке " + building + " (" + area + "), а действие не "
                            + "назвало свою постройку: без неё не отличить снос от чужого разбора");
        }
        if (structureBlock && PlanBuildings.foreign(ownBuilding, building)) {
            return new Refused(
                    building,
                    area,
                    "клетка " + cell + " принадлежит постройке " + building + " (" + area + "), а действие " + "строит "
                            + ownBuilding + ": чужую постройку плана не трогают");
        }
        return new Allowed(ground, building, area);
    }

    public static String basisText(Ground ground) {
        return switch (ground) {
            case NONE -> "без основания";
            case PLAN_DEMOLISH -> "снос/перестройка по плану";
            case REPAIR -> "ремонт к схеме";
            case PLAN_EXCAVATE -> "прокоп шахты по плану";
            case PLAN_BUILD -> "стройка по плану";
            case ORCHARD_CARE -> "уход за садом по плану";
            case SELF_RESCUE -> "самоспасение замурованного";
            case PLAYER_COMMAND -> "команда игрока";
            case TEMPORARY_MOVEMENT -> "временный проход с возвратом";
        };
    }
}
