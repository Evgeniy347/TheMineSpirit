# Ядро тактики

Пакет `com.theminespirit.core.tactics`: чистая функция «обстановка → решение» без Minecraft. Адаптер раз в
`EVAL_INTERVAL_TICKS` (10, предварительно) или по событию «ударили» собирает `Situation` и вызывает
`TacticsEngine.step(Situation, TacticsMemory, nowTick)`. Ответ — `TacticalOutcome(decision, action, retreatMethod,
callHelp, state, mode, returnTo, trace, memory)`; `memory` адаптер передаёт в следующий вызов. Числа правил живут в
четырёх JSON `src/main/resources/data/theminespirit/tactics/`, код их не дублирует.

Документ раздроблен по темам:

| Файл | О чём |
|---|---|
| [core.md](core.md) | конвейер вызова, схема JSON, факты, факторы, как добавить |
| [retreat.md](retreat.md) | способы отхода и их полезность |
| [trace.md](trace.md) | чтение trace и границы пакета |
| [adapter.md](adapter.md) | адаптер в игре: оценка, факты, исполнение, приказы, журнал решений |
| [threat.md](threat.md) | откуда берётся угроза |
| [intellect.md](intellect.md) | тактический интеллект поселения |
| [expedition.md](expedition.md) | организованный выход за границу |
| [transfer.md](transfer.md) | передача вещей |
| [defense.md](defense.md) | оборона поселения |

