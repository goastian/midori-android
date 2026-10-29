import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

function event() {
  let listener;
  return { addListener(value) { listener = value; }, emit(...args) { return listener?.(...args); } };
}

function bridge() {
  const messages = [], warnings = [];
  const port = { onMessage: event(), onDisconnect: event(), postMessage(message) { messages.push(message); } };
  const webRequest = Object.fromEntries(['onBeforeRequest', 'onHeadersReceived', 'onCompleted', 'onErrorOccurred'].map(key => [key, event()]));
  const timers = new Map();
  let timer = 0;
  const browser = {
    runtime: { connectNative: () => port, onMessage: event() }, webRequest,
    tabs: { onRemoved: event(), onUpdated: event(), get: async () => ({ url: 'https://news.example/' }), sendMessage: async () => {} },
  };
  const context = vm.createContext({ browser, crypto: { randomUUID: () => 'test' }, URL,
    console: { warn: value => warnings.push(value) },
    setTimeout(fn) { timers.set(++timer, fn); return timer; }, clearTimeout(id) { timers.delete(id); },
    MidoriScriptlets: { update: async () => {} },
  });
  vm.runInContext(readFileSync(new URL('../../app/src/main/assets/adblock/content/network-blocking.js', import.meta.url), 'utf8'), context);
  port.onMessage.emit({ type: 'network_policy', level: 'TRACKERS_AND_ADS', revision: 1, siteLevels: {}, policyId: 1 });
  return { port, messages, warnings, webRequest };
}

const flush = async () => { for (let i = 0; i < 10; i++) await Promise.resolve(); };
const request = (bridge, id) => bridge.webRequest.onBeforeRequest.emit({
  requestId: String(id), tabId: 1, type: 'script', url: `https://ads.example/${id}.js`,
  frameAncestors: [{ frameId: 0, url: 'https://news.example/' }], incognito: false,
});

test('a stalled native decision cannot grow the pending bridge past 128; overflow is cancelled', async () => {
  const b = bridge();
  await flush();
  const pending = Array.from({ length: 1000 }, (_, id) => request(b, id));
  await flush();
  const sent = b.messages.filter(message => message.type === 'network_request');
  assert.equal(sent.length, 128);
  for (let i = 128; i < pending.length; i++) assert.equal((await pending[i]).cancel, true);
  for (const message of sent) b.port.onMessage.emit({ type: 'network_response', requestId: message.requestId, cancel: true });
  assert.ok((await Promise.all(pending)).every(value => value.cancel));
  assert.equal(b.warnings.length, 7);
});

test('temporary saturation is not cached; normal protection resumes once work drains', async () => {
  const b = bridge();
  await flush();
  const pending = Array.from({ length: 128 }, (_, id) => request(b, id));
  await flush();
  assert.equal((await request(b, 999)).cancel, true);
  const sent = b.messages.filter(message => message.type === 'network_request');
  for (const message of sent) b.port.onMessage.emit({ type: 'network_response', requestId: message.requestId, cancel: false });
  await Promise.all(pending);
  const next = request(b, 999);
  await flush();
  const last = b.messages.filter(message => message.type === 'network_request').at(-1);
  assert.equal(last.url, 'https://ads.example/999.js');
  b.port.onMessage.emit({ type: 'network_response', requestId: last.requestId, cancel: false });
  assert.equal((await next).cancel, undefined);
});

test('global OFF honors a blocking site override and other sites bypass native work', async () => {
  const b = bridge();
  b.port.onMessage.emit({ type: 'network_policy', level: 'OFF', revision: 2, siteLevels: { 'news.example': 'TRACKERS' }, policyId: 2 });
  await flush();
  const pending = request(b, 1);
  await flush();
  const sent = b.messages.filter(message => message.type === 'network_request');
  assert.equal(sent.length, 1);
  b.port.onMessage.emit({ type: 'network_response', requestId: sent[0].requestId, cancel: true });
  assert.equal((await pending).cancel, true);
  assert.equal(Object.keys(await b.webRequest.onBeforeRequest.emit({ tabId: 2, type: 'script', url: 'https://ads.example/a.js',
    frameAncestors: [{ frameId: 0, url: 'https://other.example/' }] })).length, 0);
});

function cosmeticDocument(count) {
  const nodes = Array.from({ length: count }, () => ({ nodeType: 1, values: new Map(),
    matches: selector => selector === '.ad',
    getAttribute(name) { return this.values.get(name) ?? null; },
    setAttribute(name, value) { this.values.set(name, value); }, removeAttribute(name) { this.values.delete(name); },
  }));
  let visited = 0, ticks = 0, timer = 0;
  const timers = new Map();
  const context = vm.createContext({ crypto: { getRandomValues: array => array.fill(123) }, Uint32Array,
    MutationObserver: class { observe() {} disconnect() {} },
    NodeFilter: { SHOW_ELEMENT: 1 }, performance: { now: () => ticks++ / 100 },
    setTimeout(fn) { timers.set(++timer, fn); return timer; }, clearTimeout(id) { timers.delete(id); },
    document: { createTreeWalker() { let cursor = 0; return { nextNode() { visited++; return nodes[cursor++] ?? null; } }; } },
  });
  vm.runInContext(readFileSync(new URL('../../app/src/main/assets/adblock/content/cosmetic-procedural.js', import.meta.url), 'utf8'), context);
  const instance = context.MidoriCosmetics.create(() => {});
  function step() {
    const task = timers.entries().next().value;
    if (!task) return false;
    timers.delete(task[0]); task[1](); return true;
  }
  return { nodes, instance, step, visited: () => visited, pending: () => timers.size };
}

