globalThis.MidoriCosmetics = (() => {
  "use strict";

  function create(changed) {
    const attribute = `data-midori-${Array.from(crypto.getRandomValues(new Uint32Array(2)), n => n.toString(16)).join("")}`;
    let rules = [], signature = "", timer = null, cursor = 0, work = null, rerun = false;
    const marked = new Map(), removed = new Map(), attributes = new Map();
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

    function remember(node, name) {
      if (!attributes.has(node)) attributes.set(node, new Map());
      const values = attributes.get(node);
      if (!values.has(name)) values.set(name, node.getAttribute(name));
    }

    function* apply(rule, index) {
      const nodes = yield* select(rule);
      const action = rule.action?.type;
      if (action === "remove") {
        for (const node of nodes) {
          yield;
          if (!removed.has(node)) removed.set(node, [node.parentNode, node.nextSibling]);
          node.remove();
        }
        return;
      }
      if (action === "remove-attr" || action === "remove-class") {
        const test = namePattern(rule.action.arg || "");
        for (const node of nodes) {
          yield;
          if (action === "remove-attr") {
            for (const name of node.getAttributeNames().filter(test)) { remember(node, name); node.removeAttribute(name); }
          } else {
            for (const name of [...node.classList].filter(test)) { remember(node, "class"); node.classList.remove(name); }
          }
        }
        return;
      }
      if (action && action !== "style") return;
      const previous = marked.get(index) || new Set(), next = new Set(nodes), token = `r${index}`;
      const tracked = new Set(previous);
      marked.set(index, tracked);
      for (const node of new Set([...previous, ...next])) {
        yield;
        const tokens = new Set((node.getAttribute(attribute) || "").split(" ").filter(Boolean));
        if (next.has(node)) { tokens.add(token); tracked.add(node); }
        else { tokens.delete(token); tracked.delete(node); }
        const value = [...tokens].join(" ");
        if (value !== (node.getAttribute(attribute) || "")) {
          if (value) node.setAttribute(attribute, value); else node.removeAttribute(attribute);
        }
      }
      marked.set(index, next);
    }

    function styles() {
      return rules.flatMap((rule, index) => {
        if (rule.action && rule.action.type !== "style") return [];
        const declarations = rule.action?.arg || "display: none !important";
        if (/[{}\r\n@]|url\s*\(/i.test(declarations)) return [];
        return [{selector: `[${attribute}~="r${index}"]`, declarations}];
      });
    }

    function run() {
      timer = null;
      const deadline = performance.now() + 12;
      let steps = 0;
      while (cursor < rules.length) {
        try {
          if (!work) work = apply(rules[cursor], cursor);
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
      for (const nodes of marked.values()) for (const node of nodes) node.removeAttribute(attribute);
      marked.clear();
      for (const [node, values] of attributes) for (const [name, value] of values) {
        if (value === null) node.removeAttribute(name); else node.setAttribute(name, value);
      }
      attributes.clear();
      for (const [node, [parent, sibling]] of [...removed].reverse()) {
        if (!node.isConnected && parent?.isConnected) parent.insertBefore(node, sibling?.parentNode === parent ? sibling : null);
      }
      removed.clear();
    }

    return {
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
