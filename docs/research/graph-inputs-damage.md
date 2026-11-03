# Недостающее и лишнее на входе графа (03.11)

Дополнение к [graph-inputs.md](graph-inputs.md). Владелец: граф решает по количествам, координаты остаются
исполнителю. Равнозначности — [SIMPLIFICATIONS](../SIMPLIFICATIONS.md) и `BlockMatch`.

## Постройки

`GraphFacts.Building`: `missing: Map<blockId, count>` — чего нет по схеме; `extra: Map<blockId, count>` — лишнее
внутри контура; `wrong` — сколько клеток занято не тем блоком; `damage = сумма missing + extra` для стоящей
постройки. `unknown=true` только пока чанки не загружены: тогда составы пусты, а этап открыт.

Источники — существующие аудиторы (`RepairAudit`, `BuildGap`, `PlotAudit`, `YardFloorWatch`), задач они не ставят.
Сундук, калитка/дверь, саженец=дерево, факел, улицы (`dirt/grass/path/coarse_dirt`) и трава на расчистке
считаются равными; руда в шахте не входит в недостачу.

`GraphBuildingMaterials` переводит блоки через `blockItems` в предметы. Материалы повреждённой постройки попадают
в `GraphState.materials`, путь `event:repair_<id>` потребляет ровно их. Вес ремонта растёт с долей
недостающего (`1 - auditPercent/100`) и важностью постройки для этапа (`stageReqs.minimum`).

## Лишнее и лоты

- `GraphFacts.Stray.blocks` — состав лишней постройки; `builder != null` — ставил дух, сносим; игрок — не трогаем.
- `GraphFacts.Terrain`: `cut` — срезать выше уровня лота, `fill` — подсыпать (`dirt`/`cobblestone`), `floating` —
  состав висящего. Срезка даёт предметы, подсыпка расходует; связь идёт через запас.
- События `demolish_stray`, `level_lot`, `backfill`, `clear_floating` срабатывают от составов, а не только от флагов.

## Гейт этапа

`GraphStageGate`: этап закрыт, когда у построек этапа `missing` пуст и `unknown=false`, у лотов `cut` и `fill`
пусты, а лишних построек духов нет. Python-модель повторяет: `missing → материалы → event:repair_<id>`.

Проверки: дыра 3 блока → `missing={dirt:3}` → нехватка `dirt` → ремонт выбран; равнозначности не дают
недостачи; руда в шахте не даёт недостачи; забор вне плана → `demolish_stray`; бугор/яма → `cut`/`fill`.
