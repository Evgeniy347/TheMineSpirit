Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Awake {
    [DllImport("kernel32.dll")]
    public static extern uint SetThreadExecutionState(uint esFlags);
}
"@
$ES_CONTINUOUS = [uint32]"0x80000000"
$ES_SYSTEM_REQUIRED = [uint32]"0x00000001"
$hours = if ($args.Count -gt 0) { [double]$args[0] } else { 12 }
$until = (Get-Date).AddHours($hours)
while ((Get-Date) -lt $until) {
    [Awake]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED) | Out-Null
    Start-Sleep -Seconds 60
}
