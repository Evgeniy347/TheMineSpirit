# Каталог случаев графа

Здесь закреплены живые случаи, из которых выросли правила. Тест — доказательство: случай закрыт, когда
его тест краснел до правки и зеленеет после.

## Л. Неудачи

| Случай | Наблюдение | Правило | Тест |
|---|---|---|---|
| Л1 | Один недоступный ствол рубил узел везде: провалы всех целей складывались в штраф узла | Штраф узла — только по разным целям, порог `failureTargets` | `GraphChoiceFeedbackTests.failureBackoff_OneUnreachableTarget_DoesNotPenalizeWholeNode`, `GraphChoiceFeedbackTests.failureBackoff_ManyDistinctTargets_LowerTaskWeight` |
| Л2 | «Чанк не загружен» штрафовал как «нет пути», хотя чанк подгружается | `CHUNK` не штраф | `GraphChoiceFeedbackTests.failureBackoff_ChunkFailure_NoPenalty` |
| Л3 | «Нет материала» гнал работу прочь вместо добычи входа | `NO_MATERIAL` — нехватка на входе снабжения, не штраф узла | `GraphChoiceFeedbackTests.failureBackoff_NoMaterial_NoNodePenalty`, `GraphChoiceFeedbackTests.failureBackoff_NoMaterial_KeepsSupplyInputWork` |
| Л4 | Неизвестный провал или провал действия не отличался от доказанной недостижимости | `OTHER` — мягкий штраф весом `failureSoft` | `GraphChoiceFeedbackTests.failureBackoff_OtherCause_SoftPenalty` |
| Л5 | Успех по дереву не сбрасывал старый провал; штраф обрывался окном | Успех цели сбрасывает счётчик; вес затухает по `failureHalfLife` | `GraphFailureMemoryTests.succeed_SameTarget_ResetsItsCounter`, `GraphFailureMemoryTests.facts_OldFailures_DecayInsteadOfHardWindow` |
| Л6 | После провала дальняя дорога удлинялась всему узлу | `farTrip` — только у оштрафованного узла или при плохой погоде | `GraphChoiceFeedbackTests.avoidFarTrip_OneUnreachableTarget_KeepsTravel` |
| Л7 | Недоступный ствол повторяли снова и снова | Недостижимая цель исключается, рубка переходит к другому дереву | `FailureTargetsTests.choose_FirstTargetUnreachable_TakesAnotherTarget`, `FailureTargetsTests.chop_UnreachableJungleTree_KeepsNodeAndPicksAnotherTree` |
| Л8 | Защита жизни и спасение духа теряли вес из-за провала | Узлы жизни не штрафуются: меняется способ, не вес | `GraphChoiceFeedbackTests.failureBackoff_LifeEvent_NotPenalized`, `GraphChoiceFeedbackTests.failureBackoff_RescueSpirit_NotPenalized` |
