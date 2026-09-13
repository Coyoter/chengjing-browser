#!/usr/bin/env python3
"""Publish only the browser's own WordPress pages, preserving the existing site shell."""
import pathlib,sys,html,json,urllib.request,base64,subprocess
root=pathlib.Path(__file__).resolve().parents[1]
base='https://techtarian.com/wp-json/wp/v2/pages'
def secret(account,service):return subprocess.check_output(['security','find-generic-password','-a',account,'-s',service,'-w'],text=True).strip()
user=secret('techtarian','techtarian-wp-user');password=secret('coyoter','techtarian-wp-app-password')
auth=base64.b64encode((user+':'+password).encode()).decode()
def req(method,url,body=None):
 data=None if body is None else json.dumps(body,ensure_ascii=False).encode()
 request=urllib.request.Request(url,data=data,method=method,headers={'Authorization':'Basic '+auth,'Content-Type':'application/json','User-Agent':'ChengJing-Publishing/1.0'})
 with urllib.request.urlopen(request,timeout=45) as response:return json.load(response)
def upsert(slug,parent,title,content):
 rows=req('GET',base+'?slug='+slug+'&context=edit&status=any&per_page=100')
 rows=[p for p in rows if p['parent']==parent]
 assert len(rows)<=1,'Ambiguous page; not changed'
 marker='<!-- chengjing-browser-public-page -->'
 if rows:assert marker in rows[0]['content']['raw'],'Existing unrelated page; not changed'
 body={'title':title,'slug':slug,'parent':parent,'content':marker+'\n'+content,'status':'publish','comment_status':'closed','ping_status':'closed'}
 result=req('POST',base+('/'+str(rows[0]['id']) if rows else ''),body)
 read=req('GET',base+'/'+str(result['id'])+'?context=edit')
 assert read['status']=='publish' and read['content']['raw']==body['content']
 return read
policy=(root/'app/src/main/assets/privacy.txt').read_text()
privacy='\n'.join('<p>'+html.escape(p).replace('\n','<br>')+'</p>' for p in policy.split('\n\n') if p.strip())
(root/'store/privacy.html').write_text(privacy)
parent=upsert('chengjing-browser',0,'澄境瀏覽器｜自帶天眼開發者工具', '<p>澄境瀏覽器將日常瀏覽與天眼開發者工具放在一起。查看網頁結構，加入 CSS、JavaScript 與 HTML，依自己的習慣調整網頁。</p><p>提供書籤資料夾、閱讀收藏、選用 Google Drive 書籤與天眼設定同步，以及可選的 Gemma 4 本機與 OpenRouter 雲端 AI 協助。目前正在準備 Google Play 發行。</p><p><a href="https://techtarian.com/chengjing-browser/privacy/">隱私權政策與資料刪除方式</a></p><p>開發者：Coyoter。聯絡信箱：admin@techtarian.com</p>')
child=upsert('privacy',parent['id'],'澄境瀏覽器隱私權政策',privacy)
proof={'product':{'id':parent['id'],'url':parent['link']},'privacy':{'id':child['id'],'url':child['link']},'status':'published, REST content read back'}
(root/'qa/browser-policy-publication.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2))
print(json.dumps(proof,ensure_ascii=False))
