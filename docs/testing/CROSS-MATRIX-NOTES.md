# Матрица пересечений: итог и техдолг

Часть `docs/testing/CROSS-MATRIX.md`: итог покрытия и открытый техдолг.

## Итог

Механик — 24, неупорядоченных пар — 24·23/2 = 276: общий тест — 119, частично — 5, физически непересекающихся —
109, к доработке — 43. Парные сценарии — `CrossLifeWorkScenarioTests`, `CrossSettlementScenarioTests`,
`CrossRouteStageScenarioTests`, `CrossFleeScenarioTests`, `CrossBattleWorkScenarioTests`, `CrossHungerScenarioTests`.
Физически непересекающиеся пары размечены в `CROSS-MATRIX-NO-PAIR.md`, слабые пары — колонкой «сила» в
`CROSS-MATRIX.md`.

## Техдолг

- **6-24** (`CrossPathScenarioTests.path_UnknownFieldAndTerrain_GraphWaitsInsteadOfGuessing`): неизвестный
  рельеф всё равно получает `event:backfill:backfill`, хотя `plant_crop` и `building:hut` ждут загрузки чанка.
  Цена: засыпка вслепую может испортить участок. Предложение: `Terrain.unknown` запрещает засыпку, как посев.
