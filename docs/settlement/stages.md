# Этапы поселения: условия перехода

Этап закрывается только **аудитом мира** против плана. Журнал доски (`BoardLedger`) ничего не закрывает: если
в мире постройки нет, повреждена она или чанк не загружен — это «не готово». Уровень считается выполненным,
если мир подтверждает этот уровень или более высокий (например, шахта 3 закрывает требование шахты 2).

## Откуда берётся «готово»

- Кейсы доски — `FirstStepsRunner.audit` (план уровня, `BuildGap`, обмер мира).
- Постройки плана — `GraphFacts.Building` из `BuildingAuditInputs` (`TownSnapshot.Progress`, `BuildingAudit`,
  `RepairAudit`); годна только запись с готовностью 100%, без недостачи и повреждений (`GraphStageGate.buildingReady`).
- Один сбой аудита не меняет этап: новая ступень принимается после трёх одинаковых аудитов подряд
  (`StageConfirmation`). Повреждённая обязательная постройка откатывает этап только после такого же подтверждения.

## Этапы

| Этап | Держат (обязательно) | Желательно (не держат) |
|---|---|---|
| 1 | `CLEAR_GROUNDS`, `BUILD_MINE` 1, `BUILD_FENCE` 1, `BUILD_ORCHARD` 1, `BUILD_HOUSE` 1, `STOCK_CHESTS` 1, `LIGHTING` 1, `BUILD_STREETS` 1 | — |
| 2 | `BUILD_MINE` 2, `BUILD_WAREHOUSE` 2, `BUILD_STREETS` 2; план: `plaza`, `family_house_a/b/c`, `storage`, `barn`, `field_a`, `field_b`, `pen`, `well`, `lumber_yard`, `workshop` | — |
| 3 | `BUILD_MINE` 3, `BUILD_WAREHOUSE` 3, `BUILD_STREETS` 2; план: `player_house`, `forge`, `bakery`, `barracks` | `kennel`, `stable`, `weaver` |
| 4 | `BUILD_MINE` 4, `BUILD_WAREHOUSE` 4; план: `town_hall`, `tavern`, `infirmary`, `training_yard` | `enchant_room`, `alchemy`, `library` |

Все уровни требований — 1. Данные — `mod/src/main/resources/data/theminespirit/board/stages.json`
(`buildings`, `requires`, `optional`, `plan`); планировщик читает `plan`, переход — `requires`/`buildings`,
желательные показываются отдельно (`optional` во вкладке этапов и в `GET /v1/settlement/stage`).

Пасека (`apiary`) в желательные не входит: её узел графа ставит улей, а не схему постройки плана, поэтому
готовность постройки `apiary` сейчас не подтвердить.

Ручной откат этапа (`POST /v1/settlement/stage`) снимает отметки журнала и задания высших этапов; мир при этом
не ломается, а новый этап снова подтверждается аудитом.
