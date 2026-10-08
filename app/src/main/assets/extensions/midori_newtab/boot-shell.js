// Runs before Vue and its locale chunk. Extension pages cannot use inline JS.
(() => {
  const root = document.documentElement;
  let snapshot;
  try {
    const raw = localStorage.getItem('midori_boot_snapshot_v1');
    if (raw && new Blob([raw]).size <= 4096) {
      const parsed = JSON.parse(raw);
      if (parsed?.version === 1) snapshot = parsed;
    }
  } catch { /* The shell still works without storage. */ }

  let theme = snapshot?.theme === 'dark' ? 'dark' : 'light';
  if (snapshot?.autoTheme) theme = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  root.dataset.theme = theme;
  root.dataset.density = snapshot?.density === 'compact' ? 'compact' : 'comfortable';
  const vars = snapshot?.themeVars?.[theme];
  if (vars && typeof vars === 'object') {
    for (const [key, value] of Object.entries(vars)) {
      if (/^--[a-z0-9-]{1,40}$/.test(key) && typeof value === 'string' && value.length <= 100) {
        root.style.setProperty(key, value);
      }
    }
  }

  const copy = {
    en: ['Search the web', 'Search'], es: ['Buscar en la web', 'Buscar'],
    pt: ['Pesquisar na web', 'Pesquisar'], fr: ['Rechercher sur le Web', 'Rechercher'],
    de: ['Im Web suchen', 'Suchen'], it: ['Cerca sul web', 'Cerca'],
    ru: ['Поиск в интернете', 'Поиск'], zh: ['搜索网页', '搜索'], ja: ['ウェブを検索', '検索'],
  };
  let locale = '';
  try { locale = JSON.parse(localStorage.getItem('i18nStore') || '{}').locale || ''; } catch { /* use browser locale */ }
  locale = String(locale || navigator.language || 'en').toLowerCase().split('-')[0];
  if (!copy[locale]) locale = 'en';
  root.lang = locale;
  const applyCopy = () => {
    const input = document.getElementById('midori-boot-search');
    const label = document.querySelector('.boot-shell__label');
    const button = document.querySelector('.boot-shell__button');
    if (!input || !label || !button) return false;
    input.placeholder = copy[locale][0];
    label.textContent = copy[locale][0];
    button.textContent = copy[locale][1];
    return true;
  };
  if (!applyCopy()) {
    if (typeof MutationObserver !== 'undefined') {
      const observer = new MutationObserver(() => { if (applyCopy()) observer.disconnect(); });
      observer.observe(root, { childList: true, subtree: true });
    } else document.addEventListener('DOMContentLoaded', applyCopy, { once: true });
  }
})();
