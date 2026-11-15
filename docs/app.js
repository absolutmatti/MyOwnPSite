const STORAGE_KEY = "videothek_web_catalog_v1";

const INITIAL_PERFORMER_NAMES = [
  "Emily Willis",
  "Angela White",
  "Kourtney Love",
  "Raissa Bellini",
  "Kendra Lust",
  "Jennifer White",
  "Payton Preslee",
  "Mia Malkova",
  "Blake Blossom",
  "Adriana Chechik",
  "Ava Addams",
  "Tru Kait",
  "Roxie Sinner",
  "Autumn Falls",
  "Vitoria Beatriz",
  "Aria Lee",
  "Sheena Ryder",
  "Armani Black",
  "Carmela Clutch",
  "Melztube",
  "Scarlett Alexis",
  "Chanel Camryn",
  "Lela Star",
  "Madison Wilde",
  "Mandy Waters",
  "Xxlayna Marie",
  "April Olsen",
  "Sisi Rose",
  "Bunny Madison",
  "Cassie Del Isla",
  "Silvana Lee",
  "Serena Santos",
  "Misty Meaner",
  "Mandy Rhea",
  "Nina North",
  "Cami Strella",
  "Alexa Payne",
  "Sofie Reyez",
  "Monica Minx",
  "Camila Cortez",
  "Havana Bleu",
  "Maddy Black",
  "Sasha Foxxx",
  "Lexis Star",
  "Lily Jordan",
  "The Official Egypt",
  "Crystal Rush",
  "Samantha Bentley",
  "Kiley Jay",
  "Chanel Shortcake",
];

const BUCKET_RANGES = {
  ALL: null,
  A_E: ["A", "E"],
  F_J: ["F", "J"],
  K_O: ["K", "O"],
  P_T: ["P", "T"],
  U_Z: ["U", "Z"],
};

const state = {
  performers: loadCatalog(),
  sort: "alpha_asc",
  bucket: "ALL",
  query: "",
  selectedPerformerId: null,
};

function loadCatalog() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed.map((p) => ({
          id: String(p.id),
          name: String(p.name),
          imageUrl: p.imageUrl || null,
          videos: Array.isArray(p.videos) ? p.videos : [],
        }));
      }
    }
  } catch {
    // Fallback to initial seed
  }
  const seeded = INITIAL_PERFORMER_NAMES.map((name, index) => ({
    id: String(index + 1),
    name,
    imageUrl: null,
    videos: [],
  }));
  saveCatalog(seeded);
  return seeded;
}

function saveCatalog(performers = state.performers) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(performers));
}

function getInitials(name) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

function normalizeUrl(rawUrl) {
  const trimmed = rawUrl.trim();
  if (!trimmed) return "";
  if (/^(https?:\/\/|data:|blob:|file:\/\/)/i.test(trimmed)) return trimmed;
  return `https://${trimmed}`;
}

