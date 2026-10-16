#!/usr/bin/env bash
# Слежение главного агента за исполнителями opencode (docs/LEAD-AGENT.md → «Присмотр — инструменты»).
# Каждую минуту — новые завершения (EXIT=, ключ — имя .out и время его изменения); каждые 10 минут — зависшие
# (процесс жив, а логи сессии не пишутся дольше STALE_MIN минут), ресурсы ПК и domovoy (res-pc.ps1, нагрузка/память)
# и сироты исполнителей (res-pc.ps1, orphan-sweep.sh на сервере). Печатает только события.
# Запуск: инструмент Monitor (живёт 30 мин) → bash scripts/lead/oc-watch.sh; скрипт сам выходит за минуту до срока.
# Состояние «уже сообщено» — в $OC_SEEN (вне репозитория), переживает перезапуски.
seenf="${OC_SEEN:-/f/repos/minecraft/oc-results/oc-seen.txt}"
STALE_MIN=30
touch "$seenf"
finished() {
  ssh -o ConnectTimeout=10 domovoy@192.168.0.147 'cd /opt/minecraft/the-mine-spirit/TestResults/opencode && for f in $(find . -maxdepth 1 -name "*.out" -mmin -2880); do e=$(grep -a "^EXIT=" $f | tail -1); [ -n "$e" ] && echo "сервер ${f#./} $e #$(stat -c %Y $f)"; done' 2>/dev/null || true
  for f in $(find /f/repos/minecraft/oc-results -maxdepth 1 -name "l-*.out" -mmin -2880); do e=$(grep -a "EXIT=" "$f" | tail -1 | grep -o "EXIT=[0-9]*"); [ -n "$e" ] && echo "ПК $(basename $f) $e #$(stat -c %Y $f)"; done
}
stale() {
  ssh -o ConnectTimeout=10 domovoy@192.168.0.147 'cd /opt/minecraft/the-mine-spirit/TestResults/opencode && seen=; for d in $(ls -td */ | head -12); do w=$(grep -ho "/opt/minecraft/wt/[a-z0-9-]*" $d/attempt-*.jsonl 2>/dev/null | head -1); [ -z "$w" ] && continue; case " $seen " in *" $w "*) continue;; esac; seen="$seen $w"; pgrep -f -- "--dir $w" >/dev/null || continue; [ -z "$(find $d -mmin -'"$STALE_MIN"')" ] && echo "сервер ${w##*/}: логи не пишутся > '"$STALE_MIN"' мин"; done' 2>/dev/null | sort -u || true
  for w in /f/repos/minecraft/wt-*; do
    [ -d "$w/TestResults/opencode" ] || continue
    ps -ef | grep -v grep | grep -q -- "--dir F:/repos/minecraft/$(basename "$w")" || continue
    d=$(ls -td "$w"/TestResults/opencode/*/ 2>/dev/null | head -1)
    [ -n "$d" ] && [ -z "$(find "$d" -mmin -$STALE_MIN 2>/dev/null)" ] && echo "ПК $(basename "$w"): логи не пишутся > $STALE_MIN мин"
  done
}
res() {
  powershell -NoProfile -ExecutionPolicy Bypass -File "$(dirname "$0")/res-pc.ps1" 2>/dev/null
  ssh -o ConnectTimeout=10 domovoy@192.168.0.147 'f=$(free | awk "/Mem:/{print int(\$7*100/\$2)}"); [ "$f" -lt 15 ] && echo "domovoy: свободно памяти $f %"; l=$(cut -d" " -f1 /proc/loadavg); n=$(nproc); awk -v l=$l -v n=$n "BEGIN{if (l>n*2) print \"domovoy: нагрузка \" l \" на \" n \" ядер\"}"; oc=$(pgrep -c -f "opencode run" || true); ps -eo pid,etimes,pcpu,comm --no-headers | awk -v oc=$oc "\$3>50 && (\$4==\"bash\"||\$4==\"sh\"||\$4==\"python3\"||\$4==\"java\") && (\$2>7200 || oc==0) {print \"domovoy: \" \$4 \" pid=\" \$1 \" ест \" \$3 \" % ядра, живёт \" int(\$2/60) \" мин (opencode: \" oc \")\"}"' 2>/dev/null || true
}
# Монитор живёт 30 мин и не всегда убивает скрипт: выходим сами за минуту до срока, иначе осиротевшие копии
# молча забирают события себе (они уже записаны в seen).
deadline=$(( $(date +%s) + 1740 ))
tick=0
while [ "$(date +%s)" -lt "$deadline" ]; do
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    grep -qxF "$line" "$seenf" || { echo "$line"; echo "$line" >> "$seenf"; }
  done <<< "$(finished)"
  if [ $((tick % 10)) = 0 ]; then stale; res; ssh -o ConnectTimeout=10 domovoy@192.168.0.147 'bash /opt/minecraft/the-mine-spirit/scripts/lead/orphan-sweep.sh' 2>/dev/null || true; fi
  tick=$((tick + 1))
  sleep 60
done
