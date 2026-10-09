(() => {
  "use strict";

  let policy = null;
  let rejectText = null;
  let handled = false;
  let userInteracted = false;
  let timer = null;
  const observedRoots = new Set();
  const observer = new MutationObserver(schedule);
  const port = browser.runtime.connectNative("midori_adblock");

  function stop() {
    observer.disconnect();
    observedRoots.clear();
    clearTimeout(timer);
    timer = null;
  }

  function visible(element) {
    if (!element.isConnected || element.disabled || element.getAttribute("aria-disabled") === "true" ||
        element.closest("[hidden], [inert], [aria-hidden='true']") || !element.getClientRects().length) return false;
    const style = getComputedStyle(element);
    return style.display !== "none" && style.visibility !== "hidden" &&
      style.visibility !== "collapse" && style.opacity !== "0";
  }

  function roots() {
    const result = [document];
    for (const root of result) {
      for (const host of root.querySelectorAll(policy.shadowHosts.join(","))) {
        if (host.shadowRoot && !result.includes(host.shadowRoot)) {
          result.push(host.shadowRoot);
          if (!observedRoots.has(host.shadowRoot)) {
            observedRoots.add(host.shadowRoot);
            observer.observe(host.shadowRoot, { childList: true, subtree: true, attributes: true,
              attributeFilter: ["class", "style", "hidden", "disabled", "aria-hidden", "aria-disabled"] });
          }
        }
      }
    }
    return result;
  }

  function rejectButton() {
    for (const root of roots()) {
      for (const button of root.querySelectorAll(policy.rejectSelectors.join(","))) {
        if (button.matches("button, input[type='button'], input[type='submit'], a, [role='button']") &&
            visible(button)) return button;
      }
      for (const banner of root.querySelectorAll(policy.bannerSelectors.join(","))) {
        if (!visible(banner)) continue;
        for (const button of banner.querySelectorAll("button, input[type='button'], input[type='submit'], [role='button']")) {
          const label = (button.textContent || button.value || button.getAttribute("aria-label") || "")
            .normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/\s+/g, " ").trim();
          if (rejectText.test(label) && visible(button)) return button;
        }
      }
    }
    return null;
  }

  function scan() {
    timer = null;
    if (!policy?.enabled || handled || userInteracted) return;
    const button = rejectButton();
    if (!button) return;
    handled = true;
    stop();
    button.click();
  }

  function schedule() {
    if (timer === null && policy?.enabled && !handled && !userInteracted) {
      timer = setTimeout(scan, 150);
    }
  }

  function manualInteraction(event) {
    if (!event.isTrusted ||
        (event.type === "keydown" && event.key !== "Enter" && event.key !== " ")) return;
    if (!policy) {
      userInteracted = true;
      stop();
      return;
    }
    const selectors = [...policy.bannerSelectors, ...policy.rejectSelectors, ...policy.shadowHosts].join(",");
    if (event.composedPath().some(node => node instanceof Element && node.closest(selectors))) {
      userInteracted = true;
      stop();
    }
  }

  document.addEventListener("pointerdown", manualInteraction, true);
  document.addEventListener("click", manualInteraction, true);
  document.addEventListener("keydown", manualInteraction, true);
  window.addEventListener("pagehide", stop);
  window.addEventListener("pageshow", () => {
    if (policy?.enabled && !handled && !userInteracted) observe();
  });

  function observe() {
    observer.observe(document, { childList: true, subtree: true, attributes: true,
      attributeFilter: ["class", "style", "hidden", "disabled", "aria-hidden", "aria-disabled"] });
    roots();
    schedule();
  }

  port.onMessage.addListener(message => {
    if (message?.type !== "cookie_banner_policy") return;
    stop();
    policy = message;
    rejectText = new RegExp(policy.rejectText.normalize("NFD").replace(/[\u0300-\u036f]/g, ""), "i");
    if (policy.enabled && !handled && !userInteracted) observe();
  });
  port.onDisconnect.addListener(() => {
    stop();
    policy = null;
  });
  port.postMessage({ type: "ready" });
})();
