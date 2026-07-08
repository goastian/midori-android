function loadOptionsPage() {
  import('./options.js').catch((error) => {
    console.error('[midori] Failed to load options page', error);
  });
}

function scheduleOptionsLoad() {
  if (typeof window.requestIdleCallback === 'function') {
    window.requestIdleCallback(() => loadOptionsPage(), { timeout: 250 });
    return;
  }

  window.requestAnimationFrame(() => {
    window.setTimeout(loadOptionsPage, 0);
  });
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', scheduleOptionsLoad, { once: true });
} else {
  scheduleOptionsLoad();
}