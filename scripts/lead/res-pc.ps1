# Ресурсы ПК: печатает строки только при проблеме — мало свободной памяти, перегруз CPU,
# долгоживущий процесс bash/sh/python/java, который ест CPU, когда исполнителей opencode нет.
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$os = Get-CimInstance Win32_OperatingSystem
$freePct = [math]::Round(100 * $os.FreePhysicalMemory / $os.TotalVisibleMemorySize)
if ($freePct -lt 15) { "ПК: свободно памяти $freePct %" }
$cores = (Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors
$perf = Get-CimInstance Win32_PerfFormattedData_PerfProc_Process | Where-Object { $_.Name -notin '_Total','Idle' }
$total = ($perf | Measure-Object PercentProcessorTime -Sum).Sum / $cores
$opencode = @(Get-Process opencode -ErrorAction SilentlyContinue).Count
# Высокая загрузка при работающих исполнителях — норма (сборки с низким приоритетом); тревога — когда их нет.
if ($total -gt 90 -and $opencode -eq 0) { "ПК: CPU занят на $([math]::Round($total)) %, исполнителей нет" }
$now = Get-Date
foreach ($p in $perf) {
    if ($p.PercentProcessorTime -lt 50) { continue }
    $name = $p.Name -replace '#\d+$', ''
    if ($name -notin 'bash','sh','python','java','powershell','node') { continue }
    $proc = Get-Process -Id $p.IDProcess -ErrorAction SilentlyContinue
    if (-not $proc -or -not $proc.StartTime) { continue }
    $ageMin = [math]::Round(($now - $proc.StartTime).TotalMinutes)
    if ($ageMin -ge 120 -or ($opencode -eq 0 -and $ageMin -ge 30)) {
        "ПК: $name pid=$($p.IDProcess) ест $($p.PercentProcessorTime) % ядра, живёт $ageMin мин (opencode запущено: $opencode)"
    }
}
# Сироты: процессы исполнителей, у которых исчез родитель (сессия opencode закончилась, а find/bash/java остались).
# Демон Gradle переживает сессию намеренно — его не трогаем; node и powershell не трогаем (мост deepseek, свои мониторы).
$orphanNames = 'find.exe','grep.exe','bash.exe','sh.exe','timeout.exe','sleep.exe','tail.exe','head.exe','xargs.exe','java.exe','python.exe','opencode.exe'
$all = Get-CimInstance Win32_Process
$alive = @{}; foreach ($p in $all) { $alive[[int]$p.ProcessId] = $true }
foreach ($p in $all) {
    if ($orphanNames -notcontains $p.Name.ToLower()) { continue }
    if ($alive.ContainsKey([int]$p.ParentProcessId)) { continue }
    if ($p.CommandLine -like '*GradleDaemon*') { continue }
    # Родитель у процессов MSYS (Git Bash) в Windows часто «мёртвый», хотя логически жив — поэтому трогаем только
    # процессы нашего проекта (путь репозитория в командной строке) и поиск по всему диску (find / …).
    $cl = [string]$p.CommandLine
    if ($cl -like '*AppData*claude*' -or $cl -like '*oc-watch*' -or $cl -like '*keepawake*') { continue }
    $ours = ($cl -like '*repos/minecraft*') -or ($cl -like '*repos\minecraft*') -or ($p.Name -eq 'find.exe' -and $cl -match 'find(\.exe)?"?\s+/\s')
    if (-not $ours) { continue }
    $age = ($now - $p.CreationDate).TotalMinutes
    if ($age -lt 15) { continue }
    $cmd = if ($p.CommandLine) { $p.CommandLine.Substring(0, [Math]::Min(90, $p.CommandLine.Length)) } else { '' }
    & taskkill.exe /F /T /PID $p.ProcessId 2>&1 | Out-Null
    "ПК: убит сирота $($p.Name) pid=$($p.ProcessId), жил $([math]::Round($age)) мин: $cmd"
}
