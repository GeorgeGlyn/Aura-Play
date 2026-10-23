// =============================================================================
//  Aura Play – Download Engine
//  IndexedDB persistence, stream extraction, progress tracking, library UI
// =============================================================================

const DB_NAME    = "AuraPlayOffline";
const DB_VERSION = 1;
const STORE      = "downloads";

// --------------------------------------------------------------------------
// Low-level IndexedDB helpers
// --------------------------------------------------------------------------
const IDB = {
    _db: null,

    open() {
        return new Promise((resolve, reject) => {
            if (this._db) { resolve(this._db); return; }
            const req = indexedDB.open(DB_NAME, DB_VERSION);
            req.onupgradeneeded = e => {
                const db = e.target.result;
                if (!db.objectStoreNames.contains(STORE)) {
                    db.createObjectStore(STORE, { keyPath: "key" });
                }
            };
            req.onsuccess = e => { this._db = e.target.result; resolve(this._db); };
            req.onerror   = e => reject(e.target.error);
        });
    },

    async put(record) {
        const db   = await this.open();
        return new Promise((resolve, reject) => {
            const tx   = db.transaction(STORE, "readwrite");
            const store = tx.objectStore(STORE);
            store.put(record).onsuccess = () => resolve();
            tx.onerror = e => reject(e.target.error);
        });
    },

    async get(key) {
        const db = await this.open();
        return new Promise((resolve, reject) => {
            const req = db.transaction(STORE, "readonly").objectStore(STORE).get(key);
            req.onsuccess = e => resolve(e.target.result);
            req.onerror   = e => reject(e.target.error);
        });
    },

    async getAll() {
        const db = await this.open();
        return new Promise((resolve, reject) => {
            const req = db.transaction(STORE, "readonly").objectStore(STORE).getAll();
            req.onsuccess = e => resolve(e.target.result || []);
            req.onerror   = e => reject(e.target.error);
        });
    },

    async delete(key) {
        const db = await this.open();
        return new Promise((resolve, reject) => {
            const tx    = db.transaction(STORE, "readwrite");
            const req   = tx.objectStore(STORE).delete(key);
            req.onsuccess = () => resolve();
            req.onerror   = e => reject(e.target.error);
        });
    }
};

// --------------------------------------------------------------------------
// Stream URL Extraction
// We try public CORS-friendly endpoints to get a direct playable URL.
// Embed sources (vidlink, vidsrc) generate direct m3u8/mp4 links in their
// page source which we attempt to scrape through a CORS proxy.
// --------------------------------------------------------------------------
const StreamExtractor = {
    // Trusted free CORS proxies (try in order)
    PROXIES: [
        "https://corsproxy.io/?",
        "https://api.allorigins.win/raw?url=",
        "https://cors-anywhere.herokuapp.com/"
    ],

    // Vidsrc.me provides a clean API endpoint that returns a direct stream URL
    VIDSRC_API_PROVIDERS: [
        id => `https://vidsrc.me/embed/movie?tmdb=${id}`,
        (id, s, e) => `https://vidsrc.me/embed/tv?tmdb=${id}&season=${s}&episode=${e}`,
    ],

    async fetchViaProxy(url) {
        for (const proxy of this.PROXIES) {
            try {
                const res = await fetch(proxy + encodeURIComponent(url), {
                    headers: { "X-Requested-With": "XMLHttpRequest" },
                    signal: AbortSignal.timeout(8000)
                });
                if (res.ok) return await res.text();
            } catch (_) { /* try next */ }
        }
        return null;
    },

    // Try to pull a direct m3u8 / mp4 URL from embed HTML
    extractFromHtml(html) {
        if (!html) return null;
        const patterns = [
            /file\s*:\s*["']([^"']+\.m3u8[^"']*?)["']/i,
            /source\s*:\s*["']([^"']+\.m3u8[^"']*?)["']/i,
            /"(https?:\/\/[^"]+\.m3u8[^"]*)"/i,
            /file\s*:\s*["']([^"']+\.mp4[^"']*?)["']/i,
            /"(https?:\/\/[^"]+\.mp4[^"]*)"/i,
        ];
        for (const p of patterns) {
            const m = html.match(p);
            if (m && m[1]) return m[1];
        }
        return null;
    },

    async getDirectUrl(media, season = null, episode = null) {
        const { id, media_type } = media;
        const isTv = media_type === "tv" || !!season;
        const s = season || 1;
        const e = episode || 1;

        const embedUrls = isTv ? [
            `https://vidlink.pro/tv/${id}/${s}/${e}?primaryColor=E50914`,
            `https://vidsrc.cc/v2/embed/tv/${id}/${s}/${e}`,
            `https://2embed.cc/embed/tv/${id}?s=${s}&e=${e}`,
        ] : [
            `https://vidlink.pro/movie/${id}?primaryColor=E50914`,
            `https://vidsrc.cc/v2/embed/movie/${id}`,
            `https://2embed.cc/embed/${id}`,
        ];

        for (const embedUrl of embedUrls) {
            const html = await this.fetchViaProxy(embedUrl);
            const directUrl = this.extractFromHtml(html);
            if (directUrl) {
                console.log(`[Extractor] Found direct URL via ${embedUrl}: ${directUrl}`);
                return { url: directUrl, source: embedUrl };
            }
        }

        return null;
    }
};

