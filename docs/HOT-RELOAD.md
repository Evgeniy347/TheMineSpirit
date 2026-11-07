# Горячая перезагрузка таблиц данных

Таблицы ценностей, коэффициентов и правил (ценность предметов, броня, корма, мечи, наборы, добыча, политики,
постройки, схемы доски, доска задач, двери, тактика, голод, рыбалка, спуск и расширение шахты, приоритеты обороны) читаются
не из classpath один раз, а из **ресурс-менеджера сервера**: их можно поменять
на живом сервере командой `/reload`, без перезапуска и без установки нового мода.

## Как поменять коэффициент на лету

1. В мире сервера лежит каталог датапаков: `<каталог мира>/datapacks/` (например `world/datapacks/`).
2. Создайте там свой датапак, например `world/datapacks/mine/pack.mcmeta`:

   ```json
   { "pack": { "pack_format": 48, "description": "мои коэффициенты" } }
   ```

3. Положите изменённый файл **с тем же путём**, что у файла мода, но под своим датапаком:

   ```
   world/datapacks/mine/data/theminespirit/armor/armor_table.json
   ```

   Путь внутри датапака повторяет `src/main/resources/data/theminespirit/...` из мода. Полный список файлов —
   в таблице ниже.
4. Выполните ванильную команду `/reload` (нужны права оператора). Таблица перечитается; старый дух и его задача
   не пересоздаются — новые числа применяются к следующей оценке.
5. Проверьте результат командой `/spirit tables`: у таблицы будет виден источник (`датапак`), время загрузки и
   хэш содержимого. Если что-то не так — там же будет строка с ошибкой.

Файл с тем же путём под датапаком мира **переопределяет** файл мода. Файл, которого нет в датапаке, остаётся
модским.

## Что грузится на лету

| id в `/spirit tables` | Путь под `data/theminespirit/` | Что содержит |
|---|---|---|
| `item_values` | `inventory/item_values.json` | ценность предметов и категории |
| `base_kit` | `inventory/base_kit.json` | базовый набор духа |
| `kit_activities` | `inventory/kit_activities.json` | наборы по деятельностям |
| `inventory_norm` | `inventory/inventory_norm.json` | кривые спроса «освободить инвентарь» и пороги удержания задачи по деятельностям |
| `armor` | `armor/armor_table.json` | порядок материалов брони, порог износа |
| `feed` | `husbandry/feed.json` | чем кормить вид |
| `sword` | `husbandry/sword.json` | тир мечей |
| `drops` | `acquire/drops.json` | «предмет ← блоки-источники» |
| `protection` | `policy/protection.json` | защищённые и природные блоки, порог «здания» |
| `presets` | `schematics/presets.json` + тела схем | библиотека построек (индекс и тела схем — одна таблица) |
| `board` | `board/board.json` | расписание доски задач |
| `graph` | `board/graph.json` | граф потребностей доски: пороги запасов, пути добычи, постройки, события |
| `doors` | `doors/doors.json` | вечерний порог и параметры дверей |
| `tactics` | `tactics/{enemies,weights,rules,modes}.json` | ядро тактики (четыре файла — одна таблица) |
| `adapter` | `tactics/adapter.json` | интервал оценки тактики, радиус фактов и т. п. |
| `intellect` | `tactics/intellect.json` | коэффициенты тактического интеллекта: интервалы пересборки и отката, гистерезис, цели запасов, паёк, пауза между походами, пороги боя |
| `hunger` | `hunger/hunger.json` | пороги, интервалы и расходы голода духа |
| `respawn` | `base/respawn.json` | задержка возрождения погибшего духа |
| `fishing` | `fishing/fishing.json` | ширина и глубина воды для разовой ловли и рабочей точки, предел рыбы в вёдрах, порог коптильни |
| `descent` | `mining/descent.json` | целевой этаж спуска и интервал факелов |
| `descent_levels` | `mining/descent_levels.json` | ширина и высота расширения шахты, ширина ступенек |
| `priorities` | `tactics/priorities.json` | классы целей обороны и порядок их выбора по ролям |
| `processing` | `processing/processing.json` | цели запасов и коэффициенты переработки (уголь, доски, палки, готовка) |
| `schematic_house` | `schematics/house.json` | схема дома поселения |
| `schematic_fence` | `schematics/fence.json` | схема забора |
| `schematic_pen` | `schematics/pen.json` | схема загона для животных |
| `schematic_enchant_room` | `schematics/enchant_room.json` | схема чаровальни |

