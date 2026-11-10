package com.theminespirit.core.intellect.defense;

import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.STAGE_THREE;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.caseOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.pair;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.ring;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.squad;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.zombies;

import java.util.List;

final class TeamDefenseCircleHelpCases {
    private static final List<TeamDefenseScenarios.SquadSpec> RING_THREE = ring(STAGE_THREE, 8);

    private TeamDefenseCircleHelpCases() {}

    static List<TeamDefenseScenarios.Case> circleHelpCases() {
        return List.of(
                caseOf(
                        "C11",
                        "этап 3: двенадцать мобов с севера — две соседние пары идут на помощь",
                        RING_THREE,
                        List.of(zombies(12, 0, -100)),
                        "S4",
                        "ENGAGE",
                        "S2",
                        "REINFORCE",
                        "S6",
                        "REINFORCE",
                        "S1",
                        "STAY",
                        "S8",
                        "STAY"),
                caseOf(
                        "C12",
                        "малый гарнизон: три поста на севере, западе и юге — каждый держит свою сторону",
                        List.of(pair(0, -90), pair(-90, 0), pair(0, 90)),
                        List.of(zombies(4, 0, -100), zombies(4, -100, 0), zombies(4, 0, 100)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "ENGAGE",
                        "S3",
                        "ENGAGE"),
                caseOf(
                        "C14",
                        "сбежались в одну точку, мобы с востока: отряд идёт к восточному сектору",
                        List.of(squad("g", 0, 0, 8, 1.0, 10, false, 6.0, 1.0)),
                        List.of(zombies(8, 100, 0)),
                        "S1",
                        "REINFORCE"),
                caseOf(
                        "C15",
                        "сбежались в одну точку, четыре моба с востока: один отряд всё равно идёт к сектору",
                        List.of(squad("g", 0, 0, 4, 1.0, 10, false, 6.0, 1.0)),
                        List.of(zombies(4, 100, 0)),
                        "S1",
                        "REINFORCE"),
                caseOf(
                        "C16",
                        "сбежались в одну точку, мобы с севера и юга: отряд расходится по двум секторам",
                        List.of(squad("g", 0, 0, 8, 1.0, 10, false, 6.0, 1.0)),
                        List.of(zombies(5, 0, -100), zombies(5, 0, 100)),
                        "S1",
                        "SPREAD"),
                caseOf(
                        "C17",
                        "сбежались в одну точку, мобы с трёх сторон: отряд расходится по трём секторам",
                        List.of(squad("g", 0, 0, 8, 1.0, 10, false, 6.0, 1.0)),
                        List.of(zombies(5, 100, 0), zombies(5, -100, 0), zombies(5, 0, 100)),
                        "S1",
                        "SPREAD"),
                caseOf(
                        "C18",
                        "кольцо с проломом на востоке, четыре моба с северо-востока: ближний северный пост идёт в пролом",
                        List.of(pair(0, -90), pair(-90, 0), pair(0, 90)),
                        List.of(zombies(4, 70, -70)),
                        "S1",
                        "STAY",
                        "S2",
                        "REINFORCE",
                        "S3",
                        "STAY"),
                caseOf(
                        "C19",
                        "мирный периметр: все посты стоят на своих местах",
                        RING_THREE,
                        List.of(),
                        "S1",
                        "STAY",
                        "S4",
                        "STAY",
                        "S5",
                        "STAY",
                        "S8",
                        "STAY"));
    }
}
