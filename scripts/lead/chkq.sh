#!/usr/bin/env bash
# scripts/lead/chkq.sh <id> <ждать-id|-> <ветка>... — приёмка: worktree chk-<n> от <ветка> + merge main + unit + GameTest
id=$1; wait=$2; shift 2
out=/opt/minecraft/wt/chk-results; s=$out/summary-$id; : > $s
[ "$wait" != - ] && until grep -q '^DONE' $out/summary-$wait 2>/dev/null || grep -q "^DONE$wait" $out/summary$wait 2>/dev/null; do sleep 30; done
for b in "$@"; do
  n=${b#*/}; cd /opt/minecraft/the-mine-spirit
  git worktree add -q -b chk/$n /opt/minecraft/wt/chk-$n $b || { echo "$n worktree FAIL" >> $s; continue; }
  cd /opt/minecraft/wt/chk-$n
  git merge -q --no-edit main > $out/$n-merge.log 2>&1 || { echo "$n merge CONFLICT" >> $s; continue; }
  nice -n 19 bash scripts/test.sh unit > $out/$n-unit.log 2>&1; echo "$n unit EXIT=$?" >> $s
  THEMINESPIRIT_GAMETEST_WAIT=7200 nice -n 19 bash scripts/test.sh gametest > $out/$n-gt.log 2>&1; echo "$n gametest EXIT=$?" >> $s
done
echo DONE >> $s