// --------------------------------------------------------------------------
// Download Manager
// --------------------------------------------------------------------------
const Downloader = {
    activeDownloads: {}, // key -> { controller, progressEl }

    downloadKey(media, season, episode) {
        const isTv = media.media_type === "tv";
        return isTv && season
            ? `tv_${media.id}_s${season}_e${episode}`
            : `movie_${media.id}`;
    },

    async getLibrary() {
        return await IDB.getAll();
    },

    async getDownload(key) {
        return await IDB.get(key);
    },

    async isDownloaded(key) {
        const rec = await IDB.get(key);
        return rec && rec.status === "completed";
    },

    // -----------------------------------------------------------------------
    // Begin a download for a given quality
    // quality: { label: "1080p", url: <string> } or null → auto-extract
    // -----------------------------------------------------------------------
    async startDownload(media, season = null, episode = null, qualityLabel = "Best") {
        const key   = this.downloadKey(media, season, episode);
        const title = media.title || media.name;
        const isTv  = (media.media_type === "tv") && !!season;
        const label = isTv ? `${title} S${String(season).padStart(2,"0")}E${String(episode).padStart(2,"0")}` : title;

        // Guard: already downloading or downloaded
        const existing = await IDB.get(key);
        if (existing && existing.status === "completed") {
            DownloadUI.showToast(`"${label}" is already downloaded.`, "info");
            return;
        }
        if (this.activeDownloads[key]) {
            DownloadUI.showToast(`"${label}" download already in progress.`, "info");
            return;
        }

        // Seed metadata record immediately so library shows it
        await IDB.put({
            key, title: label, poster: media.poster_path, media,
            season, episode, qualityLabel,
            status: "extracting", progress: 0,
            addedAt: Date.now(), blob: null, mimeType: null, objectUrl: null
        });

        DownloadUI.renderLibrary();
        DownloadUI.showToast(`⬇ Extracting stream for "${label}"...`, "info");

        // Extract direct URL
        const extracted = await StreamExtractor.getDirectUrl(media, season, episode);
        if (!extracted) {
            await IDB.put({ ...(await IDB.get(key)), status: "failed", error: "Could not extract direct stream URL" });
            DownloadUI.renderLibrary();
            DownloadUI.showToast(`❌ Could not find downloadable stream for "${label}". Try another server.`, "error");
            return;
        }

        const { url: streamUrl } = extracted;
        const controller = new AbortController();
        this.activeDownloads[key] = { controller };

        // Update status to downloading
        await IDB.put({ ...(await IDB.get(key)), status: "downloading", streamUrl });
        DownloadUI.renderLibrary();
        DownloadUI.showToast(`⬇ Downloading "${label}" (${qualityLabel})...`, "success");

        try {
            const resp = await fetch(streamUrl, {
                signal: controller.signal,
                headers: { "User-Agent": "Mozilla/5.0" }
            });

            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);

            const contentLength = parseInt(resp.headers.get("content-length") || "0", 10);
            const mimeType      = resp.headers.get("content-type") || "video/mp4";
            const reader        = resp.body.getReader();
            const chunks        = [];
            let received        = 0;

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                chunks.push(value);
                received += value.byteLength;

                const pct = contentLength > 0 ? Math.round((received / contentLength) * 100) : -1;

                // Update progress in IDB (throttled to every 2%)
                if (pct === -1 || pct % 2 === 0) {
                    const rec = await IDB.get(key);
                    if (rec) {
                        await IDB.put({ ...rec, progress: pct >= 0 ? pct : received, receivedBytes: received });
                    }
                }
                DownloadUI.updateProgressBar(key, pct, received, contentLength);
            }

            // Assemble blob
            const blob      = new Blob(chunks, { type: mimeType });
            const objectUrl = URL.createObjectURL(blob);

            await IDB.put({
                ...(await IDB.get(key)),
                status: "completed",
                progress: 100,
                blob,
                mimeType,
                objectUrl,
                sizeBytes: blob.size,
                completedAt: Date.now()
            });

            delete this.activeDownloads[key];
            DownloadUI.renderLibrary();
            DownloadUI.showToast(`✅ "${label}" downloaded! Ready to watch offline.`, "success");

        } catch (err) {
            if (err.name === "AbortError") {
                await IDB.put({ ...(await IDB.get(key)), status: "cancelled" });
                DownloadUI.showToast(`Download cancelled: "${label}"`, "info");
            } else {
                await IDB.put({ ...(await IDB.get(key)), status: "failed", error: err.message });
                DownloadUI.showToast(`❌ Download failed: ${err.message}`, "error");
            }
            delete this.activeDownloads[key];
            DownloadUI.renderLibrary();
        }
    },

    async cancelDownload(key) {
        if (this.activeDownloads[key]) {
            this.activeDownloads[key].controller.abort();
        }
    },

    async deleteDownload(key) {
        await this.cancelDownload(key);
        const rec = await IDB.get(key);
        if (rec && rec.objectUrl) URL.revokeObjectURL(rec.objectUrl);
        await IDB.delete(key);
        DownloadUI.renderLibrary();
        DownloadUI.showToast("Download removed from library.", "info");
    },

    // Re-create objectURL after page reload (blobs don't survive across sessions
    // unless stored as ArrayBuffer; for a web app we store the blob reference in memory)
    async playOffline(key) {
        const rec = await IDB.get(key);
        if (!rec || rec.status !== "completed") {
            DownloadUI.showToast("Download not ready yet.", "error");
            return;
        }

        let src = rec.objectUrl;
        // Recreate objectURL if not valid (e.g. page refresh)
        if (!src && rec.blob) {
            src = URL.createObjectURL(rec.blob);
            await IDB.put({ ...rec, objectUrl: src });
        }

        if (!src) {
            DownloadUI.showToast("Offline video blob not available. Please re-download.", "error");
            return;
        }

        NativePlayer.playOffline(src, rec.title, rec.media, rec.season, rec.episode, key);
    }
};

