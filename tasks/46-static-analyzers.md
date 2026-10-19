# Чеклист 46. Строгие статические анализаторы (владелец 19.10)

Решение владельца: анализаторы роняют сборку; все ошибки и предупреждения — техдолг до нуля; временный список
нарушений — только на время разбора и сокращается до пустого. Неуместные для проекта правила выключаются в
конфигурации набора с причиной (не точечным подавлением). Версии выбрал главный агент (поручение владельца):
последние стабильные не свежее 2 недель, совместимые с Gradle 9.2.1 / Java 21 / ModDevGradle 2.0.148 — проверено замером.

## Версии (в gradle.properties, через DependencyVersionGuardTests)

Error Prone 2.50.0 + плагин `net.ltgt.errorprone` 5.1.1; NullAway 0.14.2 + JSpecify 1.0.1 (compileOnly);
Checkstyle 14.3.0; PMD 7.28.0; SpotBugs 4.10.4 + плагин `com.github.spotbugs` 6.5.12; ArchUnit 1.5.1;
Spotless 8.10.3 + palantir-java-format 2.102.0 (отступ 4, как у нас; google-java-format отвергнут — отступ 2).

## Замер (19.10, загруженная машина; база compileJava 19–24 с)

| Инструмент | Время | Нарушений main / test |
|---|---|---|
| Spotless + palantir | 4–21 с | 1688 файлов меняются (автоматически) |
| Error Prone | compileJava ×3,5 (68–96 с) | 161 / 96 |
| NullAway (`core`, main) | 33 с | 291 в 99 файлах |
| Checkstyle, минимальный набор | 35 с | 261 / 25 |
| PMD (после выключения неуместных) | ~59 с | ~925 / ~235 |
| SpotBugs (после выключения EI/MS_EXPOSE_REP) | 81 с | ~71 |

## Выключить в конфигурации (с причиной)

- Checkstyle: не `google_checks`, а минимальный набор — DeclarationOrder, ParameterNumber (max 7), MethodLength,
  MissingSwitchDefault, EqualsHashCode. Indentation/LineLength/ImportOrder — работа форматтера; MissingJavadoc
  противоречит правилу «без Javadoc»; имена тестов `subject_Condition_Expected` — наше правило.
- PMD: AvoidFieldNameMatchingMethodName (стиль аксессоров record), TestClassWithoutTestCases (GameTest в main),
  UnitTestContainsTooManyAsserts, GuardLogStatement (slf4j `{}`), AvoidLiteralsInIfCondition, AvoidDuplicateLiterals
  для тестов, NullAssignment (это NullAway), CompareObjectsWithEquals (это Error Prone ReferenceEquality).
- Error Prone: UnnecessaryLambda — EventBus NeoForge снимает слушателя только по тому же экземпляру.
- NullAway — только main, `AnnotatedPackages=com.theminespirit.core` (тесты намеренно передают null).
- SpotBugs EI_EXPOSE_REP/REP2/MS_EXPOSE_REP — после проверки выборки 20 шт. (record + неизменяемые копии).

## Порядок (каждый пункт — блок, сборка падает только на НОВЫХ нарушениях, временный список → 0)

1. [ ] Spotless: один механический коммит `spotlessApply` + `.git-blame-ignore-revs`, `spotlessCheck` в `check`.
   Только после слияния параллельных веток (иначе конфликты в 1688 файлах).
2. [ ] Error Prone: подключить; патч `-XepPatchChecks` всех найденных, кроме UnnecessaryLambda и StringSplitter (Guava),
   с ревью диффа (~200); руками — ReferenceEquality через `stack.is(item)`/`state.is(block)`, StringSplitter через
   `split(regex, -1)`, EnumOrdinal, ReturnValueIgnored (`BoardConfig.java:256`) и прочие (~60). Временный список —
   `-Xep:<Check>:OFF` по ещё не исправленным, до пустого.
3. [ ] NullAway в `core`: `@Nullable` (JSpecify) там, где null реально бывает, иначе убрать null; 3–4 блока по подпакетам
   (engine+script; task+board; observation+tactics+intellect; rest и прочее).
4. [ ] Checkstyle минимальный: DeclarationOrder 200 (перестановка), MissingSwitchDefault 49, ParameterNumber 35
   (объект-параметр, согласовать с MethodShapeGuard), MethodLength — порог ближе к ~40 строк CONVENTIONS (решить по замеру).
5. [ ] PMD: свой ruleset.xml; LiteralsFirstInComparisons, CloseResource (ревью), AvoidDuplicateLiterals (main) и прочее —
   3–4 блока по пакетам; храповик `maxFailures` до 0.
6. [ ] SpotBugs: ~71 после исключения EI/MS — 1–2 блока; фильтр только с постоянными причинами.
7. [ ] ArchUnit: переписать `CoreIsolationTests` и регулярные сторожа зависимостей на правила ArchUnit.
8. [ ] После каждого пункта — сторожа/абзацы, которые инструмент заменил, удалить (tasks/45, принцип AGENTS.md).

Время сборки: Error Prone — главная цена (×3,5 компиляции); если мешает исполнителям — измерить с `workers.max`
и решить с владельцем (например, Error Prone только в `check`, а не в каждой компиляции).
