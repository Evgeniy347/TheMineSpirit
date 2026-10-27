
# stage4lib.sh — общие команды генераторов построек этапа 4 (catalog-2.md).
# attic_roof X0 W Z0 D YB P E Wc S N: ступенчатая вальмовая крыша над телом (без свеса; свес — отдельным
# кольцом у заказчика). Каждый слой полон и опирается на предыдущий, край слоя — ступеньки наружу.
attic_roof() {
  local X0=$1 W=$2 Z0=$3 D=$4 YB=$5 P=$6 E=$7 Wc=$8 S=$9 N=${10} max=${11:-99} k=0 xl xr zl zr y
  while [ $k -lt $max ]; do
    xl=$((X0 + k)); xr=$((X0 + W - 1 - k)); zl=$((Z0 + k)); zr=$((Z0 + D - 1 - k)); y=$((YB + k))
    if [ $xl -gt $xr ] || [ $zl -gt $zr ]; then break; fi
    echo "box $y $y $xl $xr $zl $zr $P"
    if [ $xl -lt $xr ] && [ $zl -lt $zr ]; then
      echo "box $y $y $xl $xl $zl $zr $E"
      echo "box $y $y $xr $xr $zl $zr $Wc"
      echo "box $y $y $((xl + 1)) $((xr - 1)) $zl $zl $S"
      echo "box $y $y $((xl + 1)) $((xr - 1)) $zr $zr $N"
    fi
    k=$((k + 1))
  done
}

# frame_columns X W Z0 D YB sym: брёвна каркаса по углам и через каждые 4 блока по стенам.
frame_columns() {
  local X0=$1 W=$2 Z0=$3 D=$4 YB=$5 sym=$6 x z y
  local xs=("$X0")
  for ((x = X0 + 4; x <= X0 + W - 2; x += 4)); do xs+=("$x"); done
  xs+=("$((X0 + W - 1))")
  local zs=("$Z0")
  for ((z = Z0 + 4; z <= Z0 + D - 2; z += 4)); do zs+=("$z"); done
  zs+=("$((Z0 + D - 1))")
  for y in $(seq 1 "$YB"); do
    for x in "${xs[@]}"; do echo "set $y $x $Z0 $sym"; echo "set $y $x $((Z0 + D - 1)) $sym"; done
    for z in "${zs[@]}"; do echo "set $y $X0 $z $sym"; echo "set $y $((X0 + W - 1)) $z $sym"; done
  done
}

# eave_ring X0 W Z0 D Y: кольцо свеса на высоте Y (полные блоки, опираются на стены/потолок).
eave_ring() {
  local X0=$1 W=$2 Z0=$3 D=$4 Y=$5 P=$6
  echo "box $Y $Y $((X0 - 1)) $((X0 + W)) $((Z0 - 1)) $((Z0 - 1)) $P"
  echo "box $Y $Y $((X0 - 1)) $((X0 + W)) $((Z0 + D)) $((Z0 + D)) $P"
  echo "box $Y $Y $((X0 - 1)) $((X0 - 1)) $Z0 $((Z0 + D - 1)) $P"
  echo "box $Y $Y $((X0 + W)) $((X0 + W)) $Z0 $((Z0 + D - 1)) $P"
}

# eave_decor X0 W Z0 D Y fence leaf: забор и листва по кольцу свеса (опираются на свес).
eave_decor() {
  local X0=$1 W=$2 Z0=$3 D=$4 Y=$5 f=$6 l=$7 x z
  for x in $(seq $((X0 - 1)) $((X0 + W))); do
    if [ $(((x + Y) % 2)) -eq 0 ]; then s=$f; else s=$l; fi
    echo "set $Y $x $((Z0 - 1)) $s"; echo "set $Y $x $((Z0 + D)) $s"
  done
  for z in $(seq $Z0 $((Z0 + D - 1))); do
    if [ $(((z + Y) % 2)) -eq 0 ]; then s=$f; else s=$l; fi
    echo "set $Y $((X0 - 1)) $z $s"; echo "set $Y $((X0 + W)) $z $s"
  done
}

# corner_frame X0 W Z0 D YB sym ex: брёвна каркаса по углам и у створа двери.
corner_frame() {
  local X0=$1 W=$2 Z0=$3 D=$4 YB=$5 sym=$6 ex=$7 y
  for y in $(seq 1 "$YB"); do
    echo "set $y $X0 $Z0 $sym"; echo "set $y $((X0 + W - 1)) $Z0 $sym"
    echo "set $y $X0 $((Z0 + D - 1)) $sym"; echo "set $y $((X0 + W - 1)) $((Z0 + D - 1)) $sym"
    echo "set $y $((ex - 1)) $Z0 $sym"; echo "set $y $((ex + 1)) $Z0 $sym"
  done
}