// --------------------------------------------------------------------------
// Quality Picker – shown before starting a download
// --------------------------------------------------------------------------
const QualityPicker = {
    show(media, season = null, episode = null) {
        const title   = media.title || media.name;
        const isTv    = (media.media_type === "tv") && !!season;
        const label   = isTv ? `${title} S${season}E${episode}` : title;
        const qualities = ["Best Available", "1080p FHD", "720p HD", "480p SD"];

        const existing = document.getElementById("quality-picker-overlay");
        if (existing) existing.remove();

        const overlay = document.createElement("div");
        overlay.id    = "quality-picker-overlay";
        overlay.innerHTML = `
            <div class="quality-picker-card">
                <div class="quality-picker-header">
                    <h3 class="quality-picker-title">Download Quality</h3>
                    <p class="quality-picker-subtitle">${label}</p>
                </div>
                <p class="quality-note">⚡ Stream source will be auto-detected and downloaded at the best available resolution.</p>
                <div class="quality-options">
                    ${qualities.map((q, i) => `
                        <button class="quality-btn focusable ${i === 0 ? "recommended" : ""}" 
                                tabindex="0" 
                                data-quality="${q}">
                            ${i === 0 ? "⭐ " : ""}${q}
                            ${i === 0 ? '<span class="rec-badge">Recommended</span>' : ""}
                        </button>
                    `).join("")}
                </div>
                <button class="quality-cancel-btn focusable" tabindex="0" id="quality-cancel-btn">Cancel</button>
            </div>
        `;

        overlay.addEventListener("click", e => {
            const btn = e.target.closest(".quality-btn");
            if (btn) {
                const q = btn.dataset.quality;
                overlay.remove();
                Downloader.startDownload(media, season, episode, q);
            }
            if (e.target === overlay || e.target.id === "quality-cancel-btn") {
                overlay.remove();
            }
        });

        document.body.appendChild(overlay);

        // Focus first quality button for TV remote
        const firstBtn = overlay.querySelector(".quality-btn");
        if (firstBtn && window.Navigation) {
            setTimeout(() => window.Navigation.setFocus(firstBtn), 50);
        }
    }
};

