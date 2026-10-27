#!/usr/bin/env bash
# training_yard_<level> — тренировочный двор (catalog-2.md): огороженная площадка, мишени,
# соломенные чучела, стойка оружия; уровни 1–4. Крыши над двором нет — только навес-трибуна.
set -euo pipefail
level=${1:?уровень 1..4}
case $level in
1) W=11; D=11; H=5 ;;
2) W=15; D=15; H=5 ;;
3) W=15; D=15; H=6 ;;
4) W=15; D=15; H=6 ;;
*) echo "уровень 1..4" >&2; exit 2 ;;
esac
X=$((W + 2)); Z=$((D + 2)); Y=$H
ex=$((1 + W / 2))
stage=3; [ "$level" -ge 3 ] && stage=4
cat <<H
#!# training_yard_$level — тренировочный двор, уровень $level (catalog-2.md): открытая площадка
#!# с частоколом-оградой, соломенными чучелами, мишенями, стойкой оружия и фонарями на столбах.
#!name: "Тренировочный двор, уровень $level"
#!type: social
#!stage: $stage
#!level: $level
H
[ "$level" -gt 1 ] && echo "#!requires: training_yard_$((level - 1))"
cat <<H
#!size: ${X}x${Y}x${Z}   # Ш×В×Д
#!entrance: $ex,1,1
#!exemptions:
#!  - rule: roof
#!    reason: "двор под открытым небом; крыша только над трибуной у уровня 4"
#!legend:
#!  'a': gravel
#!  'c': stone_bricks
#!  't': spruce_fence
#!  'D': oak_fence_gate[facing=south,open=false]
#!  'p': stone_brick_slab[type=bottom]
#!  'h': hay_block
#!  'j': carved_pumpkin
#!  'A': anvil
#!  'W': grindstone
#!  'b': barrel[facing=up]
#!  'L': stripped_dark_oak_log[axis=y]
#!  '#': dark_oak_planks
#!  'e': dark_oak_stairs[facing=east,half=bottom]
#!  'w': dark_oak_stairs[facing=west,half=bottom]
#!  'N': lantern[hanging=false]
#!  'x': moss_carpet
#!layers:
H
echo "size $X $Y $Z"
echo "label 0 площадка и крыльцо"; echo "label 1 частокол, чучела, стойки"; echo "label 4 навес"
echo "box 0 0 0 $((X - 1)) 0 $((Z - 1)) a"
echo "box 1 1 2 $((W - 1)) 2 $((D - 1)) x"
echo "walls 1 1 1 $W 1 $D t"
echo "set 1 $ex 1 D"; echo "set 0 $ex 0 p"
# фонари на столбах ограды
for x in 3 $((W / 2)) $((W - 2)); do echo "set 2 $x 1 N"; echo "set 2 $x $D N"; done
for z in 3 $((D / 2)) $((D - 2)); do echo "set 2 1 $z N"; echo "set 2 $W $z N"; done
# чучела: сено + тыква
dum=( "3 3" "$((W - 2)) 3" "3 $((D - 2))" "$((W - 2)) $((D - 2))" )
[ "$level" -ge 2 ] && dum+=( "$((ex - 3)) $((D / 2))" "$((ex + 3)) $((D / 2))" )
for p in "${dum[@]}"; do set -- $p; echo "set 1 $1 $2 h"; echo "set 2 $1 $2 j"; done
# стойка оружия и снаряды
echo "set 1 2 $((D - 4)) A"; echo "set 1 2 $((D - 5)) W"; echo "set 1 $((W - 2)) 4 b"; echo "set 1 $((W - 2)) 5 b"
[ "$level" -ge 2 ] && { echo "set 1 2 3 b"; echo "set 1 3 3 b"; }
# трибуна под навесом (уровень 4): столбы, крыша, скамьи
if [ "$level" -ge 4 ]; then
  for p in "$((W - 4)) $((D - 4))" "$((W - 2)) $((D - 4))" "$((W - 4)) $((D - 2))" "$((W - 2)) $((D - 2))"; do
    set -- $p; echo "col 1 3 $1 $2 L"
  done
  echo "box 4 4 $((W - 5)) $((W - 1)) $((D - 5)) $((D - 1)) #"
  echo "set 0 $((W - 5)) $((D - 5)) #"; echo "set 0 $((W - 1)) $((D - 1)) #"
  echo "box 1 1 $((W - 4)) $((W - 2)) $((D - 3)) $((D - 3)) e"
fi
