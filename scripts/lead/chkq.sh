#!/usr/bin/env bash
id=$1; wait=$2; shift 2
repo="${THEMINESPIRIT_CHKQ_REPO:-/opt/minecraft/the-mine-spirit}"
wt="${THEMINESPIRIT_CHKQ_WT:-/opt/minecraft/wt}"
out="${THEMINESPIRIT_CHKQ_OUT:-$wt/chk-results}"
mkdir -p "$out" "$wt"
s=$out/summary-$id; : > $s
[ "$wait" != - ] && until grep -q '^DONE' $out/summary-$wait 2>/dev/null || grep -q "^DONE$wait" $out/summary$wait 2>/dev/null; do sleep 30; done
for b in "$@"; do
  n=${b#*/}; cd "$repo"
  git worktree add -q -b chk/$n $wt/chk-$n $b || { echo "$n worktree FAIL" >> $s; continue; }
  cd $wt/chk-$n
  git merge -q --no-edit main > $out/$n-merge.log 2>&1 || { echo "$n merge CONFLICT" >> $s; continue; }
  nice -n 19 bash scripts/test.sh unit > $out/$n-unit.log 2>&1; echo "$n unit EXIT=$?" >> $s
  THEMINESPIRIT_GAMETEST_WAIT=7200 nice -n 19 bash scripts/test.sh gametest > $out/$n-gt.log 2>&1; echo "$n gametest EXIT=$?" >> $s
done
echo DONE >> $s
