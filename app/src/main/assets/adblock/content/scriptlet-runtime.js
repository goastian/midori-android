(() => {
  "use strict";

  function prepare(policy) {
    const output = { ...policy, hosts: Object.create(null), entities: Object.create(null),
      ancestors: Object.create(null), ancestorEntities: Object.create(null), generic: [], regex: [] };
    const add = (index, key, position) => { (index[key] ||= []).push(position); };
    policy.rules.forEach((rule, position) => {
      if (!rule.included.length) output.generic.push(position);
      for (const raw of rule.included) {
        const ancestor = raw.endsWith(">>");
        const scope = ancestor ? raw.slice(0, -2) : raw;
        if (scope === "*") output.generic.push(position);
        else if (scope.startsWith("/")) output.regex.push(position);
        else if (scope.endsWith(".*")) add(ancestor ? output.ancestorEntities : output.entities, scope.slice(0, -2), position);
        else add(ancestor ? output.ancestors : output.hosts, scope, position);
      }
    });
    return output;
  }

  function select(policy, suffixList) {
    function hostOf(value) { try { return new URL(value).hostname.toLowerCase(); } catch { return ""; } }
    function domainMatches(host, domain) { return host === domain || host.endsWith(`.${domain}`); }
    const currentUrl = location.href;
    const host = location.hostname.toLowerCase();
    const ancestorUrls = [];
    let topUrl = currentUrl;
    let unknownTop = false;
    if (window !== window.top) {
      try { topUrl = window.top.location.href; }
      catch {
        const origins = Array.from(location.ancestorOrigins || []);
        if (origins.length) topUrl = origins.at(-1);
        else if (window.parent === window.top && document.referrer) topUrl = document.referrer;
        else unknownTop = true;
      }
      for (const origin of Array.from(location.ancestorOrigins || [])) ancestorUrls.push(origin);
      if (document.referrer) ancestorUrls.push(document.referrer);
      if (!unknownTop) ancestorUrls.push(topUrl);
    }
    if (unknownTop && (Object.values(policy.siteLevels).some(level => level !== policy.level) || policy.documentExceptions.length)) return [];
    const topHost = hostOf(topUrl);
    const level = Object.hasOwn(policy.siteLevels, topHost) ? policy.siteLevels[topHost] : policy.level;
    if (level === "OFF") return [];
    const enabled = rule => rule.tracker || level === "TRACKERS_AND_ADS";

    function networkMatch(pattern, url, matchCase) {
      const domainAnchor = pattern.startsWith("||");
      const anchored = domainAnchor || pattern.startsWith("|");
      const end = pattern.endsWith("|");
      let body = pattern.slice(domainAnchor ? 2 : anchored ? 1 : 0);
      if (end) body = body.slice(0, -1);
      if (!matchCase) { body = body.toLowerCase(); url = url.toLowerCase(); }
      const glob = (anchored ? "" : "*") + body + (end ? "" : "*");
      function at(start) {
        let position = start, rule = 0, star = -1, retry = start;
        while (position < url.length) {
          const expected = glob[rule];
          if (expected === "*") { star = rule++; retry = position; }
          else if (expected === url[position] || expected === "^" && !/[a-z0-9_.%\-]/i.test(url[position])) { position++; rule++; }
          else if (star >= 0) { rule = star + 1; position = ++retry; }
          else return false;
        }
        while (glob[rule] === "*" || glob[rule] === "^") rule++;
        return rule === glob.length;
      }
      if (!domainAnchor) return at(0);
      let start = url.indexOf("://") + 3;
      if (start < 3) return false;
      const authorityEnd = url.slice(start).search(/[:/?#]/);
      const endHost = authorityEnd < 0 ? url.length : start + authorityEnd;
      while (true) {
        if (at(start)) return true;
        const dot = url.indexOf(".", start);
        if (dot < 0 || dot >= endHost) return false;
        start = dot + 1;
      }
    }
    for (const rule of policy.documentExceptions) {
      if (!enabled(rule)) continue;
      for (const url of [currentUrl, topUrl]) {
        const pageHost = hostOf(url);
        if (rule.included.length && !rule.included.some(domain => domainMatches(pageHost, domain))) continue;
        if (rule.excluded.some(domain => domainMatches(pageHost, domain))) continue;
        if (networkMatch(rule.pattern, url, rule.matchCase)) return [];
      }
    }

    let suffixes;
    const entityCache = new Map();
    function entity(hostname) {
      if (entityCache.has(hostname)) return entityCache.get(hostname);
      suffixes ||= new Set(suffixList);
      const labels = hostname.split(".");
      let suffixLength = 1;
      for (let index = 0; index < labels.length; index++) {
        const suffix = labels.slice(index).join(".");
        if (suffixes.has(`!${suffix}`)) { suffixLength = labels.length - index - 1; break; }
        if (suffixes.has(suffix)) suffixLength = Math.max(suffixLength, labels.length - index);
        if (index > 0 && suffixes.has(`*.${suffix}`)) suffixLength = Math.max(suffixLength, labels.length - index + 1);
      }
      const result = labels.slice(0, -suffixLength).join(".");
      entityCache.set(hostname, result);
      return result;
    }
    const ancestors = [...new Set(ancestorUrls.map(hostOf).filter(Boolean))];
    function matches(raw) {
      const ancestor = raw.endsWith(">>");
      const scope = ancestor ? raw.slice(0, -2) : raw;
      const targets = ancestor ? ancestors : [host];
      if (scope === "*") return true;
      if (scope.startsWith("/")) {
        try { const regex = new RegExp(scope.slice(1, -1)); return targets.some(target => regex.test(target)); }
        catch { return false; }
      }
      if (scope.endsWith(".*")) return targets.some(target => domainMatches(entity(target), scope.slice(0, -2)));
      return targets.some(target => domainMatches(target, scope));
    }
    const candidates = new Set([...policy.generic, ...policy.regex]);
    function collect(index, hostname) {
      while (hostname) {
        for (const position of Object.hasOwn(index, hostname) ? index[hostname] : []) candidates.add(position);
        const dot = hostname.indexOf(".");
        if (dot < 0) break;
        hostname = hostname.slice(dot + 1);
      }
    }
    collect(policy.hosts, host);
    if (Object.keys(policy.entities).length) collect(policy.entities, entity(host));
    for (const ancestor of ancestors) {
      collect(policy.ancestors, ancestor);
      if (Object.keys(policy.ancestorEntities).length) collect(policy.ancestorEntities, entity(ancestor));
    }
    const applicable = [...candidates].map(index => policy.rules[index]).filter(rule => enabled(rule) &&
      (!rule.included.length || rule.included.some(matches)) && !rule.excluded.some(matches));
    const exceptions = applicable.filter(rule => rule.exception);
    if (exceptions.some(rule => rule.id === null)) return [];
    const calls = new Map();
    for (const rule of applicable) {
      if (rule.exception) continue;
      if (exceptions.some(exception => exception.id === rule.id &&
        (!exception.ublock && !exception.args.length || JSON.stringify(exception.args) === JSON.stringify(rule.args)))) continue;
      calls.set(JSON.stringify([rule.id, rule.args]), rule);
    }
    return [...calls.values()];
  }

  globalThis.MidoriScriptletRuntime = Object.freeze({ prepare, select });
})();
