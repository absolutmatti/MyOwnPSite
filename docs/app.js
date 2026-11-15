import { initializeApp } from "https://www.gstatic.com/firebasejs/11.4.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
} from "https://www.gstatic.com/firebasejs/11.4.0/firebase-auth.js";
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  initializeFirestore,
  limit,
  onSnapshot,
  persistentLocalCache,
  persistentMultipleTabManager,
  query,
  serverTimestamp,
  updateDoc,
  where,
  writeBatch,
} from "https://www.gstatic.com/firebasejs/11.4.0/firebase-firestore.js";

const FIREBASE_CONFIG = {
  apiKey: "AIzaSyAAojWAlJ0sY4br6wTadp--5DQeIOd0lXU",
  authDomain: "gen-lang-client-0990924160.firebaseapp.com",
  projectId: "gen-lang-client-0990924160",
  storageBucket: "gen-lang-client-0990924160.firebasestorage.app",
  messagingSenderId: "649516062418",
};

const FIRESTORE_DATABASE_ID =
  "ai-studio-android-videothe-ef1b650e-c084-4a19-b70d-cd1c7d3909b6";

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

const app = initializeApp(FIREBASE_CONFIG);
const auth = getAuth(app);

let db;
try {
  db = initializeFirestore(
    app,
    {
      localCache: persistentLocalCache({
        tabManager: persistentMultipleTabManager(),
      }),
    },
    FIRESTORE_DATABASE_ID
  );
} catch {
  db = getFirestore(app, FIRESTORE_DATABASE_ID);
}

const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: "select_account" });

const state = {
  user: null,
  performersRaw: [],
  videoLinksRaw: [],
  performers: [],
  sort: "alpha_asc",
  bucket: "ALL",
  query: "",
  selectedPerformerId: null,
  unsubPerformers: null,
  unsubLinks: null,
  hasLoadedPerformers: false,
  hasLoadedLinks: false,
};

