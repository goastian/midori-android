(() => {
  "use strict";

  const resources = Promise.all([
    fetch(browser.runtime.getURL("scriptlets/resources.json")).then(response => response.json()),
    fetch(browser.runtime.getURL("scriptlets/public-suffixes.json")).then(response => response.json()),
  ]);
  let registrations = [];
  let queue = Promise.resolve();
  let appliedKey = "";

  function build(policy, catalogue, suffixes, world) {
    const entries = catalogue.entries;
    const rules = policy.rules.filter(rule => rule.id === null || entries[rule.id]?.world === world);
    if (!rules.some(rule => !rule.exception)) return "";
    const roots = [...new Set(rules.filter(rule => !rule.exception).map(rule => rule.id))];
    const needed = new Set();
    function include(id) {
      if (needed.has(id)) return;
      const entry = entries[id];
      if (!entry) throw new Error(`Unknown bundled scriptlet: ${id}`);
      entry.dependencies.forEach(include);
      needed.add(id);
    }
    roots.forEach(include);
    const declarations = [];
    const exports = [];
    for (const id of needed) {
      const entry = entries[id];
      if (entry.engine === "ubo") declarations.push(entry.code);
    }
    for (const id of roots) {
      const entry = entries[id];
      exports.push(entry.engine === "ubo" ? `${JSON.stringify(id)}: args => ${entry.code.match(/^(?:function|class)\s+([\w$]+)/)[1]}(...args)`
        : `${JSON.stringify(id)}: args => (${entry.code})({name:${JSON.stringify(entry.name)},args,engine:"extension",version:${JSON.stringify(catalogue.adguardVersion)},verbose:false},args)`);
    }
    const prepared = MidoriScriptletRuntime.prepare({ ...policy, rules });
    return `(() => {
      "use strict";
      const calls = (${MidoriScriptletRuntime.select.toString()})(${JSON.stringify(prepared)},${JSON.stringify(suffixes)});
      if (!calls.length) return;
      const key = Symbol.for("midori.scriptlet.calls");
      const executed = globalThis[key] instanceof Set ? globalThis[key] : new Set();
      if (!globalThis[key]) Object.defineProperty(globalThis, key, {value:executed});
      const scriptletGlobals = {};
      ${declarations.join("\n")}
      const functions = {${exports.join(",\n")}};
      for (const call of calls) {
        const signature = JSON.stringify([call.id,call.args]);
        if (executed.has(signature)) continue;
        try { functions[call.id](call.args); executed.add(signature); } catch {}
      }
    })();`;
  }

  function update(policy) {
    const key = JSON.stringify(policy);
    queue = queue.catch(() => {}).then(async () => {
      if (key === appliedKey) return;
      const [catalogue, suffixes] = await resources;
      const scripts = [];
      if (policy.level !== "OFF" || Object.values(policy.siteLevels).some(level => level !== "OFF")) {
        for (const world of ["MAIN", "ISOLATED"]) {
          const code = build(policy, catalogue, suffixes, world);
          if (code) scripts.push({ code, world });
        }
      }
      await Promise.all(registrations.map(registration => registration.unregister()));
      registrations = [];
      try {
        for (const { code, world } of scripts) {
          registrations.push(await browser.contentScripts.register({
            matches: ["http://*/*", "https://*/*"], allFrames: true, matchAboutBlank: true,
            runAt: "document_start", world, js: [{ code }],
          }));
        }
        appliedKey = key;
      } catch (error) {
        await Promise.allSettled(registrations.map(registration => registration.unregister()));
        registrations = [];
        appliedKey = "";
        throw error;
      }
    });
    return queue;
  }

  globalThis.MidoriScriptlets = Object.freeze({ update, build });
})();
