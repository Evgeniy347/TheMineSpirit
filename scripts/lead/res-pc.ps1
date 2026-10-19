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
# Сироты исполнителей. У процессов Git Bash (MSYS) Windows часто показывает «мёртвого» родителя, хотя логически он жив
# (16.10 так убиты чужой tail -F и рабочий запуск opencode deepseek-web), поэтому по родителю не судим. Убиваем только:
# 1) find.exe с поиском по всему диску (find / …) старше 15 мин; 2) java сервера GameTest (fml.modFolders=…wt-<имя>)
# старше 15 мин, если в этом worktree не работает ни один opencode. Демон Gradle не трогаем.
$all = Get-CimInstance Win32_Process
$opencodeDirs = @($all | Where-Object { $_.Name -eq 'opencode.exe' } | ForEach-Object {
    if ($_.CommandLine -match '--dir\s+(\S+)') { $Matches[1].ToLower() } })
foreach ($p in $all) {
    $cl = [string]$p.CommandLine
    if (-not $p.CreationDate) { continue }
    $age = ($now - $p.CreationDate).TotalMinutes
    if ($age -lt 15) { continue }
    $kill = $false
    if ($p.Name -eq 'find.exe' -and $cl -match 'find(\.exe)?"?\s+/\s') { $kill = $true }
    if ($p.Name -eq 'java.exe' -and $cl -notlike '*GradleDaemon*' -and $cl -match 'fml\.modFolders=\S*?(wt-[A-Za-z0-9-]+)') {
        $wt = $Matches[1].ToLower()
        if (-not ($opencodeDirs | Where-Object { $_ -like "*$wt*" })) { $kill = $true }
    }
    if (-not $kill) { continue }
    $cmd = $cl.Substring(0, [Math]::Min(90, $cl.Length))
    & taskkill.exe /F /T /PID $p.ProcessId 2>&1 | Out-Null
    "ПК: убит сирота $($p.Name) pid=$($p.ProcessId), жил $([math]::Round($age)) мин: $cmd"
}
