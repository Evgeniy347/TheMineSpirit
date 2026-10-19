# LLM-агенты, навыки и обучение — карта переиспользования

Исследование четырёх локально склонированных LLM-агентных проектов под Minecraft.
Цель — извлечь переиспользуемые подходы к наблюдению, промптам, памяти, библиотеке навыков,
авто-куррикулуму, верификации и формату плана, и соотнести их с ядром `com.theminespirit.core`
(`ScriptCall` / `SequenceNode` / `FallbackNode`, `PlanParser`, `LlmPlanner`, `ScriptCatalog`).

> Статус: только анализ. Код проектов не менялся. Файлы репозиториев не редактировались.
> Все пути ниже — относительно корня соответствующего репозитория.

## Обзор репозиториев

| Репозиторий | Назначение | Стек | LLM / провайдеры | Лицензия |
|---|---|---|---|---|
| `mindcraft` | Фреймворк автономных ботов на mineflayer; multi-agent, режимы поведения, tool/skill-команды | Node.js (ESM), mineflayer, socket.io, express, ESLint | Сменные профили в `src/models/*.js`: OpenAI, Claude, Gemini, Grok, Groq, Ollama, vLLM, OpenRouter, DeepSeek, Mistral и др. | **MIT** (© 2024 Kolby Nottingham) |
| `mc_aiplayer` | Серверный Fabric-мод: «LLM выбирает намерение, goal engine планирует, 34 детерминированные task-машины исполняют» | Java 21, Fabric 1.21.3, Gradle; ~197 классов main, отдельный gametest source set | DeepSeek через OpenAI-совместимый `/v1/chat/completions`, tool calling, `thinking`-блок | **MIT** (© 2026 zoyluo) |
| `mc-ai-agent-mod` | Клиентский Fabric-мод: skill-based агент, natural-language планирование, tool calling; вызов скиллов также по WebSocket | Java 21/25, Fabric (1.21.11 и 26.2), multi-module Gradle (common/platform/cloud-agent) | Любой OpenAI-совместимый Chat Completions + WebSocket `skill_call`/`skill_result` + поиск (Google/百度/Bing) | **AGPL-3.0** — **copyleft** |
| `voyager` | Lifelong-learning агент: авто-куррикулум, растущая библиотека скиллов, итеративный промптинг, self-verification | Python + LangChain + Chroma; JS-мост на mineflayer (env/mineflayer) | OpenAI GPT-4 (action/critic/curriculum), GPT-3.5 (skill manager/QA), OpenAI Embeddings | **MIT** (© 2023 MineDojo Team) |

Дополнительно: `voyager/voyager/env/mineflayer/mineflayer-collectblock` — вложенный пакет под своей лицензией
(проверять отдельно); `mc-ai-agent-mod` в `THIRD_PARTY_NOTICES.md` явно указывает, что навигация
вдохновлена `cabaletta/baritone` (**LGPL-3.0+**), но реализована независимо и не линкует Baritone.

---

## Разделы

| Файл | О чём |
|---|---|
| [patterns.md](patterns.md) | архитектура и паттерны: наблюдение, промпты, память, навыки, куррикулум, верификация, оркестрация |
| [reuse.md](reuse.md) | формат плана, что переиспользовать у нас, рекомендации, лицензионные риски |

