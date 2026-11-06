# Фрагменты графа

Сюда кладут JSON-фрагменты графа: `items`, `buildings`, `groups`, `ways`, `edges`, `events`,
`triggers`, `herdCaps` и другие разделы в том же формате, что `board/graph.json`.

Java (`SupplyGraph.loadDefault`) и Python (`Graph.load_merged`) читают все `*.json` из этого
каталога после слияния `graph.json` + `graph-recipes.json`. Фрагмент добавляет только новые id:
повтор id — ошибка с путём файла. Поэтому разные исполнители правят разные файлы без конфликтов.