const hideRule = JSON.stringify({ selector: [{ type: 'css-selector', arg: '.ad' }] });

test('one large procedural rule yields inside selection and respects the 5000-node cap', () => {
  const fixture = cosmeticDocument(100000);
  fixture.instance.update([hideRule]);
  assert.ok(fixture.visited() <= 256);
  assert.equal(fixture.pending(), 1);
  let slices = 0;
  while (fixture.step()) assert.ok(++slices < 1000);
  assert.equal(fixture.visited(), 5000);
  assert.equal(fixture.nodes.filter(node => node.values.size).length, 5000);
  fixture.instance.disconnect();
  assert.ok(fixture.nodes.every(node => node.values.size === 0));
});

test('disabling a policy midway through a rule restores every mark and cancels pending slices', () => {
  const fixture = cosmeticDocument(6000);
  fixture.instance.update([hideRule]);
  while (!fixture.nodes.some(node => node.values.size)) assert.equal(fixture.step(), true);
  assert.ok(fixture.nodes.filter(node => node.values.size).length < 5000);
  fixture.instance.update([]);
  assert.ok(fixture.nodes.every(node => node.values.size === 0));
  assert.equal(fixture.pending(), 0);
});

function placementDocument(count) {
  const nodes = Array.from({ length: count }, () => ({ nodeType: 1, isConnected: true, classList: ['old'], id: '' }));
  const messages = [], timers = new Map();
  let timer = 0, visits = 0, observer;
  const port = { onMessage: event(), onDisconnect: event(), postMessage(message) { messages.push(message); } };
  const style = { nodeType: 1, isConnected: false, textContent: '', remove() { this.isConnected = false; } };
  const document = {
    createElement: () => style, head: { append(node) { node.isConnected = true; } },
    createTreeWalker(root) {
      let index = 0;
      const selected = root === document ? nodes : [];
      return { nextNode() { visits++; return selected[index++] ?? null; } };
    },
    getElementsByClassName: value => nodes.some(node => node.classList.includes(value)) ? [true] : [],
    getElementById: value => nodes.find(node => node.id === value),
    querySelectorAll() { throw new Error('whole-document queries are forbidden in the incremental collector'); },
  };
  const context = vm.createContext({ document, crypto: { getRandomValues: array => array.fill(123) }, Uint32Array,
    MutationObserver: class { constructor(fn) { observer = fn; } observe() {} disconnect() {} },
    MidoriCosmetics: { create: () => ({ update() {}, disconnect() {} }) },
    browser: { runtime: { connectNative: () => port, sendMessage: async () => {} } },
    CSS: { supports: () => true }, NodeFilter: { SHOW_ELEMENT: 1 }, performance: { now: () => 0 },
    location: { href: 'https://news.example/' }, window: { top: {} },
    setTimeout(fn) { timers.set(++timer, fn); return timer; }, clearTimeout(id) { timers.delete(id); },
  });
  vm.runInContext(readFileSync(new URL('../../app/src/main/assets/adblock/content/ad-placements.js', import.meta.url), 'utf8'), context);
  port.onMessage.emit({ type: 'content_port', portId: 'fixture' });
  port.onMessage.emit({ type: 'ad_placement_policy', enabled: true, selectors: [] });
  function drain() {
    let iterations = 0;
    while (timers.size) {
      assert.ok(++iterations < 10000);
      const [id, fn] = timers.entries().next().value; timers.delete(id); fn();
    }
  }
  return { nodes, port, messages, drain, visits: () => visits, mutate: records => observer(records) };
}

test('dynamic cosmetic queries walk the mutation root and remove tokens of deleted elements', () => {
  const fixture = placementDocument(10000);
  fixture.drain();
  const initialVisits = fixture.visits();
  assert.equal(initialVisits, 10001);
  const next = { nodeType: 1, isConnected: true, classList: ['new-ad'], id: 'new-id' };
  fixture.nodes.push(next);
  fixture.mutate([{ type: 'childList', addedNodes: [next] }]);
  fixture.drain();
  assert.ok(fixture.visits() - initialVisits <= 2);
  assert.ok(fixture.messages.filter(message => message.type === 'cosmetic_query').at(-1).classes.includes('new-ad'));
  fixture.nodes.splice(0, 10000);
  fixture.mutate([{ type: 'childList', addedNodes: [] }]);
  fixture.drain();
  const query = fixture.messages.filter(message => message.type === 'cosmetic_query').at(-1);
  assert.equal(query.classes.length, 1);
  assert.equal(query.classes[0], 'new-ad');
  fixture.port.onDisconnect.emit();
});
