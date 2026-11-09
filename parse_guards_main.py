import glob
import xml.etree.ElementTree as ET
import sys

sys.stdout.reconfigure(encoding='utf-8')
for path in glob.glob('TestResults/guards/xml/TEST-*.xml'):
    tree = ET.parse(path).getroot()
    for case in tree.iter('testcase'):
        for fail in list(case.iter('failure')) + list(case.iter('error')):
            print(case.get('classname'), case.get('name'))
            print((fail.get('message') or '')[:1500])
            print('---')
