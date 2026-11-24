/* Synchronous, first-paint theme setup; reads only the existing local UI preference. */
(() => {
  const root = document.documentElement;
  let theme = 'light'; // Keep the original default for a new installation.
  try {
    const raw = localStorage.getItem('dorm-os-state-v1');
    if (raw) {
      const saved = JSON.parse(raw);
      if (saved?.version === 1 && saved.settings?.theme === 'dark') theme = 'dark';
    }
  } catch {
    // Unavailable or invalid storage must never prevent the page from loading.
  }
  root.dataset.theme = theme;
  root.classList.toggle('theme-light', theme === 'light');
})();
