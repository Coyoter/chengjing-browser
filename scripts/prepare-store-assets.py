#!/usr/bin/env python3
from pathlib import Path
import re,subprocess,xml.etree.ElementTree as ET,urllib.request,concurrent.futures
root=Path(__file__).resolve().parents[1];cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
modules=[line for line in (root/'qa/runtime-modules.txt').read_text().splitlines() if not line.startswith('ChengJingBrowser:')]
def license_for(line):
 g,a,v=line.split(':');poms=list((cache/g/a/v).glob('*/*.pom'))
 if poms:raw=poms[0].read_bytes()
 else:
  repo='https://dl.google.com/dl/android/maven2/' if g.startswith(('androidx.','com.google.android.gms')) else 'https://repo.maven.apache.org/maven2/'
  with urllib.request.urlopen(repo+g.replace('.','/')+'/'+a+'/'+v+'/'+a+'-'+v+'.pom',timeout=20) as r:raw=r.read()
 tree=ET.fromstring(raw);found=[]
 for el in tree.iter():
  if el.tag.split('}')[-1]=='license':
   d={n.tag.split('}')[-1]:n.text or '' for n in el};found.append(d.get('name','')+' '+d.get('url',''))
 if not found and g=='com.google.guava':found=['Apache License 2.0 https://github.com/google/guava/blob/master/COPYING']
 assert found, 'Missing license: '+line
 return line+'\n'+'\n'.join(found)
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:rows=list(pool.map(license_for,modules))
old=(root/'app/src/main/assets/third-party-notices.txt').read_text();full=old[old.index('\n\nApache License 2.0\n'):]
(root/'app/src/main/assets/third-party-notices.txt').write_text('Third-party software notices\n\nCopyright belongs to the respective authors.\n\n'+'\n\n'.join(rows)+full)
print('Verified license declarations:',len(rows))
svg=(root/'design/icon.svg').read_text().replace('width="1080" height="1080"','width="512" height="512"').replace('x="1" y="1" width="106" height="106" rx="24"','x="0" y="0" width="108" height="108" rx="0"')
(root/'store/assets/icon-512.svg').write_text(svg)
subprocess.run(['/opt/homebrew/bin/rsvg-convert',str(root/'store/assets/icon-512.svg'),'-o',str(root/'store/assets/icon-512.png')],check=True)
inner=re.search(r'<g transform=.*</g>',svg).group(0)
feature='<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500"><rect width="1024" height="500" fill="#101c17"/><g transform="translate(68 135) scale(2.1)">'+inner+'</g><text x="355" y="211" font-family="PingFang TC, Noto Sans CJK TC, sans-serif" font-size="60" font-weight="500" fill="#f3efe4">澄境瀏覽器</text><text x="359" y="275" font-family="PingFang TC, Noto Sans CJK TC, sans-serif" font-size="27" fill="#a8bcb1">自帶天眼開發者工具</text><text x="359" y="333" font-family="sans-serif" font-size="21" letter-spacing="4" fill="#69cbb0">CSS  ·  JS  ·  HTML</text></svg>'
(root/'store/assets/feature-1024x500.svg').write_text(feature)
subprocess.run(['/opt/homebrew/bin/rsvg-convert',str(root/'store/assets/feature-1024x500.svg'),'-o',str(root/'store/assets/feature-1024x500.png')],check=True)
print('Store graphics rendered')
