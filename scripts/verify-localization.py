from pathlib import Path
import json,re,xml.etree.ElementTree as E
ROOT=Path.cwd();cat=json.loads((ROOT/'localization/source-zh-TW.json').read_text())
paths={'zh-TW':'values-b+zh+Hant','zh-CN':'values-b+zh+Hans','en':'values','ja':'values-ja','ko':'values-ko','fr':'values-fr','de':'values-de','es':'values-es','pt':'values-pt','ar':'values-ar','th':'values-th','ru':'values-ru','hi':'values-hi','id':'values-in','vi':'values-vi','bn':'values-bn','ur':'values-ur'}
def placeholders(s):return sorted(re.findall(r'%\d+\$s|%%',s))
for tag,folder in paths.items():
 root=E.parse(ROOT/'app/src/main/res'/folder/'strings.xml').getroot();strings={x.attrib['name']:x.text for x in root.findall('string')}
 assert set(strings)==set(cat),(tag,'missing resources')
 data={key:value for key,value in strings.items()}
 for k,v in cat.items():
  assert placeholders(v)==placeholders(data[k]),(tag,k,'placeholder')
  assert data[k].strip(),(tag,k,'empty')
 assert len(root.find("string-array[@name='home_quotes']"))==89
 assert (ROOT/'app/src/main/assets/i18n'/tag/'practice.html').exists()
 assert (ROOT/'app/src/main/assets/i18n'/tag/'privacy.txt').exists()
 assert all(len((value or '').encode())<32767 for value in strings.values()),(tag,'Android string pool limit')
print('Complete resources:',len(cat),'keys ×',len(paths),'languages; placeholders, 89 quotes and 17 practice pages matched.')
# No forgotten hardcoded app UI; allow only language autonyms, source fallback, and stable legacy markers.
allowed={'AppLanguagePolicy.kt','BrowserTextSource.kt','BrowserController.kt','BrowserCollections.kt'}
left=[]
for p in (ROOT/'app/src/main/java').rglob('*.kt'):
 if p.name in allowed:continue
 for line in p.read_text().splitlines():
  if re.search(r'"[^"\n]*[\u3400-\u9fff][^"\n]*"',line) and not line.lstrip().startswith('//'):left.append((p.name,line[:180]))
assert not left,left
print('Hardcoded UI scan passed (autonyms, original-source compatibility markers excluded).')