Каталог `structure/*.nbt` — бинарные шаблоны структур для GameTest, а не таблица коэффициентов; он не
перезагружается.

## Ошибка в файле

Если изменённый json битый (не разобран, неизвестное поле, число вне границ), то:

- в лог сервера идёт строка `ERROR` с именем таблицы и причиной;
- в ответе `/spirit tables` таблица помечается как ошибочная;
- **прежняя версия таблицы остаётся в силе** — сервер не падает и не подменяет таблицу пустой.

Это не «тихий fallback»: ошибка видна и в логе, и в команде. Исправьте файл и повторите `/reload`.

## Как это устроено

- `com.theminespirit.core.reload.TableHolder` — держатель текущей версии одной таблицы. Читатели берут
  `current()`; загрузчик заменяет версию целиком одной атомарной записью ссылки. Неудачная загрузка зовёт
  `failed(...)`: прежняя версия в силе, статус получает текст ошибки.
- `com.theminespirit.core.reload.TableRegistry` / `DataTables` — единственное место регистрации. `DataTables`
  держит типизированные держатели всех таблиц; код ядра (`TacticsConfig.loadDefault()`, `InventoryTables.values()`,
  `FeedTable.feedFor`, `DropTable.shared()`, `DescentData.loadDefault()`, `DefenseProfile.loadDefault()` и т. д.)
  отдаёт `holder.current()`, а не читает classpath сам.
- `com.theminespirit.neoforge.DataReload` — единственная подписка на `AddReloadListenerEvent`. На каждый
  `/reload` он читает json из серверного `ResourceManager` (мод + датапаки мира), парсит и кладёт в держатели;
  ошибка одной таблицы не мешает остальным.
- `com.theminespirit.neoforge.SchematicResources` — загрузчик схем по имени для `build_schematic`: для схем,
  зарегистрированных в `DataTables`, отдаёт `holder.current()`, остальные (тела пресетов) читает из classpath.
- `com.theminespirit.neoforge.SpiritTablesCommand` — команда `/spirit tables`.

## Проверка

- JUnit: `TableRegistryTests` — замена версии, атомарность, «неудачная загрузка оставляет старую», отчёт по
  ошибке, независимость таблиц. `SchematicResourcesTests` — загрузчик схем (`build_schematic`) читает тело
  зарегистрированной схемы из держателя, подмена в реестре видна потребителю, битый json оставляет прежнюю схему.
  `IntellectTuningTests` — таблица `intellect`: файл совпадает с умолчаниями, подмена видна потребителю
  (`TaskBackoff`), битый json оставляет прежнюю версию, диапазоны полей отвергаются с именем поля.

## Граница GameTest

В GameTest-сервере (каталог `build/gametest-run`) датапак, положенный в `world/datapacks` **после** старта
сервера, не обнаруживается ванильным `FolderRepositorySource` (проверено: `getAvailableIds()` не содержит
`file/*`). Поэтому GameTest проверяет перезагрузку через **тот же слушатель `DataReload` и настоящий серверный
`MultiPackResourceManager`**: текущие выбранные паки сервера плюс датапак теста поверх. Ванильный `/reload`
на выделенном сервере идёт по тому же пути (слушатель `AddReloadListenerEvent` + ресурс-менеджер).