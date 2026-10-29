globalThis.MidoriCosmetics = (() => {
  "use strict";

  function create(changed) {
    let generation = 0;
    const marker = () => `data-midori-${Array.from(crypto.getRandomValues(new Uint32Array(2)), n => n.toString(16)).join("")}-${generation++}`;
    let attribute = marker();
    let rules = [], signature = "", timer = null, cursor = 0, work = null, rerun = false;
    const marked = new Map(), removed = new Map(), attributes = new Map();
    let attributeRecords = new WeakMap();
    const maxNodes = 2048, maxCharacters = 131072, maxAttributes = 512;
    let retainedNodes = 0, retainedCharacters = 0, retainedAttributes = 0, overflows = 0;
    const observer = new MutationObserver(records => {
      if (records.some(record => record.type !== "attributes" || record.attributeName !== attribute)) schedule();
    });

    function pattern(value) {
      if (typeof value !== "string" || value.length > 2048) throw new Error("Invalid pattern");
      const match = /^\/(.*)\/([imsu]*)$/.exec(value);
      if (match) { const regex = new RegExp(match[1], match[2]); return text => regex.test(text); }
      return text => text.includes(value.replace(/^(['"])(.*)\1$/, "$2"));
    }

    function namePattern(value) {
      if (/^\/.*\/[imsu]*$/.test(value)) return pattern(value);
      const names = value.replace(/^(['"])(.*)\1$/, "$2").split(/\s*\|\s*/);
      return text => names.includes(text);
    }

    function* select(rule) {
      let nodes = [document];
      for (const operator of rule.selector) {
        const arg = operator.arg || "";
        const next = new Set();
        const add = node => { if (node && next.size < 5000) next.add(node); };
        for (const node of nodes) {
          yield;
          switch (operator.type) {
            case "css-selector": {
              if (/^[>+~]/.test(arg.trim()) || arg.includes(":scope")) {
                const matches = node.querySelectorAll(/^[>+~]/.test(arg.trim()) ? `:scope ${arg}` : arg);
                for (let i = 0; i < matches.length && next.size < 5000; i++) { add(matches[i]); yield; }
              } else {
                const walker = document.createTreeWalker(node, NodeFilter.SHOW_ELEMENT);
                let candidate;
                while (next.size < 5000 && (candidate = walker.nextNode())) {
                  if (candidate.matches(arg)) add(candidate);
                  yield;
                }
              }
              break;
            }
            case "has-text": if (pattern(arg)(node.textContent || "")) add(node); break;
            case "min-text-length":
              if (!/^\d+$/.test(arg)) return [];
              if ((node.textContent || "").length >= Number(arg)) add(node);
              break;
            case "matches-path": if (pattern(arg)(location.pathname + location.search)) add(node); break;
            case "matches-attr": {
              const split = arg.indexOf("="), name = namePattern(split < 0 ? arg : arg.slice(0, split).trim());
              const value = split < 0 ? null : pattern(arg.slice(split + 1).trim());
              if (node.getAttributeNames?.().some(key => name(key) && (!value || value(node.getAttribute(key))))) add(node);
              break;
            }
            case "matches-css": case "matches-css-before": case "matches-css-after": {
              const split = arg.indexOf(":"), property = arg.slice(0, split).trim();
              if (split < 1 || !/^[\w-]+$/.test(property)) return [];
              const pseudo = operator.type === "matches-css" ? null : `::${operator.type.slice(12)}`;
              if (pattern(arg.slice(split + 1).trim())(getComputedStyle(node, pseudo).getPropertyValue(property))) add(node);
              break;
            }
            case "upward": {
              let parent = node;
              if (!/^\d+$/.test(arg)) parent = node.parentElement?.closest(arg);
              else {
                const distance = Number(arg);
                if (distance < 1 || distance > 255) return [];
                for (let step = 0; step < distance && parent; step++) parent = parent.parentElement;
              }
              add(parent);
              break;
            }
            case "xpath": {
              const result = document.evaluate(arg, node, null, XPathResult.ORDERED_NODE_ITERATOR_TYPE, null);
              let match;
              while (next.size < 5000 && (match = result.iterateNext())) { add(match); yield; }
              break;
            }
            default: return [];
          }
          if (next.size >= 5000) break;
        }
        nodes = [...next];
      }
      return nodes.filter(node => node.nodeType === 1);
    }

    function markFallback(node, index) {
      const tokens = new Set((node.getAttribute(attribute) || "").split(" ").filter(Boolean));
      tokens.add(`r${index}`);
      node.setAttribute(attribute, [...tokens].join(" "));
      if (!marked.has(index)) marked.set(index, new Set());
      marked.get(index).add(new WeakRef(node));
    }

    function overflow() {
      if (++overflows === 1 || (overflows & (overflows - 1)) === 0) console.warn("Midori cosmetic restoration budget exceeded", overflows);
    }

    function restoreAttributes(node, values) {
      for (const [name, value] of values) {
        if (node) {
          if (value === null) node.removeAttribute(name); else node.setAttribute(name, value);
        }
        retainedAttributes--;
        retainedCharacters -= name.length + (value?.length || 0);
      }
    }

    function restoreRemoved(node, entry) {
      entry.observer?.disconnect();
      const parent = entry.parent.deref(), sibling = entry.sibling?.deref();
      if (!node.isConnected && parent) parent.insertBefore(node, sibling?.parentNode === parent ? sibling : null);
      retainedNodes -= entry.nodes;
      retainedCharacters -= entry.characters;
    }

    function* prune() {
      for (const [reference, values] of attributes) {
        const node = reference.deref();
        if (!node?.isConnected) {
          restoreAttributes(node, values);
          attributes.delete(reference);
          if (node) attributeRecords.delete(node);
        }
        yield;
      }
      for (const [node, entry] of [...removed].reverse()) {
        if (node.isConnected || !entry.parent.deref()?.isConnected) {
          restoreRemoved(node, entry);
          removed.delete(node);
        } else {
          retainedNodes -= entry.nodes;
          retainedCharacters -= entry.characters;
          entry.nodes = 0; entry.characters = 0;
          const cost = yield* removalCost(node);
          if (cost) {
            Object.assign(entry, cost);
            retainedNodes += cost.nodes;
            retainedCharacters += cost.characters;
          } else {
            restoreRemoved(node, entry);
            removed.delete(node);
            if (node.isConnected) markFallback(node, entry.index);
            overflow();
          }
        }
        yield;
      }
    }

    function remember(node, name) {
      let record = attributeRecords.get(node);
      if (record?.values.has(name)) return true;
      const value = node.getAttribute(name), characters = name.length + (value?.length || 0);
      if (retainedAttributes >= maxAttributes || retainedCharacters + characters > maxCharacters) { overflow(); return false; }
      if (!record) {
        record = { reference: new WeakRef(node), values: new Map() };
        attributeRecords.set(node, record);
        attributes.set(record.reference, record.values);
      }
      record.values.set(name, value);
      retainedAttributes++;
      retainedCharacters += characters;
      return true;
    }

    function* removalCost(node) {
      const walker = document.createTreeWalker(node, NodeFilter.SHOW_ALL);
      let current = node, nodes = 0, characters = 0;
      while (current) {
        nodes++;
        characters += current.nodeValue?.length || 0;
        for (const name of current.getAttributeNames?.() || []) characters += name.length + current.getAttribute(name).length;
        if (retainedNodes + nodes > maxNodes || retainedCharacters + characters > maxCharacters) return null;
        yield;
        current = walker.nextNode();
      }
      return { nodes, characters };
    }

    function* apply(rule, index) {
      const nodes = yield* select(rule);
      const action = rule.action?.type;
      if (action === "remove") {
        const fallback = [];
        for (const node of nodes) {
          yield;
          if (!node.isConnected || !node.parentNode || removed.has(node)) continue;
          const cost = yield* removalCost(node);
          if (!cost) {
            overflow();
            markFallback(node, index);
            fallback.push(node);
            continue;
          }
          if (!node.isConnected || !node.parentNode) continue;
          const detachedObserver = new MutationObserver(schedule);
          removed.set(node, { parent: new WeakRef(node.parentNode), sibling: node.nextSibling ? new WeakRef(node.nextSibling) : null, observer: detachedObserver, index, ...cost });
          retainedNodes += cost.nodes;
          retainedCharacters += cost.characters;
          node.remove();
          detachedObserver.observe(node, {childList: true, subtree: true, attributes: true, characterData: true});
        }
        yield* mark(fallback, index);
        return;
      }
      if (action === "remove-attr" || action === "remove-class") {
        const test = namePattern(rule.action.arg || "");
        for (const node of nodes) {
          yield;
          if (action === "remove-attr") {
            for (const name of node.getAttributeNames().filter(test)) { if (remember(node, name)) node.removeAttribute(name); }
          } else {
            for (const name of [...node.classList].filter(test)) { if (remember(node, "class")) node.classList.remove(name); }
          }
        }
        return;
      }
      if (action && action !== "style") return;
      yield* mark(nodes, index);
    }

    function* mark(nodes, index) {
      const previousRefs = marked.get(index) || new Set(), previous = new Set(), next = new Set(nodes), token = `r${index}`;
      for (const reference of previousRefs) { const node = reference.deref(); if (node) previous.add(node); yield; }
      const tracked = new Set(previousRefs), nextRefs = new Set();
      marked.set(index, tracked);
      for (const node of new Set([...previous, ...next])) {
        yield;
        const tokens = new Set((node.getAttribute(attribute) || "").split(" ").filter(Boolean));
        if (next.has(node)) {
          tokens.add(token);
          const reference = new WeakRef(node);
          tracked.add(reference); nextRefs.add(reference);
        } else tokens.delete(token);
        const value = [...tokens].join(" ");
        if (value !== (node.getAttribute(attribute) || "")) {
          if (value) node.setAttribute(attribute, value); else node.removeAttribute(attribute);
        }
      }
      marked.set(index, nextRefs);
    }

    function styles() {
      return rules.flatMap((rule, index) => {
        if (rule.action && rule.action.type !== "style" && rule.action.type !== "remove") return [];
        const declarations = rule.action?.type === "style" ? rule.action.arg : "display: none !important";
        if (/[{}\r\n@]|url\s*\(/i.test(declarations)) return [];
        return [{selector: `[${attribute}~="r${index}"]`, declarations}];
      });
    }

    function* applyWithPruning(rule, index) {
      if (index === 0) yield* prune();
      yield* apply(rule, index);
    }

    function run() {
      timer = null;
      const deadline = performance.now() + 12;
      let steps = 0;
      while (cursor < rules.length) {
        try {
          if (!work) work = applyWithPruning(rules[cursor], cursor);
          if (work.next().done) { work = null; cursor++; }
        } catch { work = null; cursor++; }
        if (++steps >= 256 || performance.now() >= deadline) {
          timer = setTimeout(run, 32);
          return;
        }
      }
      cursor = 0;
      if (rerun) { rerun = false; schedule(); }
    }

    function schedule() {
      if (timer !== null) { rerun = true; return; }
      if (rules.length) timer = setTimeout(run, 64);
    }

    function clear() {
      observer.disconnect();
      clearTimeout(timer); timer = null; cursor = 0; work = null; rerun = false;
      for (const references of marked.values()) for (const reference of references) reference.deref()?.removeAttribute(attribute);
      marked.clear();
      for (const [reference, values] of attributes) restoreAttributes(reference.deref(), values);
      attributes.clear();
      attributeRecords = new WeakMap();
      for (const [node, entry] of [...removed].reverse()) restoreRemoved(node, entry);
      removed.clear();
      retainedNodes = 0; retainedCharacters = 0; retainedAttributes = 0;
      attribute = marker();
    }

    return {
      retention() { return { removed: removed.size, nodes: retainedNodes, characters: retainedCharacters, attributes: retainedAttributes, overflows }; },
      update(values) {
        const next = JSON.stringify(values);
        if (next === signature) return;
        clear(); signature = next;
        rules = values.slice(0, 2048).flatMap(value => {
          try { const rule = JSON.parse(value); return Array.isArray(rule.selector) && rule.selector.length <= 32 ? [rule] : []; }
          catch { return []; }
        });
        changed(styles());
        if (rules.length) {
          observer.observe(document, {childList: true, subtree: true, attributes: true, characterData: true});
          run();
        }
      },
      disconnect() { clear(); rules = []; signature = ""; changed([]); },
    };
  }
  return { create };
})();
