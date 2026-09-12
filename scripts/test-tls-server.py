import ssl, http.server, base64, pathlib
root=pathlib.Path(__file__).resolve().parents[1]/'qa'/'tls-fixture'
root.mkdir(parents=True,exist_ok=True)
if not (root/'cert.pem').exists():
 import subprocess
 subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(root/'key.pem'),'-out',str(root/'cert.pem'),'-days','1','-subj','/CN=ChengJing Local TLS Test'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
pixel=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j6XkAAAAASUVORK5CYII=')
class Handler(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  body=pixel if self.path.startswith('/pixel') else b'<!doctype html><meta name="viewport" content="width=device-width"><title>TLS fixture</title><h1 id="tls-main">Certificate continuation test</h1><p>The document loaded through an untrusted certificate.</p>'
  self.send_response(200); self.send_header('Content-Type','image/png' if self.path.startswith('/pixel') else 'text/html'); self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,fmt,*args): print(fmt % args,flush=True)
server=http.server.ThreadingHTTPServer(('127.0.0.1',18743),Handler)
context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(root/'cert.pem',root/'key.pem')
server.socket=context.wrap_socket(server.socket,server_side=True)
print('Local self-signed TLS fixture ready on 18743',flush=True)
server.serve_forever()
