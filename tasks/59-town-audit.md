# 59. Аудит поселения: один кэш, группы клеток, задачи через граф

ТЗ утверждено 27.11 (`verdant-vent-bass.md`). Четыре пересчёта с нуля (`RepairAudit`, `BuildGap`,
`SettlementScan`/`WorldAreaScan`, `SchematicClearance`) схлопываются в одну подсистему: RAM-кэш чанков,
индекс ожиданий из плана, группы «клетка + действие», выдача в граф, дух выполняет готовую группу и не
пересчитывает мир.

## Источник истины

Ожидания — карта поселения + схемы этапов (`StageCatalog`), уровни — `SettlementPlot.anchor().y()`.
Факт — `ChunkCache` (короткие коды из `BlockDict`), наполняется по загрузке чанка и правится хуками
изменений. Разрешения (`BlockProtection`, аренды, сад) отвечают только «разрешено ли сломать», клетка не
становится невидимой.

## Инварианты (каждый — тест или сторож)

- И-1: клетка поселения — ровно в одной группе, группа — ровно в одном чанке.
- И-2: тактика не строит план из мира (сторож на `StageBuilds.of(`, `TicketPlans.plan(`, `RepairAudit.audit(`,
  `BuildGap.trace(`, `SettlementScan.` в `mech/building`, `SchematicSite`, `SchematicClearance`,
  `SchematicWorkPhase`).
- И-3: любое расхождение кэша → `AUDIT-MISS` с классификацией, группа в `QUARANTINED`, перезаливка только
  своего чанка; полного пересчёта поселения инцидент не вызывает.

## Блоки

1. [x] Пакет `core/board/audit`: `Wanted`, `Access`, `SourceRef`, `DiffItem`, `Group`, `Expectation`,
      `BlockDict`, `ChunkCache`, `ChangeRing`, `BlockEquivalence`, `AuditMiss` + юнит-тесты.
2. [x] Id улиц возвращаются в план: `TownMap.MapStreet` → `List<Street>`, `TownMapRules`, `TownStreets`,
      `TownPlanCodec` и ~14 потребителей маски.
3. [x] `ExpectIndex` по плану (участки, улицы, земля, шахта) + один источник уровня (`levelY` параметром).
      Отложено: конверт стены (`wall:*` как отдельный источник схем) — вместе с группой стены в блоке 7.
4. [x] Наполнение `ChunkCache` по `ChunkEvent.Load`, бюджеты в `data/.../audit.json`, `planRevision`.
5. [x] Инвалидация: `BlockChangeWatch` + `TickQueue` (1/с) + `ChangeRing`; каскад «свой билет не грязним».
6. [x] Фоновая сверка по спирали + `AUDIT-MISS` в три канала + классификация причин.
7. [x] Группы и статусы в реестре, вид клиента (`ConstructionAuditService`) и REST.
8. [x] Граф: шаблон `audit`, маппинг в `BoardCase`, поля `after`/`requires`, префиксный `find`, веса в данных,
      тесты порядка и сторожи.
9. [x] Билет: `GroupRef` в `TicketTarget`, починка потери `plan` при повторной постановке,
      `StageBuilds.of` вне пути выдачи аудита.
10. [x] Тактика выполняет `DiffItem` с одиночной проверкой факта; `SchematicClearance.missing()/toClear()`
       вне боевого пути аудита; `StrayGroups`/`PitGroups` заменены подгруппами `ground`.
11. [ ] Удаление старого: `WorldAreaScan.LAST/Sliced/CACHE_TICKS`, `BuiltAuditCache`, `BuildingAudit.SCHEMES/GAPS`,
       `RepairAuditWatch`, `WorldAreaInputs.cachedHouse`, `auditIntervalTicks`; миграция ~15 тест-классов.

## Приёмка

`scripts/test.sh unit` и `gametest` зелёные; `Perf` (`world.area`, `graph.in.world.audit`) не хуже базлайна;
живая проверка на копии мира (`scripts/dev-server.sh up`) без клиента: `/v1/world` отдаёт полный набор групп,
изменение блока игроком даёт `AUDIT-MISS` и возврат группы в работу. Публикация и перезапуск сервера — только
по согласованию владельца.
