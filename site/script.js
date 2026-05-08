(() => {
  "use strict";

  const REPO = "P0intMaN/terminus";
  const STAR_CACHE_KEY = "terminus.star_cache";
  const STAR_CACHE_TTL_MS = 30 * 60 * 1000;

  const fmt = (n) => {
    if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, "") + "k";
    return String(n);
  };

  const writeCache = (count) => {
    try {
      localStorage.setItem(STAR_CACHE_KEY, JSON.stringify({ count, at: Date.now() }));
    } catch { /* silent */ }
  };

  const readCache = () => {
    try {
      const raw = localStorage.getItem(STAR_CACHE_KEY);
      if (!raw) return null;
      const { count, at } = JSON.parse(raw);
      if (Date.now() - at > STAR_CACHE_TTL_MS) return null;
      return count;
    } catch { return null; }
  };

  const renderStars = (count) => {
    document.querySelectorAll("[data-star-count]").forEach((el) => {
      el.textContent = fmt(count);
    });
    document.querySelectorAll("[data-star-pill]").forEach((el) => {
      el.hidden = false;
    });
  };

  const fetchStars = async () => {
    const cached = readCache();
    if (cached !== null) { renderStars(cached); return; }
    try {
      const res = await fetch(`https://api.github.com/repos/${REPO}`, {
        headers: { Accept: "application/vnd.github.v3+json" },
      });
      if (!res.ok) return;
      const data = await res.json();
      if (typeof data.stargazers_count !== "number") return;
      writeCache(data.stargazers_count);
      renderStars(data.stargazers_count);
    } catch { /* network down — pills stay hidden */ }
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", fetchStars);
  } else {
    fetchStars();
  }
})();
