globalThis.MidoriCosmetics = (() => {
  "use strict";

  function create(changed) {
    const attribute = `data-midori-${Array.from(crypto.getRandomValues(new Uint32Array(2)), n => n.toString(16)).join("")}`;
    let rules = [], signature = "", timer = null, cursor = 0;
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

    function select(rule) {
      let nodes = [document];
      for (const operator of rule.selector) {
        const arg = operator.arg || "";
        switch (operator.type) {
          case "css-selector":
            nodes = nodes.flatMap(node => [...node.querySelectorAll(/^[>+~]/.test(arg.trim()) ? `:scope ${arg}` : arg)].slice(0, 5000));
            break;
          case "has-text": { const test = pattern(arg); nodes = nodes.filter(node => test(node.textContent || "")); break; }
          case "min-text-length":
            if (!/^\d+$/.test(arg)) return [];
            nodes = nodes.filter(node => (node.textContent || "").length >= Number(arg));
            break;
          case "matches-path": if (!pattern(arg)(location.pathname + location.search)) return []; break;
          case "matches-attr": {
            const split = arg.indexOf("="), name = namePattern(split < 0 ? arg : arg.slice(0, split).trim());
            const value = split < 0 ? null : pattern(arg.slice(split + 1).trim());
            nodes = nodes.filter(node => node.getAttributeNames?.().some(key => name(key) && (!value || value(node.getAttribute(key)))));
            break;
          }
          case "matches-css": case "matches-css-before": case "matches-css-after": {
            const split = arg.indexOf(":"), property = arg.slice(0, split).trim();
            if (split < 1 || !/^[\w-]+$/.test(property)) return [];
            const test = pattern(arg.slice(split + 1).trim());
            const pseudo = operator.type === "matches-css" ? null : `::${operator.type.slice(12)}`;
            nodes = nodes.filter(node => test(getComputedStyle(node, pseudo).getPropertyValue(property)));
            break;
          }
          case "upward": {
            nodes = nodes.map(node => {
              if (!/^\d+$/.test(arg)) return node.parentElement?.closest(arg);
              const distance = Number(arg);
              if (distance < 1 || distance > 255) return null;
              for (let step = 0; step < distance && node; step++) node = node.parentElement;
              return node;
            }).filter(Boolean);
            break;
          }
          case "xpath":
            nodes = nodes.flatMap(node => {
              const result = document.evaluate(arg, node, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
              return Array.from({length: Math.min(result.snapshotLength, 5000)}, (_, i) => result.snapshotItem(i));
            });
            break;
          default: return [];
        }
        nodes = [...new Set(nodes)].slice(0, 5000);
      }
      return nodes.filter(node => node.nodeType === 1);
    }

    function remember(node, name) {
      if (!attributes.has(node)) attributes.set(node, new Map());
      const values = attributes.get(node);
      if (!values.has(name)) values.set(name, node.getAttribute(name));
    }

    function apply(rule, index) {
      const nodes = select(rule), action = rule.action?.type;
      if (action === "remove") {
        for (const node of nodes) {
          if (!removed.has(node)) removed.set(node, [node.parentNode, node.nextSibling]);
          node.remove();
        }
        return;
      }
      if (action === "remove-attr" || action === "remove-class") {
        const test = namePattern(rule.action.arg || "");
        for (const node of nodes) {
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
      for (const node of new Set([...previous, ...next])) {
        const tokens = new Set((node.getAttribute(attribute) || "").split(" ").filter(Boolean));
        if (next.has(node)) tokens.add(token); else tokens.delete(token);
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
      while (cursor < rules.length) {
        try { apply(rules[cursor], cursor); } catch {}
        cursor++;
        if (performance.now() > deadline && cursor < rules.length) { timer = setTimeout(run, 32); return; }
      }
      cursor = 0;
    }

    function schedule() { if (timer === null && rules.length) timer = setTimeout(run, 64); }

    function clear() {
      observer.disconnect();
      clearTimeout(timer); timer = null; cursor = 0;
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
