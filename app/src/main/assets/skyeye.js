/* ChengJing Sky Eye. Runs in the document; it has no credential or native write APIs. */
(() => {
  'use strict';
  if (window !== window.top) return;
  if (window.__chengjingEye) { window.__chengjingEye.configure(__CJ_CONFIG__); return; }
  let config = __CJ_CONFIG__, enabled = false, selected = null, preview = null, raf = 0;
  let overlay, label, outlineStyle, observer, selectedBox;
  const marker = 'data-cj-removed';
  const own = new WeakSet(), injected = new WeakMap(), observed = new WeakSet();
  const channel = window.ChengJingSelection;
  const send = data => { try { channel?.postMessage(JSON.stringify(data)); } catch (_) {} };
  const matchesDomain = (host, domain) => host === domain || host.endsWith('.' + domain);
  const current = () => {
    const host = location.hostname;
    if (config.exceptions?.some(d => matchesDomain(host,d))) return null;
    if (preview && matchesDomain(host,preview.domain)) return preview;
    return config.sites?.filter(s => matchesDomain(host,s.domain)).sort((a,b)=>b.domain.length-a.domain.length)[0] || null;
  };
  const roots = () => {
    const result = [document];
    for (let i=0; i<result.length; i++) for (const el of result[i].querySelectorAll('*')) if (el.shadowRoot && !own.has(el)) result.push(el.shadowRoot);
    return result;
  };
  function query(selector) {
    const parts = selector.split(' >>> ');
    let scopes = [document], found = [];
    for (let i=0; i<parts.length; i++) {
      found = scopes.flatMap(s=>Array.from(s.querySelectorAll(parts[i])));
      scopes = found.map(n=>n.shadowRoot).filter(Boolean);
    }
    return found.filter(n=>!own.has(n));
  }
  function safe(selector) {
    if (!selector || selector.length>1200 || /[{};\n\r]/.test(selector)) return {valid:false,error:'元件規則格式不正確'};
    try {
      const nodes=query(selector);
      if (nodes.some(n=>n === document.body || n === document.documentElement)) return {valid:false,error:'不能移除整個頁面'};
      return {valid:true,count:nodes.length,large:nodes.some(n=>n.getBoundingClientRect().height>innerHeight*.8 && n.getBoundingClientRect().width>innerWidth*.8)};
    } catch (_) { return {valid:false,error:'找不到有效的元件選擇器'}; }
  }
  const stable = value => value && value.length < 65 && !/^(active|hover|focus|selected|open|show|visible|hidden|is-|has-|css-|jsx-|sc-)/i.test(value) && !/[0-9a-f]{8,}|\d{5,}/i.test(value);
  function localSelector(el) {
    const root=el.getRootNode();
    if (stable(el.id)) return '#'+CSS.escape(el.id);
    const path=[]; let node=el;
    while(node && node.nodeType===1 && node!==document.body && node!==document.documentElement) {
      if (stable(node.id)) { path.unshift('#'+CSS.escape(node.id)); break; }
      let segment=node.localName;
      const classes=Array.from(node.classList).filter(stable).slice(0,3);
      if(classes.length) segment+=classes.map(c=>'.'+CSS.escape(c)).join('');
      else {
        const role=node.getAttribute('role');
        if(role && /^[a-z-]+$/.test(role)) segment+='[role="'+role+'"]';
      }
      const siblings=node.parentElement ? Array.from(node.parentElement.children).filter(n=>n.localName===node.localName) : [];
      if(siblings.length>1 && (!classes.length || siblings.filter(n=>classes.every(c=>n.classList.contains(c))).length>1)) segment+=':nth-of-type('+(siblings.indexOf(node)+1)+')';
      path.unshift(segment);
      const candidate=path.join(' > ');
      try { if(root.querySelectorAll(candidate).length===1) break; } catch(_) {}
      node=node.parentElement;
    }
    return path.join(' > ') || el.localName;
  }
  function selector(el) {
    const root=el.getRootNode();
    return root.host ? selector(root.host)+' >>> '+localSelector(el) : localSelector(el);
  }
  const describe=el=>({tag:el.localName,selector:selector(el),label:el.localName+(el.id?' #'+el.id:el.classList.length?' .'+Array.from(el.classList).slice(0,2).join('.'):'')});
  function reportSelection() {
    if(!selected || !selected.isConnected) return;
    const d=describe(selected), rect=selected.getBoundingClientRect();
    send({type:'selection',...d,count:query(d.selector).length,width:Math.round(rect.width),height:Math.round(rect.height),canParent:!!selected.parentElement && selected.parentElement!==document.body,frame:selected.localName==='iframe',hidden:getComputedStyle(selected).display==='none'});
    drawSelected();
  }
  function drawSelected() {
    if(!selectedBox || !selected) return;
    const r=selected.getBoundingClientRect();
    Object.assign(selectedBox.style,{left:r.x+'px',top:r.y+'px',width:r.width+'px',height:r.height+'px',display:'block'});
    label.textContent=describe(selected).label;
    label.style.display='block';
    label.style.top=Math.max(4,Math.min(innerHeight-34,r.top-32))+'px';
    label.style.left=Math.max(4,Math.min(innerWidth-230,r.left))+'px';
  }
  function select(el) {
    if(!el || own.has(el) || el===document.body || el===document.documentElement) return;
    selected=el; reportSelection();
  }
  function intercept(e) {
    if(!enabled || e.composedPath().some(n=>own.has(n))) return;
    const el=e.composedPath().find(n=>n instanceof Element && !own.has(n));
    e.preventDefault(); e.stopImmediatePropagation();
    select(el);
  }
  function ensureOverlay() {
    if(overlay?.isConnected || !document.documentElement) return;
    overlay=document.createElement('div'); own.add(overlay);
    overlay.setAttribute('aria-hidden','true');
    overlay.style.cssText='all:initial;position:fixed;inset:0;pointer-events:none;z-index:2147483647;';
    const shadow=overlay.attachShadow({mode:'closed'});
    const style=document.createElement('style'); style.textContent='*{box-sizing:border-box} .box{position:fixed;border:2px solid #147a64;background:#35c7a225;border-radius:3px;pointer-events:none;display:none}.label{position:fixed;background:#123d32;color:#fffdf7;padding:6px 10px;border-radius:6px;font:12px/1.3 sans-serif;max-width:230px;overflow:hidden;white-space:nowrap}.frame{position:fixed;pointer-events:auto;background:#35c7a218;border:1px dashed #147a64;}';
    selectedBox=document.createElement('div');selectedBox.className='box';
    label=document.createElement('div'); label.className='label'; label.style.display='none';
    shadow.append(style,selectedBox,label); document.documentElement.append(overlay);
    overlay._shadow=shadow;
  }
  function drawFrames() {
    if(!enabled || !overlay) return;
    overlay._shadow.querySelectorAll('.frame').forEach(n=>n.remove());
    roots().flatMap(r=>Array.from(r.querySelectorAll('iframe'))).forEach(frame=>{
      const r=frame.getBoundingClientRect(); if(r.width<1||r.height<1)return;
      const hit=document.createElement('div');own.add(hit);hit.className='frame';
      Object.assign(hit.style,{left:r.x+'px',top:r.y+'px',width:r.width+'px',height:r.height+'px'});
      hit.addEventListener('click',e=>{e.preventDefault();e.stopPropagation();select(frame);},true);
      overlay._shadow.append(hit);
    });
  }
  function apply() {
    raf=0; if(!document.documentElement)return;
    const site=current(), hidden=new Set();
    if(site) for(const rule of site.rules||[]) {
      try { if(safe(rule.selector).valid) query(rule.selector).forEach(n=>hidden.add(n)); }catch(_){}
    }
    for(const root of roots()) {
      root.querySelectorAll('['+marker+']').forEach(n=>{if(!hidden.has(n))n.removeAttribute(marker);});
      let style=injected.get(root);
      if(!style || !style.isConnected) {
        style=document.createElement('style');own.add(style); injected.set(root,style);
        (root===document ? document.documentElement : root).append(style);
      }
      const content='['+marker+']{display:none!important;visibility:hidden!important;pointer-events:none!important}'+(site?.css||'')+(site?.unlockScroll?'html,body{overflow:auto!important;position:static!important;touch-action:auto!important}':'')+(enabled?'*:not(html):not(body):not(style):not(script){outline:1px solid #147a6466!important;outline-offset:-1px!important}':'');
      if(style.textContent!==content)style.textContent=content;
      if(!observed.has(root)) { observer.observe(root,{childList:true,subtree:true,attributes:true,attributeFilter:['class','id','style','hidden']}); observed.add(root); }
    }
    hidden.forEach(n=>{if(!n.hasAttribute(marker))n.setAttribute(marker,'');});
    if(enabled){ensureOverlay();drawFrames();drawSelected();}
  }
  function schedule() { if(!raf)raf=requestAnimationFrame(apply); }
  observer=new MutationObserver(records=>{if(records.some(r=>!own.has(r.target) && !(r.target.parentNode && own.has(r.target.parentNode))))schedule();});
  function setEnabled(value) {
    enabled=!!value;
    if(!enabled){overlay?.remove();overlay=null;selected=null;}
    apply();return {enabled};
  }
  function inventory() {
    return roots().flatMap(r=>Array.from(r.querySelectorAll(r===document?'body *':'*'))).filter(el=>!own.has(el)&&!['SCRIPT','STYLE','LINK','META','NOSCRIPT'].includes(el.tagName)).slice(0,1200).map(el=>{
      const d=describe(el), s=getComputedStyle(el), r=el.getBoundingClientRect();
      return {...d,hidden:s.display==='none'||s.visibility==='hidden',fixed:s.position==='fixed'||s.position==='sticky',width:Math.round(r.width),height:Math.round(r.height)};
    }).filter(d=>d.fixed||d.hidden||d.tag==='iframe').slice(0,80);
  }
  function snapshot() {
    const structural=roots().flatMap(r=>Array.from(r.querySelectorAll(r===document?'body *':'*'))).filter(el=>!own.has(el)&&!['SCRIPT','STYLE','LINK','META','NOSCRIPT','INPUT','TEXTAREA','OPTION'].includes(el.tagName)).slice(0,350).map(el=>{
      const s=getComputedStyle(el);return {...describe(el),position:s.position,display:s.display,children:el.children.length};
    });
    return JSON.stringify({selected:selected?describe(selected):null,elements:structural,notice:'Structure only; no text, input values, cookies or page URL.'});
  }
  const api={
    configure(value){config=value;preview=null;apply();},
    enable:setEnabled,
    parent(){if(selected?.parentElement && selected.parentElement!==document.body)select(selected.parentElement);},
    select(s){try{select(query(s)[0]);}catch(_){}},
    inspect:safe,
    preview(value){preview=value;apply();return {applied:true};},
    inventory,snapshot,
    status(){return {enabled,removed:document.querySelectorAll('['+marker+']').length,domain:current()?.domain||'',selected:selected?describe(selected):null};}
  };
  Object.defineProperty(window,'__chengjingEye',{value:api,configurable:false,writable:false});
  document.addEventListener('click',intercept,true);
  document.addEventListener('pointerdown',e=>{if(enabled){e.stopImmediatePropagation();}},true);
  document.addEventListener('submit',e=>{if(enabled){e.preventDefault();e.stopImmediatePropagation();}},true);
  window.addEventListener('scroll',()=>{if(enabled){drawSelected();drawFrames();}},{passive:true,capture:true});
  window.addEventListener('resize',schedule);
  const ready=()=>{apply();const site=current();if(site?.js){try{(0,eval)(site.js);}catch(e){send({type:'codeError',message:String(e).slice(0,200)});}}};
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ready,{once:true});else ready();
  apply();
})();
