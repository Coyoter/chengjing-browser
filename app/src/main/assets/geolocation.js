(() => {
  'use strict';
  if (window !== window.top || !window.isSecureContext || !navigator.geolocation || !window.ChengJingLocation) return;
  if (Object.prototype.hasOwnProperty.call(window, '__chengjingLocationAccess')) return;
  const bridge = window.ChengJingLocation;
  const doc = Array.from(crypto.getRandomValues(new Uint32Array(4)), n => n.toString(16).padStart(8, '0')).join('');
  const stringify = JSON.stringify.bind(JSON), parse = JSON.parse.bind(JSON);
  const policyObject = document.permissionsPolicy || document.featurePolicy;
  const allowed = policyObject && policyObject.allowsFeature.bind(policyObject);
  const visibility = Object.getOwnPropertyDescriptor(Document.prototype, 'visibilityState').get.bind(document);
  Object.defineProperty(window, '__chengjingLocationAccess', {
    get: () => stringify({ doc, allowed: !!allowed && allowed('geolocation'), visible: visibility() === 'visible' }),
    configurable: false
  });
  const calls = new Map();
  let nextId = 1;
  const errorValue = (code, message) => Object.freeze({ code, message, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 });
  const invoke = (callback, value) => { if (callback) setTimeout(() => callback(value), 0); };
  const number = (value, fallback, max) => value === undefined ? fallback : Math.min(max, Math.max(0, Math.floor(Number(value)) || 0));
  function post(message) { bridge.postMessage(stringify({ ...message, doc })); }
  function send(id, entry) { post({ op: entry.watch ? 'watch' : 'get', id, ...entry.options }); }
  function begin(success, error, options, watch) {
    if (typeof success !== 'function' || (error != null && typeof error !== 'function')) throw new TypeError('Geolocation callbacks must be functions');
    const id = nextId++;
    if (!allowed || !allowed('geolocation')) { invoke(error, errorValue(1, 'This document does not allow geolocation.')); return id; }
    if (calls.size >= 8) { invoke(error, errorValue(2, 'Too many pending location requests.')); return id; }
    options = options || {};
    const entry = { success, error, watch, options: {
      highAccuracy: !!options.enableHighAccuracy,
      timeout: number(options.timeout, 60000, 120000),
      maximumAge: number(options.maximumAge, 0, 4294967295)
    }};
    calls.set(id, entry);send(id, entry);return id;
  }
  const geo = navigator.geolocation;
  Object.defineProperties(geo, {
    getCurrentPosition: { configurable: true, writable: true, value: function getCurrentPosition(success, error, options) { begin(success, error, options, false); } },
    watchPosition: { configurable: true, writable: true, value: function watchPosition(success, error, options) { return begin(success, error, options, true); } },
    clearWatch: { configurable: true, writable: true, value: function clearWatch(id) { id = Number(id);const entry = calls.get(id);if (entry && entry.watch) { calls.delete(id);post({ op: 'clear', id }); } } }
  });
  bridge.addEventListener('message', event => {
    let message;try { message = parse(event.data); } catch (_) { return; }
    if (message.doc !== doc) return;
    const entry = calls.get(message.id);if (!entry) return;
    if (message.type === 'position') {
      if (!entry.watch) calls.delete(message.id);
      const coords = Object.freeze({ ...message.position.coords, toJSON() { return { ...message.position.coords }; } });
      const position = Object.freeze({ coords, timestamp: message.position.timestamp, toJSON() { return { coords: coords.toJSON(), timestamp: this.timestamp }; } });
      if (entry.watch) setTimeout(() => { if (calls.get(message.id) === entry) entry.success(position); }, 0);
      else invoke(entry.success, position);
    } else if (message.type === 'error') {
      if (!entry.watch || message.terminal) calls.delete(message.id);
      invoke(entry.error, errorValue(message.code, message.message));
    }
  });
  function suspend() {
    post({ op: 'pause' });
  }
  function resume() { post({ op: 'resume' }); }
  document.addEventListener('visibilitychange', () => { if (visibility() === 'visible') resume(); else suspend(); });
  window.addEventListener('pagehide', () => {
    suspend();
    for (const [id, entry] of calls) if (!entry.watch) { calls.delete(id);invoke(entry.error, errorValue(2, 'The document was closed.')); }
  });
  window.addEventListener('pageshow', () => { for (const [id, entry] of calls) if (entry.watch) send(id, entry); });
})();