function getInitials(name) {
  const parts = String(name || "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

function normalizeUrl(rawUrl) {
  const trimmed = String(rawUrl || "").trim();
  if (!trimmed) return "";
  if (/^(https?:\/\/|data:|blob:|file:\/\/)/i.test(trimmed)) return trimmed;
  return `https://${trimmed}`;
}

function deriveTitle(url) {
  const clean = url.split("?")[0].split("#")[0].replace(/\/+$/, "");
  const segment = clean.split("/").pop();
  return (segment || url.replace(/^https?:\/\//i, "")).slice(0, 200);
}

function escapeHtml(text) {
  return String(text ?? "")
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
  showToast._timer = setTimeout(() => toast.classList.add("hidden"), 2800);
}

function showAuthError(message) {
  const el = document.getElementById("auth-error");
  if (!message) {
    el.textContent = "";
    el.classList.add("hidden");
  } else {
    el.textContent = message;
    el.classList.remove("hidden");
  }
}

function timestampToMillis(ts) {
  if (!ts) return 0;
  if (typeof ts.toMillis === "function") return ts.toMillis();
  if (typeof ts.seconds === "number") return ts.seconds * 1000;
  return 0;
}

function rebuildCatalog() {
  const videosByPerformer = new Map();
  for (const link of state.videoLinksRaw) {
    const arr = videosByPerformer.get(link.performerId) || [];
    arr.push(link);
    videosByPerformer.set(link.performerId, arr);
  }
  for (const [, arr] of videosByPerformer) {
    arr.sort((a, b) => b.addedAt - a.addedAt);
  }

  state.performers = state.performersRaw.map((p) => ({
    ...p,
    videos: videosByPerformer.get(p.id) || [],
  }));

  syncSelectionFromHash(false);
  render();
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
        return (
          b.videos.length - a.videos.length ||
          a.name.localeCompare(b.name, "de", { sensitivity: "base" })
        );
      case "videos_asc":
        return (
          a.videos.length - b.videos.length ||
          a.name.localeCompare(b.name, "de", { sensitivity: "base" })
        );
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

  const mediaMarkup = performer.imageUri
    ? `<img class="portrait-img" src="${escapeHtml(performer.imageUri)}" alt="${safeName}" loading="lazy" />`
    : `<div class="portrait-empty-frame"><div class="monogram-circle">${initials}</div></div>`;

  const leftBadge =
    !performer.imageUri && showEmptyRatioBadge
      ? `<span class="badge-pill">9:16</span>`
      : `<span></span>`;

  const rightBadge =
    videoCount > 0
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
  const authView = document.getElementById("auth-view");
  const catalogView = document.getElementById("catalog-view");
  const detailView = document.getElementById("detail-view");
  const topbarActions = document.getElementById("topbar-actions");
  const statsEl = document.getElementById("catalog-stats");

  if (!state.user) {
    topbarActions.classList.add("hidden");
    catalogView.classList.add("hidden");
    detailView.classList.add("hidden");
    authView.classList.remove("hidden");
    statsEl.textContent = "Nicht angemeldet • Anmeldung für Cloud-Sync erforderlich";
    return;
  }

  authView.classList.add("hidden");
  topbarActions.classList.remove("hidden");

  if (!state.hasLoadedPerformers) {
    statsEl.textContent = "Synchronisiere mit Cloud…";
  } else {
    const totalLinks = state.performers.reduce((sum, p) => sum + p.videos.length, 0);
    const visible = getFilteredPerformers();
    statsEl.textContent = `${visible.length} von ${state.performers.length} Darstellerinnen • ${totalLinks} Video-Links`;
  }

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
    renderCatalogGrid(getFilteredPerformers());
  }
}

function renderCatalogGrid(visible) {
  const grid = document.getElementById("performer-grid");
  const emptyState = document.getElementById("empty-state");

  if (!state.hasLoadedPerformers) {
    grid.innerHTML = "";
    emptyState.classList.add("hidden");
    return;
  }

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
  document.getElementById("detail-portrait-card").innerHTML = renderPortraitMarkup(
    performer,
    false
  );
  document.getElementById("detail-name").textContent = performer.name;

  const removeBtn = document.getElementById("btn-performer-remove-image");
  removeBtn.classList.toggle("hidden", !performer.imageUri);

  const count = performer.videos.length;
  document.getElementById("detail-pills").innerHTML = `
    <span class="badge-pill ${count > 0 ? "gold" : ""}">${count} ${
    count === 1 ? "Video" : "Videos"
  }</span>
    <span class="badge-pill ${performer.imageUri ? "gold" : ""}">${
    performer.imageUri ? "9:16-Bild aktiv" : "Leere 9:16-Kachel"
  }</span>
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
        ? `<img class="video-thumb-img" src="${escapeHtml(
            video.thumbnailUrl
          )}" alt="${escapeHtml(video.title)}" loading="lazy" />`
        : ``;
      return `
        <article class="video-card">
          <a class="video-link-anchor" href="${escapeHtml(
            video.url
          )}" target="_blank" rel="noopener noreferrer">
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
            <button class="secondary-btn" type="button" data-edit-video="${escapeHtml(
              video.id
            )}">Bearbeiten</button>
            <button class="danger-btn" type="button" data-delete-video="${escapeHtml(
              video.id
            )}">Löschen</button>
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

function syncSelectionFromHash(shouldRender = true) {
  const hash = window.location.hash;
  if (hash.startsWith("#performer/")) {
    const id = decodeURIComponent(hash.slice("#performer/".length));
    state.selectedPerformerId = state.performers.some((p) => p.id === id) ? id : null;
  } else {
    state.selectedPerformerId = null;
  }
  if (shouldRender) render();
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

  form.onsubmit = async (e) => {
    e.preventDefault();
    const data = {};
    for (const f of fields) {
      data[f.name] = form.elements[f.name].value;
    }
    try {
      await onSubmit(data);
      close();
    } catch (err) {
      showToast(err?.message || "Speichern fehlgeschlagen");
    }
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
        const w = Math.max(1, Math.round(img.width * ratio));
        const h = Math.max(1, Math.round(img.height * ratio));
        const canvas = document.createElement("canvas");
        canvas.width = w;
        canvas.height = h;
        canvas.getContext("2d").drawImage(img, 0, 0, w, h);

        for (const quality of [0.8, 0.65, 0.5, 0.35]) {
          const dataUrl = canvas.toDataURL("image/jpeg", quality);
          if (dataUrl.length <= 220_000) {
            resolve(dataUrl);
            return;
          }
        }
        reject(new Error("Bild ist selbst nach Komprimierung zu groß (> 220 KB)."));
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

function stopFirestoreListeners() {
  if (state.unsubPerformers) {
    state.unsubPerformers();
    state.unsubPerformers = null;
  }
  if (state.unsubLinks) {
    state.unsubLinks();
    state.unsubLinks = null;
  }
  state.performersRaw = [];
  state.videoLinksRaw = [];
  state.performers = [];
  state.hasLoadedPerformers = false;
  state.hasLoadedLinks = false;
  state.selectedPerformerId = null;
}

async function ensureCatalogSeeded(uid) {
  const metaRef = doc(db, "users", uid, "meta", "catalog");
  const metaSnap = await getDoc(metaRef);
  if (metaSnap.exists()) return;

  const existingSnap = await getDocs(
    query(collection(db, "users", uid, "performers"), limit(1))
  );

  const batch = writeBatch(db);
  if (existingSnap.empty) {
    INITIAL_PERFORMER_NAMES.forEach((name, index) => {
      const seedId = `seed_${String(index + 1).padStart(2, "0")}`;
      const performerRef = doc(db, "users", uid, "performers", seedId);
      batch.set(performerRef, {
        userId: uid,
        name: name.trim(),
        imageUri: null,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
    });
  }

  batch.set(metaRef, {
    userId: uid,
    seeded: true,
    createdAt: serverTimestamp(),
  });

  await batch.commit();
}

function startFirestoreSync(uid) {
  stopFirestoreListeners();

  ensureCatalogSeeded(uid).catch((err) => {
    console.error("Seeding error:", err);
    showToast("Cloud-Initialisierung fehlgeschlagen");
  });

  const performersCol = collection(db, "users", uid, "performers");
  const linksCol = collection(db, "users", uid, "video_links");

  state.unsubPerformers = onSnapshot(
    performersCol,
    (snapshot) => {
      state.hasLoadedPerformers = true;
      state.performersRaw = snapshot.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          name: String(data.name || ""),
          imageUri: data.imageUri || null,
          createdAt: timestampToMillis(data.createdAt),
        };
      });
      rebuildCatalog();
    },
    (error) => {
      console.error("Performers listener error:", error);
      showToast("Sync-Fehler beim Laden der Darstellerinnen");
    }
  );

  state.unsubLinks = onSnapshot(
    linksCol,
    (snapshot) => {
      state.hasLoadedLinks = true;
      state.videoLinksRaw = snapshot.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          performerId: String(data.performerId || ""),
          title: String(data.title || ""),
          url: String(data.url || ""),
          thumbnailUrl: data.thumbnailUrl || null,
          addedAt: timestampToMillis(data.createdAt),
        };
      });
      rebuildCatalog();
    },
    (error) => {
      console.error("Video links listener error:", error);
      showToast("Sync-Fehler beim Laden der Video-Links");
    }
  );
}

async function createPerformer(name) {
  const uid = state.user?.uid;
  if (!uid) throw new Error("Bitte zuerst anmelden");
  const trimmed = name.trim().slice(0, 120);
  if (!trimmed) throw new Error("Name darf nicht leer sein");

  const ref = await addDoc(collection(db, "users", uid, "performers"), {
    userId: uid,
    name: trimmed,
    imageUri: null,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  return ref.id;
}

async function updatePerformerImage(performerId, imageUri) {
  const uid = state.user?.uid;
  if (!uid) throw new Error("Bitte zuerst anmelden");
  const normalized = imageUri ? String(imageUri).trim() : null;
  if (normalized && normalized.length > 250_000) {
    throw new Error("Bild-URL / Bilddaten überschreiten das 250-KB-Limit.");
  }
  await updateDoc(doc(db, "users", uid, "performers", performerId), {
    imageUri: normalized || null,
    updatedAt: serverTimestamp(),
  });
}

async function renamePerformer(performerId, newName) {
  const uid = state.user?.uid;
  if (!uid) throw new Error("Bitte zuerst anmelden");
  const trimmed = newName.trim().slice(0, 120);
  if (!trimmed) throw new Error("Name darf nicht leer sein");
  await updateDoc(doc(db, "users", uid, "performers", performerId), {
    name: trimmed,
    updatedAt: serverTimestamp(),
  });
}

async function deletePerformerAndLinks(performerId) {
  const uid = state.user?.uid;
  if (!uid) throw new Error("Bitte zuerst anmelden");
  const childLinks = await getDocs(
    query(
      collection(db, "users", uid, "video_links"),
      where("performerId", "==", performerId)
    )
  );
  const batch = writeBatch(db);
  childLinks.docs.forEach((d) => batch.delete(d.ref));
  batch.delete(doc(db, "users", uid, "performers", performerId));
  await batch.commit();
}

async function createVideoLink(performerId, title, url, thumbnailUrl) {
  const uid = state.user?.uid;
  if (!uid) throw new Error("Bitte zuerst anmelden");
  const normalizedUrl = normalizeUrl(url).slice(0, 2000);
  if (!normalizedUrl) throw new Error("Video-URL darf nicht leer sein");
  const resolvedTitle = (title.trim() || deriveTitle(normalizedUrl)).slice(0, 200);
  const normalizedThumb = thumbnailUrl?.trim() ? normalizeUrl(thumbnailUrl) : null;
  if (normalizedThumb && normalizedThumb.length > 250_000) {
    throw new Error("Thumbnail überschreitet das 250-KB-Limit.");
  }

  await addDoc(collection(db, "users", uid, "video_links"), {
    userId: uid,
    performerId,
    title: resolvedTitle,
    url: normalizedUrl,
    thumbnailUrl: normalizedThumb,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

async function updateVideoLinkDoc(linkId, title, url, thumbnailUrl) {
  const uid = state.user?.uid;
  if (!uid) throw new Error("Bitte zuerst anmelden");
  const normalizedUrl = normalizeUrl(url).slice(0, 2000);
  if (!normalizedUrl) throw new Error("Video-URL darf nicht leer sein");
  const resolvedTitle = (title.trim() || deriveTitle(normalizedUrl)).slice(0, 200);
  const normalizedThumb = thumbnailUrl?.trim() ? normalizeUrl(thumbnailUrl) : null;
  if (normalizedThumb && normalizedThumb.length > 250_000) {
    throw new Error("Thumbnail überschreitet das 250-KB-Limit.");
  }

  await updateDoc(doc(db, "users", uid, "video_links", linkId), {
    title: resolvedTitle,
    url: normalizedUrl,
    thumbnailUrl: normalizedThumb,
    updatedAt: serverTimestamp(),
  });
}

async function deleteVideoLinkDoc(linkId) {
  const uid = state.user?.uid;
  if (!uid) throw new Error("Bitte zuerst anmelden");
  await deleteDoc(doc(db, "users", uid, "video_links", linkId));
}

function initEvents() {
  window.addEventListener("hashchange", () => syncSelectionFromHash(true));

  document.getElementById("btn-google-signin").addEventListener("click", async () => {
    showAuthError(null);
    const btn = document.getElementById("btn-google-signin");
    btn.disabled = true;
    btn.textContent = "Anmeldung läuft…";
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (error) {
      const code = error?.code || "";
      if (
        code === "auth/popup-blocked" ||
        code === "auth/operation-not-supported-in-this-browser"
      ) {
        await signInWithRedirect(auth, googleProvider);
        return;
      }
      if (code === "auth/unauthorized-domain") {
        showAuthError(
          `Die Domain "${window.location.hostname}" muss noch in der Firebase Console unter Authentication → Settings → Authorized domains freigegeben werden.`
        );
      } else if (code !== "auth/popup-closed-by-user") {
        showAuthError(error?.message || "Google-Anmeldung fehlgeschlagen.");
      }
    } finally {
      btn.disabled = false;
      btn.textContent = "Mit Google anmelden";
    }
  });

  document.getElementById("btn-sign-out").addEventListener("click", async () => {
    stopFirestoreListeners();
    await signOut(auth);
    showToast("Abgemeldet");
  });

  document.getElementById("search-input").addEventListener("input", (e) => {
    state.query = e.target.value;
    render();
  });

  document.querySelectorAll("[data-sort]").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.sort = btn.dataset.sort;
      document
        .querySelectorAll("[data-sort]")
        .forEach((b) => b.classList.toggle("active", b === btn));
      render();
    });
  });

  document.querySelectorAll("[data-bucket]").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.bucket = btn.dataset.bucket;
      document
        .querySelectorAll("[data-bucket]")
        .forEach((b) => b.classList.toggle("active", b === btn));
      render();
    });
  });

  document.getElementById("btn-reset-filters").addEventListener("click", () => {
    state.query = "";
    state.bucket = "ALL";
    state.sort = "alpha_asc";
    document.getElementById("search-input").value = "";
    document
      .querySelectorAll("[data-sort]")
      .forEach((b) => b.classList.toggle("active", b.dataset.sort === "alpha_asc"));
    document
      .querySelectorAll("[data-bucket]")
      .forEach((b) => b.classList.toggle("active", b.dataset.bucket === "ALL"));
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
      fields: [
        {
          name: "name",
          label: "Name",
          placeholder: "z. B. Vorname Nachname",
          required: true,
        },
      ],
      onSubmit: async ({ name }) => {
        await createPerformer(name);
        showToast(`${name.trim()} synchronisiert`);
      },
    });
  });

  document.getElementById("input-performer-file").addEventListener("change", async (e) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file || !state.selectedPerformerId) return;
    try {
      const dataUrl = await compressImageFile(file);
      await updatePerformerImage(state.selectedPerformerId, dataUrl);
      showToast("9:16-Bild gespeichert & synchronisiert");
    } catch (err) {
      showToast(err?.message || "Bild konnte nicht geladen werden");
    }
  });

  document.getElementById("btn-performer-image-url").addEventListener("click", () => {
    const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
    if (!performer) return;
    openModal({
      title: "Bild-URL festlegen",
      fields: [
        {
          name: "url",
          label: "Bild-URL",
          value: performer.imageUri || "",
          placeholder: "https://…",
          required: true,
        },
      ],
      onSubmit: async ({ url }) => {
        await updatePerformerImage(performer.id, normalizeUrl(url));
        showToast("Bild synchronisiert");
      },
    });
  });

  document
    .getElementById("btn-performer-remove-image")
    .addEventListener("click", async () => {
      if (!state.selectedPerformerId) return;
      try {
        await updatePerformerImage(state.selectedPerformerId, null);
        showToast("Bild entfernt");
      } catch (err) {
        showToast(err?.message || "Bild konnte nicht entfernt werden");
      }
    });

  document.getElementById("btn-rename-performer").addEventListener("click", () => {
    const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
    if (!performer) return;
    openModal({
      title: "Namen bearbeiten",
      fields: [{ name: "name", label: "Name", value: performer.name, required: true }],
      onSubmit: async ({ name }) => {
        await renamePerformer(performer.id, name);
        showToast("Name synchronisiert");
      },
    });
  });

  document
    .getElementById("btn-delete-performer")
    .addEventListener("click", async () => {
      const performer = state.performers.find((p) => p.id === state.selectedPerformerId);
      if (!performer) return;
      if (!window.confirm(`"${performer.name}" wirklich löschen?`)) return;
      try {
        await deletePerformerAndLinks(performer.id);
        showToast("Eintrag gelöscht");
        closeDetail();
      } catch (err) {
        showToast(err?.message || "Löschen fehlgeschlagen");
      }
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
      onSubmit: async ({ url, title, thumbnailUrl }) => {
        await createVideoLink(performer.id, title, url, thumbnailUrl);
        showToast("Video-Link synchronisiert");
      },
    });
  });

  document.getElementById("video-list").addEventListener("click", async (e) => {
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
          {
            name: "thumbnailUrl",
            label: "Thumbnail-URL (optional)",
            value: video.thumbnailUrl || "",
          },
        ],
        onSubmit: async ({ url, title, thumbnailUrl }) => {
          await updateVideoLinkDoc(video.id, title, url, thumbnailUrl);
          showToast("Video-Link aktualisiert");
        },
      });
      return;
    }

    const deleteBtn = e.target.closest("[data-delete-video]");
    if (deleteBtn) {
      try {
        await deleteVideoLinkDoc(deleteBtn.dataset.deleteVideo);
        showToast("Video-Link entfernt");
      } catch (err) {
        showToast(err?.message || "Video-Link konnte nicht entfernt werden");
      }
    }
  });

  document.getElementById("btn-export").addEventListener("click", () => {
    const blob = new Blob([JSON.stringify(state.performers, null, 2)], {
      type: "application/json",
    });
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
    const uid = state.user?.uid;
    if (!file || !uid) return;
    try {
      const text = await file.text();
      const parsed = JSON.parse(text);
      if (!Array.isArray(parsed)) throw new Error("Ungültiges Format");

      let batch = writeBatch(db);
      let opCount = 0;
      const commitIfNeeded = async () => {
        if (opCount >= 400) {
          await batch.commit();
          batch = writeBatch(db);
          opCount = 0;
        }
      };

      const existingByName = new Map(
        state.performers.map((p) => [p.name.trim().toLowerCase(), p.id])
      );

      for (const item of parsed) {
        const name = String(item.name || "").trim().slice(0, 120);
        if (!name) continue;

        let performerId = existingByName.get(name.toLowerCase());
        const imgRaw = item.imageUri || item.imageUrl || null;
        const safeImg =
          imgRaw && String(imgRaw).length <= 250_000 ? String(imgRaw).trim() : null;

        if (!performerId) {
          const perfRef = doc(collection(db, "users", uid, "performers"));
          performerId = perfRef.id;
          existingByName.set(name.toLowerCase(), performerId);
          batch.set(perfRef, {
            userId: uid,
            name,
            imageUri: safeImg,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp(),
          });
          opCount++;
          await commitIfNeeded();
        } else if (safeImg) {
          batch.update(doc(db, "users", uid, "performers", performerId), {
            imageUri: safeImg,
            updatedAt: serverTimestamp(),
          });
          opCount++;
          await commitIfNeeded();
        }

        if (Array.isArray(item.videos)) {
          for (const v of item.videos) {
            const url = normalizeUrl(v?.url || "").slice(0, 2000);
            if (!url) continue;
            const title = (String(v?.title || "").trim() || deriveTitle(url)).slice(0, 200);
            const thumb = v?.thumbnailUrl ? normalizeUrl(v.thumbnailUrl) : null;
            const safeThumb = thumb && thumb.length <= 250_000 ? thumb : null;

            const linkRef = doc(collection(db, "users", uid, "video_links"));
            batch.set(linkRef, {
              userId: uid,
              performerId,
              title,
              url,
              thumbnailUrl: safeThumb,
              createdAt: serverTimestamp(),
              updatedAt: serverTimestamp(),
            });
            opCount++;
            await commitIfNeeded();
          }
        }
      }

      if (opCount > 0) {
        await batch.commit();
      }
      showToast("Backup in Cloud importiert");
      closeDetail();
    } catch (err) {
      console.error(err);
      showToast("Ungültige Backup-Datei");
    }
  });
}

initEvents();

getRedirectResult(auth).catch((error) => {
  if (error?.code === "auth/unauthorized-domain") {
    showAuthError(
      `Die Domain "${window.location.hostname}" muss noch in der Firebase Console unter Authentication → Settings → Authorized domains freigegeben werden.`
    );
  } else if (error) {
    showAuthError(error.message || "Google-Anmeldung fehlgeschlagen.");
  }
});

onAuthStateChanged(auth, (user) => {
  state.user = user;
  if (user) {
    showAuthError(null);
    startFirestoreSync(user.uid);
  } else {
    stopFirestoreListeners();
  }
  render();
});
