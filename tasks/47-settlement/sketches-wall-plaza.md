# Эскизы: ограждение (`wall`, `gate`) и площадь (`plaza`)

Эскиз стиля, сторожами не проверен.

## Ограждение — разрез поперёк стены (слева — внутрь поселения, справа — наружу)

Сегмент — 8 блоков вдоль линии; разрез одинаков по всей длине, кроме отмеченного «через клетку».
`~` земля · `f` забор · `t` факел · `L` бревно · `#` доски помоста · `c` булыжник · `S` каменный кирпич ·
`m` зубец (через клетку) · `n` фонарь · `.` воздух

```
1 забор, эт. 0         2 частокол, эт. 1
                         f      y4  заострение через клетку
   t    y3 (столб)       L      y3
   f    y2 (столб)     ##L      y2  помост, стоят на нём
   f    y1             f L      y1  столб помоста
~~~~~~~~~~~~~~       ~~~~~~~~~~~~   y0
```

```
3 каменная стена, эт. 2            4 крепостная стена, эт. 3
                                      n  m   y8  фонарь, зубец
     m     y6  зубец через клетку     ...S   y7  парапет; помост 3
   ..c     y5  парапет по грудь       SSSS   y6
   ccc     y4  помост шириной 2       SSSS   y5
   ccc     y3                         SSSS   y4
   ccc     y2                         SSSSS  y3  контрфорс через 8
   ccc     y1                         SSSSS  y2
                                      SSSSS  y1
~~~ccc~~~~ y0                      ~~~SSSS~...~  y0  ров 3×2
```

- Забор: одна линия (коллизия 1,5 — не перепрыгнуть), столб с факелом через 8.
- Частокол: брёвна в 3 высоты; изнутри помост на высоте 2 (стоя на нём, дух видит и стреляет поверх),
  заострения — забор на бревне через клетку; лестница на помост через 24.
- Каменная стена: булыжник толщиной 3, высотой 4, помост шириной 2, парапет по грудь, зубцы через клетку
  (между ними бойницы); ступени изнутри через 24 и у каждой башни.
- Крепостная: каменный кирпич толщиной 4, высотой 6, контрфорсы снаружи через 8, фонари на помосте, ров.

## Главные ворота, уровень 3 — вид снаружи (надвратная башня 9×7, проход 3×4)

`a` каменные ступеньки вверх ногами (арка) · `|` железные прутья (поднятая решётка) · `g` калитка · `G` бойница

```
  m.m.m.m.m      y10 зубцы
  SSSSSSSSS      y9  площадка башни, выход с помоста стены
  SccGcGccS      y8  бойницы
  ScccccccS      y7
  ScccccccS      y6
  SccaSaccS      y5  арка
  Scc|||ccS      y4  решётка (декор)
  Scc...ccS      y3  проход 3 шириной
  Scc...ccS      y2
  SccgggccS      y1  три калитки: днём открыты, ночью закрыты
  SSSSSSSSS      y0
 n         n     фонари на столбах по обе стороны
```

Уровни ворот: 1 — три калитки в заборе и два столба с факелами; 2 — арка-перекладина из брёвен над
частоколом; 3 — надвратная башня (выше); 4 — барбакан: две башни по сторонам и мост через ров.

## Площадь, уровень 1 — 15×15, этап 0

`g` гравий · `.` трава · `W` колодец 3×3 (`well`) · `t` факельный столб · `l` кафедра — доска заданий

```
t....ggggg....t   ← дом игрока
..ggggggggggg..
..ggggggggggg..
..ggggggggggg..
..ggggggggggg..
..ggggggggggg..
ggggggWWWgggggg
ggggggWWWgggggg   ← поперечная улица слева и справа
ggggggWWWgggggg
..ggggggggggg..
..ggggggggggg..
..gggggglgggg..
..ggggggggggg..
..ggggggggggg..
t....ggggg....t   ← главная улица от ворот
```

## Площадь, уровень 3 — 25×25, этап 4

`s` каменный кирпич · `a` шлифованный андезит (кольцо) · `F` бортик фонтана · `w` вода · `P` столб с источником
наверху · `h` скамья · `o` фонарный столб · `z` цветущая азалия в кадке · `v` клумба · `b` колокол · `l` доска
заданий · `e` ступени к дому игрока

```
zvvvvvvvveeeeeeevvvvvvvvz   ← дом игрока
vsssssssosssssssosssssssv
vsssssssssssssssssssssssv
vsszsssssssslsssssssszssv
vsssssssssssbsssssssssssv
vsssssssssssssssssssssssv
vsssssssssssssssssssssssv
vssssssoaaaaaaaaaossssssv
vssssssassshhhsssassssssv
vosssssasssssssssasssssov
sssssssassFFFFFssasssssss
sssssssahsFwwwFshasssssss   ← поперечная улица
sssssssahsFwPwFshasssssss
sssssssahsFwwwFshasssssss
sssssssassFFFFFssasssssss
vosssssasssssssssasssssov
vssssssassshhhsssassssssv
vssssssoaaaaaaaaaossssssv
vsssssssssssssssssssssssv
vsssssssssssssssssssssssv
vsssssssssssssssssssssssv
vsszssssssssssssssssszssv
vsssssssssssssssssssssssv
vssssssssosssssossssssssv
zvvvvvvvvvsssssvvvvvvvvvz   ← главная улица от ворот
```

Что растёт по уровням площади: 1 — гравий, колодец, факелы, доска; 2 (19×19) — булыжник с узором, скамьи,
клумбы, фонари, колокол; 3 — каменный кирпич с кольцом, фонтан, кадки с азалиями, ступени к дому игрока.