// --------------------------------------------------------------------------
// --------------------------------------------------------------------------
// Native HTML5 Video Player (for offline playback on Samsung Tizen & Web)
// --------------------------------------------------------------------------
const NativePlayer = {
    isOpen: false,
    isHudVisible: true,
    hudTimer: null,
    indicatorTimer: null,
    video: null,
    isCoverFit: false,
    currentKey: null,
    currentMedia: null,
    currentSeason: null,
    currentEpisode: null,
    currentTitle: null,
    lastSavedPos: 0,
    hasResumed: false,

    playOffline(src, title, media = null, season = null, episode = null, key = null) {
        this.isOpen = true;
        this.isHudVisible = true;
        this.isCoverFit = false;
        this.currentKey = key;
        this.currentMedia = media;
        this.currentSeason = season;
        this.currentEpisode = episode;
        this.currentTitle = title;
        this.lastSavedPos = 0;
        this.hasResumed = false;

        let container = document.getElementById("native-player-container");
        if (!container) {
            container = document.createElement("div");
            container.id = "native-player-container";
            document.body.appendChild(container);
        }

        const subTitle = season ? `Season ${season} • Episode ${episode} • Offline Download` : "Downloaded • 1080p HD • Offline";
        container.innerHTML = `
            <div class="native-player-overlay">
                <!-- Top Header HUD -->
                <div class="native-player-hud" id="native-hud-top">
                    <button class="player-back-btn focusable" id="native-back-btn" tabindex="0" onclick="NativePlayer.close()">
                        ← Back
                    </button>
                    <div class="player-titles">
                        <div class="player-title">${title}</div>
                        <div class="player-subtitle">${subTitle}</div>
                    </div>
                    <div class="player-top-actions">
                        <button class="player-action-btn focusable" id="native-fill-btn" tabindex="0" onclick="NativePlayer.toggleFillScreen()">
                            Fit (16:9)
                        </button>
                    </div>
                </div>

                <!-- Center Remote Controls (Netflix 10s Rewind | Play/Pause | 10s Forward) -->
                <div class="native-center-controls" id="native-center-controls">
                    <button class="native-ctrl-btn native-rewind-btn focusable" id="native-rewind-btn" tabindex="0" title="Rewind 10s" onclick="NativePlayer.seek(-10)">
                        <span class="ctrl-icon">↺</span>
                        <span class="ctrl-sec">10</span>
                    </button>
                    <button class="native-ctrl-btn native-play-btn focusable" id="native-play-btn" tabindex="0" title="Play/Pause" onclick="NativePlayer.togglePlayPause()">
                        <span class="ctrl-icon" id="native-play-icon">⏸</span>
                    </button>
                    <button class="native-ctrl-btn native-forward-btn focusable" id="native-forward-btn" tabindex="0" title="Forward 10s" onclick="NativePlayer.seek(10)">
                        <span class="ctrl-icon">↻</span>
                        <span class="ctrl-sec">10</span>
                    </button>
                </div>

                <!-- Bottom Scrubber & Time Bar -->
                <div class="native-player-bottom-hud" id="native-hud-bottom">
                    <div class="native-time-display" id="native-current-time">0:00</div>
                    <div class="native-scrubber-track focusable" id="native-scrubber" tabindex="0">
                        <div class="native-scrubber-buffered" id="native-scrubber-buffered" style="width: 0%"></div>
                        <div class="native-scrubber-fill" id="native-scrubber-fill" style="width: 0%">
                            <div class="native-scrubber-thumb"></div>
                        </div>
                    </div>
                    <div class="native-time-display" id="native-total-time">--:--</div>
                </div>

                <!-- Gesture / Action Floating Bubble HUD -->
                <div class="native-action-bubble hidden" id="native-action-bubble"></div>

                <!-- HTML5 Video without buggy native browser controls -->
                <video id="native-video" 
                       class="native-video"
                       src="${src}"
                       autoplay 
                       playsinline
                       preload="auto">
                    Your browser does not support HTML5 video.
                </video>

                <!-- Loading Spinner -->
                <div class="native-loading-overlay" id="native-loading">
                    <div class="spinner"></div>
                    <p>Starting offline playback...</p>
                </div>
            </div>
        `;

        container.classList.remove("hidden");
        document.body.style.overflow = "hidden";

        this.video = document.getElementById("native-video");
        const loadingEl = document.getElementById("native-loading");
        const playIcon = document.getElementById("native-play-icon");
        const currentTimeEl = document.getElementById("native-current-time");
        const totalTimeEl = document.getElementById("native-total-time");
        const scrubberFill = document.getElementById("native-scrubber-fill");
        const scrubberBuffered = document.getElementById("native-scrubber-buffered");

        const tryResumePosition = async () => {
            if (this.hasResumed || !this.currentKey || !this.video) return;
            this.hasResumed = true;
            try {
                const rec = await IDB.get(this.currentKey);
                if (rec && rec.lastPosition && rec.lastPosition > 3) {
                    const dur = this.video.duration || rec.duration || 0;
                    if (!dur || rec.lastPosition < (dur - 15)) {
                        this.video.currentTime = rec.lastPosition;
                        this.showActionBubble(`Resumed from ${this.formatTime(rec.lastPosition)}`);
                    }
                }
            } catch (err) {
                console.warn("[NativePlayer] Resume position check failed:", err);
            }
        };

        this.video.addEventListener("loadedmetadata", tryResumePosition);
        this.video.addEventListener("canplay", () => {
            loadingEl.classList.add("hidden");
            tryResumePosition();
        });

        this.video.addEventListener("waiting", () => {
            loadingEl.classList.remove("hidden");
        });

        this.video.addEventListener("playing", () => {
            loadingEl.classList.add("hidden");
            if (playIcon) playIcon.textContent = "⏸";
        });

        this.video.addEventListener("pause", () => {
            if (playIcon) playIcon.textContent = "▶";
            this.savePlaybackProgress();
        });

        this.video.addEventListener("ended", () => {
            if (playIcon) playIcon.textContent = "▶";
            this.resetPlaybackProgress();
        });

        this.video.addEventListener("timeupdate", () => {
            if (!this.video) return;
            const cur = this.video.currentTime || 0;
            const dur = this.video.duration || 0;
            if (currentTimeEl) currentTimeEl.textContent = this.formatTime(cur);
            if (totalTimeEl && dur > 0) totalTimeEl.textContent = this.formatTime(dur);
            if (scrubberFill && dur > 0) {
                const pct = Math.min(100, Math.max(0, (cur / dur) * 100));
                scrubberFill.style.width = pct + "%";
            }
            if (scrubberBuffered && this.video.buffered && this.video.buffered.length > 0 && dur > 0) {
                const bufEnd = this.video.buffered.end(this.video.buffered.length - 1);
                const bufPct = Math.min(100, Math.max(0, (bufEnd / dur) * 100));
                scrubberBuffered.style.width = bufPct + "%";
            }

            // Throttled persistence every 2 seconds
            if (Math.abs(cur - this.lastSavedPos) >= 2) {
                this.savePlaybackProgress();
            }
        });

        this.video.addEventListener("error", () => {
            loadingEl.classList.remove("hidden");
            loadingEl.innerHTML = `
                <p style="color:#ff4a4a;font-size:18px;">⚠ Could not play offline video.<br>
                The video file may have expired or is corrupted. Please re-download.</p>
            `;
        });

        // Click on video toggles HUD
        this.video.addEventListener("click", () => {
            this.toggleHud();
        });

        // Click / Mouse movement resets HUD timer
        container.addEventListener("mousemove", () => this.showHud());
        container.addEventListener("touchstart", () => this.showHud());

        // Initial HUD display & focus on center Play/Pause button for immediate TV remote usability
        this.showHud();
        const centerPlayBtn = document.getElementById("native-play-btn");
        if (centerPlayBtn && window.Navigation) {
            setTimeout(() => window.Navigation.setFocus(centerPlayBtn), 100);
        }

        if (window.TizenBridge) window.TizenBridge.preventScreenSaver(true);
    },

    async savePlaybackProgress() {
        if (!this.isOpen || !this.video || !this.currentKey) return;
        const cur = this.video.currentTime || 0;
        const dur = this.video.duration || 0;
        if (cur <= 0) return;
        this.lastSavedPos = cur;

        const isNearEnd = dur > 0 && cur >= (dur - 15);
        const savedPos = isNearEnd ? 0 : Math.floor(cur);

        try {
            const rec = await IDB.get(this.currentKey);
            if (rec) {
                rec.lastPosition = savedPos;
                if (dur > 0) rec.duration = Math.floor(dur);
                await IDB.put(rec);
            }

            // Sync with HomeScreen Continue Watching row
            if (window.App && typeof window.App.updateContinueWatching === "function" && rec) {
                const mediaObj = rec.media || {
                    id: rec.key,
                    title: rec.title,
                    poster_path: rec.poster,
                    media_type: rec.season ? "tv" : "movie"
                };
                window.App.updateContinueWatching({
                    id: mediaObj.id || rec.key,
                    title: rec.title,
                    poster_path: rec.poster || (mediaObj && mediaObj.poster_path),
                    backdrop_path: mediaObj && (mediaObj.backdrop_path || mediaObj.poster_path),
                    media_type: mediaObj.media_type || (rec.season ? "tv" : "movie"),
                    season: rec.season,
                    episode: rec.episode,
                    position: savedPos,
                    duration: dur > 0 ? Math.floor(dur) : (rec.duration || 5400),
                    isOffline: true,
                    downloadKey: rec.key
                });
            }
        } catch (e) {
            console.warn("[NativePlayer] Error saving offline progress:", e);
        }
    },

    async resetPlaybackProgress() {
        if (!this.currentKey) return;
        try {
            const rec = await IDB.get(this.currentKey);
            if (rec) {
                rec.lastPosition = 0;
                await IDB.put(rec);
            }
            if (window.App && typeof window.App.removeContinueWatching === "function") {
                const id = (rec && rec.media && rec.media.id) || this.currentKey;
                window.App.removeContinueWatching(id, this.currentSeason, this.currentEpisode);
            }
        } catch (e) {}
    },

    formatTime(sec) {
        if (!sec || isNaN(sec)) return "0:00";
        const total = Math.floor(sec);
        const h = Math.floor(total / 3600);
        const m = Math.floor((total % 3600) / 60);
        const s = total % 60;
        const padS = s < 10 ? "0" + s : s;
        if (h > 0) {
            const padM = m < 10 ? "0" + m : m;
            return `${h}:${padM}:${padS}`;
        }
        return `${m}:${padS}`;
    },

    showHud() {
        if (!this.isOpen) return;
        this.isHudVisible = true;
        const top = document.getElementById("native-hud-top");
        const center = document.getElementById("native-center-controls");
        const bottom = document.getElementById("native-hud-bottom");
        if (top) { top.classList.remove("hud-hidden"); top.classList.add("hud-visible"); }
        if (center) { center.classList.remove("hud-hidden"); center.classList.add("hud-visible"); }
        if (bottom) { bottom.classList.remove("hud-hidden"); bottom.classList.add("hud-visible"); }

        clearTimeout(this.hudTimer);
        this.hudTimer = setTimeout(() => {
            this.hideHud();
        }, 6000);
    },

    hideHud() {
        if (!this.isOpen) return;
        this.isHudVisible = false;
        clearTimeout(this.hudTimer);
        const top = document.getElementById("native-hud-top");
        const center = document.getElementById("native-center-controls");
        const bottom = document.getElementById("native-hud-bottom");
        if (top) { top.classList.remove("hud-visible"); top.classList.add("hud-hidden"); }
        if (center) { center.classList.remove("hud-visible"); center.classList.add("hud-hidden"); }
        if (bottom) { bottom.classList.remove("hud-visible"); bottom.classList.add("hud-hidden"); }
    },

    toggleHud() {
        if (this.isHudVisible) {
            this.hideHud();
        } else {
            this.showHud();
            const playBtn = document.getElementById("native-play-btn");
            if (playBtn && window.Navigation) {
                window.Navigation.setFocus(playBtn);
            }
        }
    },

    togglePlayPause() {
        if (!this.video) return;
        if (this.video.paused) {
            this.video.play().catch(() => {});
            this.showActionBubble("▶ Playing");
        } else {
            this.video.pause();
            this.showActionBubble("⏸ Paused");
        }
        this.showHud();
    },

    seek(seconds) {
        if (!this.video) return;
        const target = Math.max(0, Math.min(this.video.duration || Infinity, this.video.currentTime + seconds));
        this.video.currentTime = target;
        this.showActionBubble(seconds > 0 ? `+${seconds}s Fast Forward` : `${seconds}s Rewind`);
        this.showHud();
    },

    toggleFillScreen() {
        if (!this.video) return;
        this.isCoverFit = !this.isCoverFit;
        this.video.style.objectFit = this.isCoverFit ? "cover" : "contain";
        const btn = document.getElementById("native-fill-btn");
        if (btn) btn.textContent = this.isCoverFit ? "Fill (Zoom)" : "Fit (16:9)";
        this.showActionBubble(this.isCoverFit ? "Fill Screen (Zoom)" : "Original Aspect (Fit)");
        this.showHud();
    },

    showActionBubble(text) {
        const bubble = document.getElementById("native-action-bubble");
        if (!bubble) return;
        bubble.textContent = text;
        bubble.classList.remove("hidden");
        bubble.classList.add("bubble-visible");

        clearTimeout(this.indicatorTimer);
        this.indicatorTimer = setTimeout(() => {
            bubble.classList.remove("bubble-visible");
            bubble.classList.add("hidden");
        }, 1300);
    },

    close() {
        this.savePlaybackProgress();
        this.isOpen = false;
        this.isHudVisible = false;
        clearTimeout(this.hudTimer);
        clearTimeout(this.indicatorTimer);
        const container = document.getElementById("native-player-container");
        if (container) {
            const video = container.querySelector("video");
            if (video) { video.pause(); video.src = ""; }
            container.remove();
        }
        document.body.style.overflow = "";
        if (window.TizenBridge) window.TizenBridge.preventScreenSaver(false);

        // Re-render downloads library to refresh watched progress bars
        if (window.DownloadUI) {
            window.DownloadUI.renderLibrary();
        }

        // Restore focus to downloads grid
        setTimeout(() => {
            const firstPlay = document.querySelector(".dl-btn-play");
            if (firstPlay && window.Navigation) {
                window.Navigation.setFocus(firstPlay);
            } else if (window.Navigation) {
                window.Navigation.moveFocus(40);
            }
        }, 100);
    }
};

