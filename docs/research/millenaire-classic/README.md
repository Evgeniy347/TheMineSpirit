# Millénaire (классика) — карта переиспользования

Исследование по локальному клону классического Millénaire от Kinniken
(`/opt/minecraft/research/millenaire`). Изменений в репозитории нет; это справочник идей для наших
подсистем семей/работ/склада/экономики/торговли/квестов/отношений/строительства/культур.
Наш проект — `The Mine Spirit`, NeoForge 1.21.1 / Java 21; ядро `com.theminespirit.core` без импортов
Minecraft. Дата анализа: 2026-10-11.

---

## Обзор (назначение, версия MC/загрузчик, Java, структура репо, лицензия)

**Назначение.** Классический Millénaire — мод, добавляющий самодостаточные NPC-деревни по мотивам
культур XI века (норманны, византийцы, индийцы, японцы, майя). Жители (мужчины/женщины/дети) имеют
работу, дом, семью, потребности, торгуют с игроком и между деревнями, строят и улучшают здания по
уровням, дают квесты, помнят репутацию и «отношения» между деревнями. Мир живёт автономно (расписания,
голы/цели, ночная/дневная активность).

**Версия и стек (решающие факты).**
- Мод: **Millénaire 6.0.1** — `mill/org/millenaire/common/forge/Mill.java:112` (`versionNumber = "6.0.1"`),
  `@Mod`-аннотация там же (`Mill.java:97`).
- Minecraft **1.7.10**, загрузчик **Forge / FML** (`cpw.mods.fml.*`, `net.minecraftforge.*`), эпоха
  `IIconRegister`, `LanguageRegistry`, числовых Block/Item ID. Java 6/7-эра (используются `final`-повсюду,
  diamond-оператор отсутствует).
- Это **не** современный NeoForge/1.21.1. Прямой перенос API невозможен, переносимы только идеи и форматы
  данных (см. «Портируемость»).

**Структура репозитория.** Три корня:
- `mill/` — Java-исходники (`mill/org/millenaire/client/*`, `mill/org/millenaire/common/*`) и ассеты
  (`mill/assets/millenaire/`). `common/` ≈ серверная логика, `client/` ≈ рендер/GUI.
- `millenaire/` — **контент и данные**: `cultures/` (5 культур: `norman`, `byzantines`, `hindi`,
  `japanese`, `mayan`), `quests/`, `goals/`, `languages/{en,fr}/`, `config*.txt`, `goods.txt`,
  `itemlist.txt`, `blocklist.txt`, `hof.txt`, `resources used*.txt`.
- `millenaire-custom/` — заготовка пользовательского контента (те же подкаталоги + `mods/`).

Git: один коммит `9b9607e "Minor fixes"`, remote `https://github.com/Kinniken/millenaire.git`. Два файла
моделей помечены `// Decompiled by Jad ... Copyright 2001 Pavel Kouznetsov` — признак смешанного
происхождения кода (частично декомпилирован).

### Лицензия (критично)

- В клоне **нет файла LICENSE/COPYING** ни в корне, ни в подкаталогах. По умолчанию это
  **all-rights-reserved**.
- Официальная страница (millenaire.org/contribute) и CurseForge описывают **custom proprietary license**:
  - «Millénaire as a whole, and all parts specifically developed by Kinniken, are copyrighted to Kinniken,
    **all rights reserved**, 2011–2026».
  - Использование мода и **некоммерческая** перепаковка в модпаки — разрешены при указании авторства и
    ссылки на millenaire.org.
  - **«Use of elements of the mod in other mods or other projects is forbidden without authorization»** —
    использовать элементы мода (код, данные, текстуры, blueprints, тексты) в других проектах запрещено.
- Это **не copyleft** (не GPL). Но лицензия **запретительная**: она не даёт прав на копирование/переработку.
  Вывод: **только clean-room** — читать и переосмысливать идеи, самостоятельно писать код и заново
  порождать данные. Ни строчки кода, ни контент-файлов копировать нельзя.
- Для контекста (это **другие** репозитории, не этот клон, лежат рядом в `/opt/minecraft/research/`):
  `millenaire-neoforge/LICENSE` — **GPL-3.0**, `millenaire-rewrite/LICENSE.txt` — **LGPL-2.1** (Forge).
  Это сторонние проекты; на классический клон их лицензии **не** распространяются.

## Разделы

| Файл | О чём |
|---|---|
| [architecture.md](architecture.md) | архитектура: жители, культура, экономика, торговля, квесты, стройка, AI, персистентность |
| [reuse.md](reuse.md) | что переиспользовать у нас, портируемость, рекомендации, лицензионные риски |

