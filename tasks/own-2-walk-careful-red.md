# own-2 — GameTest `walk_careful`, который краснеет на подлоге

Источник: `todo.md` → «`walk_careful` вдоль пропасти: GameTest не краснеет на подлоге (ванильный поиск и так идёт
по краю)». Ветка `task/own-walk-careful`, каталог `F:\repos\minecraft\wt-own-walk-careful`.

## Проблема
`MovementCarefulGameTests.walkCareful_SixBlocksAlongFourDeepChasm_ArrivesAndAlwaysHasFloorBelow` проверяет, что
под духом всегда есть пол, пока он идёт вдоль пропасти. Если заменить `walk_careful` обычной ходьбой (`goto`),
тест остаётся зелёным: ванильный поиск пути и так не прыгает в пропасть глубиной 4, когда рядом есть ровный путь.
Тест ничего не доказывает.

## Что сделать
1. Найди сценарий, в котором осторожный путь (`CarefulPath`, `DropProfile.MAX_SAFE_DROP`) **отличается** от
   обычного (`goto`, ванильная навигация). Варианты для проверки:
   - короткий путь через спуск глубже `MAX_SAFE_DROP` (ваниль спрыгивает до 3 блоков с уроном или без, а
     осторожный путь обходит длинной лестницей или тропой);
   - узкий карниз шириной 1 над обрывом против обхода по широкой дороге;
   - цель за ямой: ваниль прыгает через неё, осторожный шаг обходит.
   Сначала проверь гипотезу: запусти обе ходьбы и залогируй путь (`Path`/`Node`, как в существующем тесте).
2. Напиши GameTest (или переделай существующий) так, чтобы:
   - с `walk_careful` он зелёный (дух дошёл, ни разу не падал больше чем на `MAX_SAFE_DROP`, урона от падения нет);
   - с подменой на `goto` (поменять одну строку `pointCall(helper, "walk_careful", …)` на `"goto"`) он **красный**.
     Проверь это руками и опиши в сообщении коммита.
3. Если такого сценария найти не удалось, не подгоняй тест под результат. Переименуй тест в регрессионный
   (например, суффикс `_Regression`), добавь в javadoc пометку, почему он не различает подлог, и запиши вывод в `todo.md`.
4. Шаблон `arena` 11×8×11, `timeoutTicks` ≤ 400 (сторож `GameTestTimeoutGuardTests`), уборка через `SavedBlocks`
   / `MovementScene.finish`, как в соседних тестах. Имена тестов — по `docs/TESTING.md`.

## Где код
`src/main/java/com/theminespirit/gametest/MovementCarefulGameTests.java`, `MovementScene`, `SpiritRun`;
`neoforge/CarefulPath`, `core/movement/DropProfile`, `core/script/WalkCarefulParams`, `neoforge/GotoScript`.

## Приёмка
- Точечный прогон: `bash scripts/gametest.sh` (на ПК без flock — пока через `./gradlew runGameTestServer`, один
  сервер на машину). Новый тест зелёный 3 прогона подряд.
- В сообщении коммита: «на goto красный: <текст ошибки>».
- `todo.md`: пункт перенесён в «Сделано» с хэшем (или с выводом из п. 3).

## Не трогать
Логику `CarefulPath`. Если окажется, что она неверна, опиши это в `todo.md` отдельным пунктом.

## Итог (2026-10-15)

Выполнено. Сценария, где `walk_careful` зелёный, а подлог на `goto` красный, **не существует** при текущей
реализации: `walk_careful` идёт тем же ванильным путём, что и `goto`, а `CarefulPath.refusal` лишь отклоняет
уже построенный путь. Ванильный `WalkNodeEvaluator.tryFindFirstGroundNodeBelow` (исходник Minecraft 1.21.1)
сам ограничивает спуск `mob.getMaxFallDistance()`, а у духа он равен `DropPolicy.MAX_DROP = 2`, поэтому
pathfinder не строит путь с падением глубже `MAX_SAFE_DROP` и `CarefulPath.refusal` на реальных путях
всегда `null`.

Гипотеза проверена диагностическими GameTest (временные, удалены): ванильный путь для ям 1×4 и 2×4 глубиной 4
обходит пропасть, спускаясь максимум на 1–2 блока, никогда не прыгает и не падает глубже; плато высотой 3
даёт `canReach=false` и недостижимо для обоих режимов.

Тест `walkCareful_SixBlocksAlongFourDeepChasm_ArrivesAndAlwaysHasFloorBelow` оставлен регрессионным
с комментарием-обоснованием; различие режимов покрывает синтетический
`carefulPath_SyntheticPaths_RefusesOnlyDeepDropsUnreachableAndMissing`. Суффикс `_Regression` не добавлен —
его запрещает `TestNamingGuardTests` (имя строго из трёх частей); пометка о причине — в комментарии и в `todo.md`.

Проверки: `./gradlew test` — зелёный (3850 тестов); GameTest — 6 прогонов из 6 зелёные,
`All 439 required tests passed`. Коммит: `137e537` (`test: walk_careful вдоль пропасти — регрессионная пометка
и вывод о недостижимости подлога`). Логика `CarefulPath` не менялась.