// --------------------------------------------------------------------------
// Download UI – Library Tab rendering, toast notifications, progress bars
// --------------------------------------------------------------------------
const DownloadUI = {
    async renderLibrary() {
        const grid = document.getElementById("downloads-grid");
        if (!grid) return;

        const library = await Downloader.getLibrary();
        if (library.length === 0) {
            grid.innerHTML = `
                <div class="downloads-empty">
                    <div class="downloads-empty-icon">⬇</div>
                    <h3>No Downloads Yet</h3>
                    <p>Open any movie or TV show and tap<br><strong>Download</strong> to save for offline viewing.</p>
                </div>`;
            return;
        }

        grid.innerHTML = library.map(item => {
            const posterUrl = item.poster
                ? `https://image.tmdb.org/t/p/w500${item.poster}`
                : "assets/icon.png";
            const statusIcon = {
                extracting:  "🔍",
                downloading: "⬇",
                completed:   "✅",
                failed:      "❌",
                cancelled:   "⏹",
            }[item.status] || "⬇";

            const sizeStr = item.sizeBytes
                ? `${(item.sizeBytes / (1024 * 1024)).toFixed(0)} MB`
                : "";

            const isReady      = item.status === "completed";
            const isActive     = ["extracting","downloading"].includes(item.status);
            const progressPct  = isActive ? (item.progress > 0 ? item.progress : 0) : (isReady ? 100 : 0);
            const isResumable  = isReady && item.lastPosition && item.lastPosition > 3 && item.duration > 0;
            const watchedPct   = isResumable ? Math.min(100, Math.round((item.lastPosition / item.duration) * 100)) : 0;

            return `
                <div class="download-card" data-key="${item.key}">
                    <div class="download-thumb-wrap">
                        <img class="download-thumb" src="${posterUrl}" alt="${item.title}">
                        <div class="download-status-badge">${statusIcon}</div>
                    </div>
                    <div class="download-info">
                        <div class="download-title">${item.title}</div>
                        <div class="download-meta">
                            ${item.qualityLabel || ""}
                            ${sizeStr ? " • " + sizeStr : ""}
                        </div>
                        ${isActive ? `
                            <div class="download-progress-track">
                                <div class="download-progress-fill" 
                                     id="prog-${item.key}"
                                     style="width:${progressPct}%"></div>
                            </div>
                            <div class="download-progress-label" id="prog-label-${item.key}">
                                ${progressPct >= 0 ? progressPct + "%" : "Calculating..."}
                            </div>
                        ` : ""}
                        ${isResumable ? `
                            <div class="download-progress-track watched-track" style="margin-top:6px;" title="Watched ${watchedPct}%">
                                <div class="download-progress-fill watched-fill" style="width:${watchedPct}%; background:linear-gradient(90deg, #e50914, #ff3b30);"></div>
                            </div>
                            <div class="download-resume-label" style="font-size:11px; color:#ff4a4a; font-weight:600; margin-top:3px; display:flex; justify-content:space-between;">
                                <span>⏱ Resume at ${NativePlayer.formatTime(item.lastPosition)}</span>
                                <span style="opacity:0.8;">${watchedPct}% watched</span>
                            </div>
                        ` : ""}
                        <div class="download-actions">
                            ${isReady ? `
                                <button class="dl-btn dl-btn-play focusable" tabindex="0"
                                        onclick="Downloader.playOffline('${item.key}')">
                                    ${isResumable ? `▶ Resume (${NativePlayer.formatTime(item.lastPosition)})` : `▶ Watch Offline`}
                                </button>
                            ` : ""}
                            ${isActive ? `
                                <button class="dl-btn dl-btn-cancel focusable" tabindex="0"
                                        onclick="Downloader.cancelDownload('${item.key}')">
                                    ⏹ Cancel
                                </button>
                            ` : ""}
                            <button class="dl-btn dl-btn-delete focusable" tabindex="0"
                                    onclick="DownloadUI.confirmDelete('${item.key}', '${item.title.replace(/'/g, "\\'")}')">
                                🗑 Remove
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }).join("");
    },

    updateProgressBar(key, pct, received, total) {
        const fill  = document.getElementById(`prog-${key}`);
        const label = document.getElementById(`prog-label-${key}`);
        if (!fill || !label) return;

        if (pct >= 0) {
            fill.style.width  = `${pct}%`;
            label.textContent = `${pct}%  (${(received/(1024*1024)).toFixed(1)} MB${total > 0 ? " / " + (total/(1024*1024)).toFixed(1) + " MB" : ""})`;
        } else {
            fill.style.width  = "100%";
            fill.style.animation = "progress-pulse 1s infinite";
            label.textContent = `${(received/(1024*1024)).toFixed(1)} MB downloaded…`;
        }
    },

    confirmDelete(key, title) {
        if (window.confirm(`Remove "${title}" from your downloads?`)) {
            Downloader.deleteDownload(key);
        }
    },

    // Global toast notification
    showToast(message, type = "info") {
        const existing = document.getElementById("dl-toast");
        if (existing) existing.remove();

        const toast = document.createElement("div");
        toast.id    = "dl-toast";
        toast.className = `dl-toast dl-toast-${type}`;
        toast.textContent = message;
        document.body.appendChild(toast);

        // Animate in
        requestAnimationFrame(() => toast.classList.add("dl-toast-show"));

        setTimeout(() => {
            toast.classList.remove("dl-toast-show");
            setTimeout(() => toast.remove(), 400);
        }, 3500);
    }
};

// Expose globally
window.Downloader    = Downloader;
window.QualityPicker = QualityPicker;
window.NativePlayer  = NativePlayer;
window.DownloadUI    = DownloadUI;
window.IDB           = IDB;
