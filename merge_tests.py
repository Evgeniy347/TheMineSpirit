import subprocess

main = subprocess.check_output(['git', 'show', 'main:tools/graph-gpu/test_conformance.py']).decode('utf-8')
ours_path = 'tools/graph-gpu/test_conformance.py'
ours = open(ours_path, encoding='utf-8').read()


def block(text, name):
    start = text.index('def ' + name + '(')
    end = text.find('\ndef ', start + 1)
    if end < 0:
        end = text.find('\nif __name__', start + 1)
    return text[start:end]


for name in ('test_life_coefficient_vector', 'test_food_need_band'):
    main_block = block(main, name)
    if 'def ' + name + '(' in ours:
        start = ours.index('def ' + name + '(')
        end = ours.find('\ndef ', start + 1)
        if end < 0:
            end = ours.find('\nif __name__', start + 1)
        ours = ours[:start] + main_block + ours[end:]
    else:
        anchor = ours.index('def test_food_need_band(')
        ours = ours[:anchor] + main_block + '\n\n' + ours[anchor:]

if 'test_life_coefficient_vector()' not in ours:
    ours = ours.replace('        test_life_player_fact()\n', '        test_life_player_fact()\n        test_life_coefficient_vector()\n')
open(ours_path, 'w', encoding='utf-8').write(ours)
print('ok')
