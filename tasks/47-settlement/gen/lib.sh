# Помощники генерации схем: L <y> <подпись> строки...; rep <n> <строка> — повтор строки.
# gable roof rows for body width W (odd), depth D; k = layer index; gable window symbol at center or '#'
# roof_row k kind: kind = over (overhang row), gable(win), mid
roof_row() { local W=$1 k=$2 kind=$3 win=${4:-#}; local pad="" i inner=$((W - 2*k)) s
  for ((i=0;i<k;i++)); do pad+=" "; done
  if [ $inner -le 0 ]; then echo "${pad}_"; return; fi
  case $kind in
    over) s="e#"; for ((i=0;i<inner-2;i++)); do s+=" "; done; s+="#w";;
    gable) s="e"; local c=$(( (inner+1)/2 )); for ((i=1;i<=inner;i++)); do if [ $i -eq $c ]; then s+="$win"; else s+="#"; fi; done; s+="w";;
    mid) s="e#"; for ((i=0;i<inner-2;i++)); do s+="."; done; s+="#w";;
  esac
  [ $inner -eq 1 ] && s="e#w"
  echo "${pad}${s}"; }
L() { local y=$1 t=$2; shift 2; echo "  - y$y $t: |"; local r n s i
  for r in "$@"; do
    if [[ "$r" =~ ^([0-9]+)\*(.*)$ ]]; then n=${BASH_REMATCH[1]}; s=${BASH_REMATCH[2]}; for ((i=0;i<n;i++)); do echo "      $s"; done
    else echo "      $r"; fi
  done; }