function deriveTitle(url) {
  const clean = url.split("?")[0].split("#")[0].replace(/\/+$/, "");
  const segment = clean.split("/").pop();
  return segment || url.replace(/^https?:\/\//i, "").slice(0, 36);
}

function escapeHtml(text) {
  return String(text)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function showToast(message) {
  const toast = document.getElementById("toast");
  toast.textContent = message;
  toast.classList.remove("hidden");
  clearTimeout(showToast._timer);
  showToast._timer = setTimeout(() => toast.classList.add("hidden"), 2600);
}

function getFilteredPerformers() {
  const q = state.query.trim().toLowerCase();
  const range = BUCKET_RANGES[state.bucket];

  const filtered = state.performers.filter((p) => {
    const matchesQuery = !q || p.name.toLowerCase().includes(q);
    let matchesBucket = true;
    if (range) {
      const ch = p.name.trim().charAt(0).toUpperCase();
      matchesBucket = ch >= range[0] && ch <= range[1];
    }
    return matchesQuery && matchesBucket;
  });

  return filtered.sort((a, b) => {
    switch (state.sort) {
      case "alpha_desc":
        return b.name.localeCompare(a.name, "de", { sensitivity: "base" });
      case "videos_desc":
        return (b.videos.length - a.videos.length) || a.name.localeCompare(b.name, "de", { sensitivity: "base" });
      case "videos_asc":
        return (a.videos.length - b.videos.length) || a.name.localeCompare(b.name, "de", { sensitivity: "base" });
      case "alpha_asc":
      default:
        return a.name.localeCompare(b.name, "de", { sensitivity: "base" });
    }
  });
}

function renderPortraitMarkup(performer, showEmptyRatioBadge = true) {
  const initials = escapeHtml(getInitials(performer.name));
  const safeName = escapeHtml(performer.name);
  const videoCount = performer.videos.length;

  const mediaMarkup = performer.imageUrl
    ? `<img class="portrait-img" src="${escapeHtml(performer.imageUrl)}" alt="${safeName}" loading="lazy" />`
    : `<div class="portrait-empty-frame"><div class="monogram-circle">${initials}</div></div>`;

  const leftBadge = !performer.imageUrl && showEmptyRatioBadge
    ? `<span class="badge-pill">9:16</span>`
    : `<span></span>`;

  const rightBadge = videoCount > 0
    ? `<span class="badge-pill gold">${videoCount} ${videoCount === 1 ? "Video" : "Videos"}</span>`
    : ``;

  return `
    ${mediaMarkup}
    <div class="tile-badges">${leftBadge}${rightBadge}</div>
    <div class="tile-scrim">
      <p class="tile-name">${safeName}</p>
    </div>
  `;
}

function render() {
  const totalLinks = state.performers.reduce((sum, p) => sum + p.videos.length, 0);
  const visible = getFilteredPerformers();

  document.getElementById("catalog-stats").textContent =
    `${visible.length} von ${state.performers.length} Darstellerinnen • ${totalLinks} Video-Links`;

  const catalogView = document.getElementById("catalog-view");
  const detailView = document.getElementById("detail-view");

  const activePerformer = state.selectedPerformerId
    ? state.performers.find((p) => p.id === state.selectedPerformerId)
    : null;

  if (activePerformer) {
    catalogView.classList.add("hidden");
    detailView.classList.remove("hidden");
    renderDetail(activePerformer);
  } else {
    detailView.classList.add("hidden");
    catalogView.classList.remove("hidden");
    renderCatalogGrid(visible);
  }
}

function renderCatalogGrid(visible) {
  const grid = document.getElementById("performer-grid");
  const emptyState = document.getElementById("empty-state");

  if (visible.length === 0) {
    grid.innerHTML = "";
    emptyState.classList.remove("hidden");
    return;
  }

  emptyState.classList.add("hidden");
  grid.innerHTML = visible
    .map(
      (p) => `
      <button class="portrait-card" type="button" data-open-id="${escapeHtml(p.id)}">
        ${renderPortraitMarkup(p, true)}
      </button>
    `
    )
    .join("");
}

function renderDetail(performer) {
  document.getElementById("detail-portrait-card").innerHTML = renderPortraitMarkup(performer, false);
  document.getElementById("detail-name").textContent = performer.name;

  const removeBtn = document.getElementById("btn-performer-remove-image");
  removeBtn.classList.toggle("hidden", !performer.imageUrl);

  const count = performer.videos.length;
  document.getElementById("detail-pills").innerHTML = `
    <span class="badge-pill ${count > 0 ? "gold" : ""}">${count} ${count === 1 ? "Video" : "Videos"}</span>
    <span class="badge-pill ${performer.imageUrl ? "gold" : ""}">${performer.imageUrl ? "9:16-Bild aktiv" : "Leere 9:16-Kachel"}</span>
  `;

  const videoList = document.getElementById("video-list");
  const videoEmpty = document.getElementById("video-empty");

  if (count === 0) {
    videoList.innerHTML = "";
    videoEmpty.classList.remove("hidden");
    return;
  }

  videoEmpty.classList.add("hidden");
  videoList.innerHTML = performer.videos
    .map((video) => {
      const thumbMarkup = video.thumbnailUrl
        ? `<img class="video-thumb-img" src="${escapeHtml(video.thumbnailUrl)}" alt="${escapeHtml(video.title)}" loading="lazy" />`
        : ``;
      return `
        <article class="video-card">
          <a class="video-link-anchor" href="${escapeHtml(video.url)}" target="_blank" rel="noopener noreferrer">
            <div class="video-thumb-wrap">
              ${thumbMarkup}
              <span class="video-play-circle" aria-hidden="true">▶</span>
            </div>
            <div class="video-card-body">
              <h4 class="video-card-title">${escapeHtml(video.title)}</h4>
              <p class="video-card-url">${escapeHtml(video.url)}</p>
            </div>
          </a>
          <div class="video-card-actions">
            <button class="secondary-btn" type="button" data-edit-video="${escapeHtml(video.id)}">Bearbeiten</button>
            <button class="danger-btn" type="button" data-delete-video="${escapeHtml(video.id)}">Löschen</button>
          </div>
        </article>
      `;
    })
    .join("");
}

function openPerformer(id, pushHash = true) {
  state.selectedPerformerId = id;
  if (pushHash) {
    window.location.hash = `performer/${encodeURIComponent(id)}`;
  }
  render();
  window.scrollTo({ top: 0, behavior: "instant" });
}

function closeDetail(pushHash = true) {
  state.selectedPerformerId = null;
  if (pushHash && window.location.hash) {
    window.location.hash = "";
  }
  render();
}

function openModal({ title, fields, submitLabel = "Speichern", onSubmit }) {
  const backdrop = document.getElementById("modal-backdrop");
  const form = document.getElementById("modal-form");
  const titleEl = document.getElementById("modal-title");
  const fieldsEl = document.getElementById("modal-fields");
  const submitBtn = document.getElementById("modal-submit");

  titleEl.textContent = title;
  submitBtn.textContent = submitLabel;

  fieldsEl.innerHTML = fields
    .map(
      (f) => `
      <div class="modal-field">
        <label for="field-${f.name}">${escapeHtml(f.label)}</label>
        <input
          id="field-${f.name}"
          name="${escapeHtml(f.name)}"
          type="${f.type || "text"}"
          value="${escapeHtml(f.value || "")}"
          placeholder="${escapeHtml(f.placeholder || "")}"
          ${f.required ? "required" : ""}
        />
      </div>
    `
    )
    .join("");

  backdrop.classList.remove("hidden");
  const firstInput = fieldsEl.querySelector("input");
  if (firstInput) firstInput.focus();

  const close = () => backdrop.classList.add("hidden");

  form.onsubmit = (e) => {
    e.preventDefault();
    const data = {};
    for (const f of fields) {
      data[f.name] = form.elements[f.name].value;
    }
    onSubmit(data);
    close();
  };

  document.getElementById("modal-cancel").onclick = close;
  backdrop.onclick = (e) => {
    if (e.target === backdrop) close();
  };
}

function compressImageFile(file, maxWidth = 360, maxHeight = 640) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = reject;
    reader.onload = () => {
      const img = new Image();
      img.onerror = reject;
      img.onload = () => {
        const ratio = Math.min(maxWidth / img.width, maxHeight / img.height, 1);
        const w = Math.round(img.width * ratio);
        const h = Math.round(img.height * ratio);
        const canvas = document.createElement("canvas");
        canvas.width = w;
        canvas.height = h;
        canvas.getContext("2d").drawImage(img, 0, 0, w, h);
        resolve(canvas.toDataURL("image/jpeg", 0.82));
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

function syncFromHash() {
  const hash = window.location.hash;
  if (hash.startsWith("#performer/")) {
    const id = decodeURIComponent(hash.slice("#performer/".length));
    if (state.performers.some((p) => p.id === id)) {
      state.selectedPerformerId = id;
    } else {
      state.selectedPerformerId = null;
    }
  } else {
    state.selectedPerformerId = null;
  }
  render();
}

function initEvents() {
  window.addEventListener("hashchange", syncFromHash);

  document.getElementById("search-input").addEventListener("input", (e) => {
    state.query = e.target.value;
    render();
  });

  document.querySelectorAll("[data-sort]").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.sort = btn.dataset.sort;
      document.querySelectorAll("[data-sort]").forEach((b) => b.classList.toggle("active", b === btn));
      render();
    });
  });

  document.querySelectorAll("[data-bucket]").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.bucket = btn.dataset.bucket;
      document.querySelectorAll("[data-bucket]").forEach((b) => b.classList.toggle("active", b === btn));
      render();
    });
  });

  document.getElementById("btn-reset-filters").addEventListener("click", () => {
    state.query = "";
    state.bucket = "ALL";
    state.sort = "alpha_asc";
    document.getElementById("search-input").value = "";
    document.querySelectorAll("[data-sort]").forEach((b) => b.classList.toggle("active", b.dataset.sort === "alpha_asc"));
    document.querySelectorAll("[data-bucket]").forEach((b) => b.classList.toggle("active", b.dataset.bucket === "ALL"));
    render();
  });

  document.getElementById("performer-grid").addEventListener("click", (e) => {
    const card = e.target.closest("[data-open-id]");
    if (card) {
      openPerformer(card.dataset.openId);
    }
  });

  document.getElementById("btn-back").addEventListener("click", () => closeDetail());

  document.getElementById("btn-add-performer").addEventListener("click", () => {
    openModal({
      title: "Neue Darstellerin hinzufügen",
      fields: [{ name: "name", label: "Name", placeholder: "z. B. Vorname Nachname", required: true }],
      onSubmit: ({ name }) => {
        const trimmed = name.trim();
        if (!trimmed) return;
        const newItem = {
          id: String(Date.now()),
          name: trimmed,
          imageUrl: null,
          videos: [],
        };
        state.performers.push(newItem);
        saveCatalog();
        showToast(`${trimmed} hinzugefügt`);
        render();
      },
    });
  });

  document.getElementById("input-performer-file").addEventListener("change", async (e) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file || !state.selectedPerformerId) return;
    try {
      const dataUrl = await compressImageFile(file);
      const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
      if (!performer) return;
      performer.imageUrl = dataUrl;
      saveCatalog();
      showToast("9:16-Bild gespeichert");
      render();
    } catch {
      showToast("Bild konnte nicht geladen werden");
    }
  });

  document.getElementById("btn-performer-image-url").addEventListener("click", () => {
    const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
    if (!performer) return;
    openModal({
      title: "Bild-URL festlegen",
      fields: [{ name: "url", label: "Bild-URL", value: performer.imageUrl || "", placeholder: "https://…", required: true }],
      onSubmit: ({ url }) => {
        performer.imageUrl = normalizeUrl(url);
        saveCatalog();
        showToast("Bild gespeichert");
        render();
      },
    });
  });

  document.getElementById("btn-performer-remove-image").addEventListener("click", () => {
    const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
    if (!performer) return;
    performer.imageUrl = null;
    saveCatalog();
    showToast("Bild entfernt");
    render();
  });

  document.getElementById("btn-rename-performer").addEventListener("click", () => {
    const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
    if (!performer) return;
    openModal({
      title: "Namen bearbeiten",
      fields: [{ name: "name", label: "Name", value: performer.name, required: true }],
      onSubmit: ({ name }) => {
        const trimmed = name.trim();
        if (!trimmed) return;
        performer.name = trimmed;
        saveCatalog();
        showToast("Name aktualisiert");
        render();
      },
    });
  });

  document.getElementById("btn-delete-performer").addEventListener("click", () => {
    const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
    if (!performer) return;
    if (!window.confirm(`"${performer.name}" wirklich löschen?`)) return;
    state.performers = state.performers.filter((p) => p.id !== performer.id);
    saveCatalog();
    showToast("Eintrag gelöscht");
    closeDetail();
  });

  document.getElementById("btn-add-video").addEventListener("click", () => {
    const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
    if (!performer) return;
    openModal({
      title: "Video-Link hinzufügen",
      fields: [
        { name: "url", label: "Video-URL", placeholder: "https://…", required: true },
        { name: "title", label: "Titel des Videos (optional)", placeholder: "z. B. Szene Titel" },
        { name: "thumbnailUrl", label: "Thumbnail-URL (optional)", placeholder: "https://…" },
      ],
      onSubmit: ({ url, title, thumbnailUrl }) => {
        const normalizedUrl = normalizeUrl(url);
        if (!normalizedUrl) return;
        performer.videos.unshift({
          id: String(Date.now()),
          url: normalizedUrl,
          title: title.trim() || deriveTitle(normalizedUrl),
          thumbnailUrl: thumbnailUrl.trim() ? normalizeUrl(thumbnailUrl) : null,
        });
        saveCatalog();
        showToast("Video-Link gespeichert");
        render();
      },
    });
  });

  document.getElementById("video-list").addEventListener("click", (e) => {
    const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
    if (!performer) return;

    const editBtn = e.target.closest("[data-edit-video]");
    if (editBtn) {
      const video = performer.videos.find((v) => v.id === editBtn.dataset.editVideo);
      if (!video) return;
      openModal({
        title: "Video-Link bearbeiten",
        fields: [
          { name: "url", label: "Video-URL", value: video.url, required: true },
          { name: "title", label: "Titel des Videos", value: video.title },
          { name: "thumbnailUrl", label: "Thumbnail-URL (optional)", value: video.thumbnailUrl || "" },
        ],
        onSubmit: ({ url, title, thumbnailUrl }) => {
          const normalizedUrl = normalizeUrl(url);
          if (!normalizedUrl) return;
          video.url = normalizedUrl;
          video.title = title.trim() || deriveTitle(normalizedUrl);
          video.thumbnailUrl = thumbnailUrl.trim() ? normalizeUrl(thumbnailUrl) : null;
          saveCatalog();
          showToast("Video-Link aktualisiert");
          render();
        },
      });
      return;
    }

    const deleteBtn = e.target.closest("[data-delete-video]");
    if (deleteBtn) {
      performer.videos = performer.videos.filter((v) => v.id !== deleteBtn.dataset.deleteVideo);
      saveCatalog();
      showToast("Video-Link entfernt");
      render();
    }
  });

  document.getElementById("btn-export").addEventListener("click", () => {
    const blob = new Blob([JSON.stringify(state.performers, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "videothek-backup.json";
    link.click();
    URL.revokeObjectURL(url);
    showToast("Backup exportiert");
  });

  document.getElementById("input-import").addEventListener("change", async (e) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    try {
      const text = await file.text();
      const parsed = JSON.parse(text);
      if (!Array.isArray(parsed)) throw new Error("Invalid format");
      state.performers = parsed.map((p, i) => ({
        id: String(p.id || i + 1),
        name: String(p.name),
        imageUrl: p.imageUrl || null,
        videos: Array.isArray(p.videos) ? p.videos : [],
      }));
      saveCatalog();
      showToast("Backup importiert");
      closeDetail();
    } catch {
      showToast("Ungültige Backup-Datei");
    }
  });
}

initEvents();
syncFromHash();
