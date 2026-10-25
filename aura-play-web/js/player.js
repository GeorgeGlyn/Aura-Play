// Aura Play - Cinema Streaming Player
const Player = {
    isOpen: false,
    isPaused: false,
    currentMedia: null,
    currentServer: "vidsrc_pm",
    hideTimeout: null,
    toastTimeout: null,
    initialStartTime: 0,
    playStartTime: 0,
    progressInterval: null,
    currentTime: 0,
    duration: 0,
    lastSavedPos: 0,

    init() {
        this.container = document.getElementById("player-container");
        this.frame = document.getElementById("player-frame");
        this.titleEl = document.getElementById("player-title");
        this.subtitleEl = document.getElementById("player-subtitle");
        this.hud = document.getElementById("player-hud");
        this.toastEl = document.getElementById("player-toast");
        this.toastTextEl = document.getElementById("player-toast-text");

        // Activity monitor to auto-hide HUD
        window.addEventListener("mousemove", () => {
            if (this.isOpen) {
                this.showHudTemporarily();
            }
        });

        // Listen for postMessage from embed stream players (vidsrc.pm, vidlink, etc.)
        window.addEventListener("message", (ev) => {
            if (!this.isOpen || !ev.data) return;
            try {
                const data = typeof ev.data === "string" ? JSON.parse(ev.data) : ev.data;
                if (!data) return;

                // Handle vidsrc.pm & vidlink stream events
                if (data.event === "timeupdate" || typeof data.currentTime === "number" || typeof data.time === "number") {
                    const time = (typeof data.currentTime === "number") ? data.currentTime : data.time;
                    if (typeof time === "number" && time >= 0) {
                        this.currentTime = Math.floor(time);
                        this.initialStartTime = this.currentTime;
                        this.playStartTime = Date.now();
                    }
                    if (typeof data.duration === "number" && data.duration > 0) {
                        this.duration = Math.floor(data.duration);
                    }
                    this.recordProgress();
                }

                if (data.event === "play") {
                    this.isPaused = false;
                } else if (data.event === "pause") {
                    this.isPaused = true;
                } else if (data.event === "seeked" && typeof data.currentTime === "number") {
                    this.currentTime = Math.floor(data.currentTime);
                }
            } catch (e) {}
        });
    },

    play(media, season = null, episode = null, startTime = null) {
        this.currentMedia = Object.assign({}, media, { season: season, episode: episode });
        this.isOpen = true;
        this.isPaused = false;

        // Check if there is an existing continue watching progress
        const saved = (window.App && typeof window.App.getContinueWatchingItem === "function")
            ? window.App.getContinueWatchingItem(media.id, season, episode)
            : null;

        this.initialStartTime = (typeof startTime === "number") ? startTime : (saved ? (saved.position || 0) : 0);
        this.currentTime = this.initialStartTime;
        this.playStartTime = Date.now();
        this.lastSavedPos = this.initialStartTime;
        this.duration = (media && media.runtime) ? (media.runtime * 60) : 5400;

        if (window.TizenBridge) {
            window.TizenBridge.preventScreenSaver(true);
        }

        // Setup titles
        const isTv = media.media_type === "tv" || !!season;
        const mainTitle = media.title || media.name || "Now Playing";
        if (this.titleEl) this.titleEl.textContent = mainTitle;

        if (this.subtitleEl) {
            if (isTv && season && episode) {
                this.subtitleEl.textContent = `Season ${season} • Episode ${episode}`;
            } else if (media.release_date || media.first_air_date) {
                const year = (media.release_date || media.first_air_date || "").substring(0, 4);
                this.subtitleEl.textContent = `${year} • 4K Ultra HD • ATMOS`;
            } else {
                this.subtitleEl.textContent = "Cinema HD Stream";
            }
        }

        this.updateServerButtons();
        this.loadStreamUrl();
        this.container.classList.remove("hidden");
        this.container.classList.add("active");

        // Periodically track and record continue watching progress
        clearInterval(this.progressInterval);
        this.progressInterval = setInterval(() => {
            this.recordProgress();
        }, 3000);

        // Clear navigation focus so parent window retains full TV remote control
        if (window.Navigation && window.Navigation.currentFocus) {
            window.Navigation.currentFocus.classList.remove("focused");
            window.Navigation.currentFocus.blur();
            window.Navigation.currentFocus = null;
        }

        // Ensure parent container has focus to capture all TV remote keys
        if (this.container) {
            this.container.focus();
        }

        this.showHudTemporarily();
    },

    recordProgress() {
        if (!this.isOpen || !this.currentMedia) return;
        const elapsed = this.currentTime > 0 
            ? this.currentTime 
            : Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);

        if (elapsed < 5) return;
        this.lastSavedPos = elapsed;
        const dur = this.duration || ((this.currentMedia && this.currentMedia.runtime) ? (this.currentMedia.runtime * 60) : 5400);

        if (window.App && typeof window.App.updateContinueWatching === "function") {
            const isNearEnd = elapsed >= (dur - 30);
            if (isNearEnd) {
                window.App.removeContinueWatching(this.currentMedia.id, this.currentMedia.season, this.currentMedia.episode);
            } else {
                window.App.updateContinueWatching({
                    id: this.currentMedia.id,
                    title: this.currentMedia.title || this.currentMedia.name,
                    poster_path: this.currentMedia.poster_path,
                    backdrop_path: this.currentMedia.backdrop_path,
                    media_type: this.currentMedia.media_type || (this.currentMedia.season ? "tv" : "movie"),
                    season: this.currentMedia.season || null,
                    episode: this.currentMedia.episode || null,
                    position: elapsed,
                    duration: dur,
                    isOffline: false
                });
            }
        }
    },

    loadStreamUrl() {
        const { id, media_type, season, episode } = this.currentMedia;
        const isTv = media_type === "tv" || !!season;

        let url = "";
        const s = season || 1;
        const e = episode || 1;
        const startSec = Math.floor(this.initialStartTime);
        const timeParam = startSec > 5 ? `&startAt=${startSec}&t=${startSec}` : "";

        switch (this.currentServer) {
            case "moviesapi":
                url = isTv 
                    ? `https://player.moviesapi.vip/tv/${id}/${s}/${e}`
                    : `https://player.moviesapi.vip/movie/${id}`;
                break;
            case "twoembed":
                url = isTv 
                    ? `https://www.2embed.skin/embed/tv/${id}/${s}/${e}`
                    : `https://www.2embed.skin/embed/${id}`;
                break;
            case "vidlink":
                url = isTv 
                    ? `https://vidlink.pro/tv/${id}/${s}/${e}?autoplay=true&primaryColor=E50914${timeParam}`
                    : `https://vidlink.pro/movie/${id}?autoplay=true&primaryColor=E50914${timeParam}`;
                break;
            case "vidsrc_pm":
            default:
                url = isTv 
                    ? `https://vidsrc.pm/embed/tv/${id}/${s}/${e}?autoplay=1${timeParam}`
                    : `https://vidsrc.pm/embed/movie/${id}?autoplay=1${timeParam}`;
                break;
        }

        console.log(`[Player] Loading stream (${this.currentServer}): ${url} (startAt: ${startSec}s)`);
        this.frame.removeAttribute("sandbox");
        this.frame.src = url;
        this.isPaused = false;

        // Perform a single gentle autoplay assistance after 1.2s - NEVER loop or click repeatedly!
        setTimeout(() => {
            if (this.isOpen) {
                this.triggerInitialPlay();
            }
        }, 1200);
    },

    switchServer(serverKey) {
        // Carry forward elapsed time when switching servers
        const elapsed = this.currentTime > 0
            ? this.currentTime
            : Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);
        this.initialStartTime = elapsed;
        this.currentTime = elapsed;
        this.playStartTime = Date.now();
        this.currentServer = serverKey;
        this.updateServerButtons();
        this.loadStreamUrl();
        this.showToast(`Switched to ${this.getServerDisplayName(serverKey)}`);
        this.showHudTemporarily();
    },

    getServerDisplayName(key) {
        switch (key) {
            case "vidsrc_pm": return "Server 1 (VidSrc)";
            case "moviesapi": return "Server 2 (MoviesAPI)";
            case "twoembed": return "Server 3 (2Embed)";
            case "vidlink": return "Server 4 (VidLink)";
            default: return "Server";
        }
    },

    updateServerButtons() {
        const serverMap = {
            "vidsrc_pm": document.getElementById("server-btn-vidsrcpm"),
            "moviesapi": document.getElementById("server-btn-moviesapi"),
            "twoembed": document.getElementById("server-btn-twoembed"),
            "vidlink": document.getElementById("server-btn-vidlink")
        };
        for (const key in serverMap) {
            const btn = serverMap[key];
            if (btn) {
                if (key === this.currentServer) {
                    btn.classList.add("active");
                } else {
                    btn.classList.remove("active");
                }
            }
        }
    },

    triggerInitialPlay() {
        const frame = document.getElementById("player-frame");
        if (!frame) return;

        // 1. Send dedicated postMessage to embed player (vidsrc.pm & vidlink)
        if (frame.contentWindow) {
            try {
                frame.contentWindow.postMessage({ action: "play" }, "*");
                frame.contentWindow.postMessage({ type: "MEDIA_PLAY" }, "*");
            } catch (e) {}
        }

        // 2. If direct DOM is accessible, ensure video is playing and remove clickjack overlays
        try {
            const rootDoc = frame.contentDocument;
            if (!rootDoc) return;

            // Remove transparent / invisible clickjacking overlays
            const overlays = rootDoc.querySelectorAll("div, a");
            for (let i = 0; i < overlays.length; i++) {
                const el = overlays[i];
                const s = rootDoc.defaultView ? rootDoc.defaultView.getComputedStyle(el) : el.style;
                if (s && (s.position === "fixed" || s.position === "absolute")) {
                    const z = parseInt(s.zIndex, 10);
                    if (z >= 99 && !el.querySelector("video") && !el.querySelector("svg")) {
                        const op = parseFloat(s.opacity);
                        if (op < 0.05 || s.backgroundColor === "transparent") {
                            el.remove();
                        }
                    }
                }
            }

            // Click backdrop wrapper if present (e.g. 2embed)
            const backdrop = rootDoc.getElementById("backdrop-screen") || rootDoc.querySelector(".playbtnx");
            if (backdrop) {
                try { backdrop.click(); } catch (e) {}
            }

            // Only trigger play if video is actually paused; NEVER click repeatedly!
            const v = rootDoc.querySelector("video");
            if (v && v.paused) {
                v.muted = false;
                v.play().catch(() => {});
            }
        } catch (e) {}
    },

    togglePlayPause() {
        const frame = document.getElementById("player-frame");
        if (!frame) return;

        this.isPaused = !this.isPaused;

        // 1. Send dedicated postMessage to vidsrc.pm & vidlink
        if (frame.contentWindow) {
            try {
                frame.contentWindow.postMessage({ action: this.isPaused ? "pause" : "play" }, "*");
                frame.contentWindow.postMessage({ action: "togglePlay" }, "*");
                frame.contentWindow.postMessage({ type: this.isPaused ? "MEDIA_PAUSE" : "MEDIA_PLAY" }, "*");
            } catch (e) {}
        }

        // 2. Direct DOM fallback if accessible
        try {
            const rootDoc = frame.contentDocument;
            if (rootDoc) {
                const v = rootDoc.querySelector("video");
                if (v) {
                    if (this.isPaused) {
                        v.pause();
                    } else {
                        v.play().catch(() => {});
                    }
                }
            }
        } catch (e) {}

        // 3. Show sleek TV on-screen toast
        this.showToast(this.isPaused ? "⏸ Paused" : "▶ Playing");
    },

    seek(deltaSeconds) {
        const frame = document.getElementById("player-frame");
        if (!frame) return;

        const currentPos = (typeof this.currentTime === "number" && this.currentTime > 0)
            ? this.currentTime
            : Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);

        const target = Math.max(0, currentPos + deltaSeconds);
        this.currentTime = target;
        this.initialStartTime = target;
        this.playStartTime = Date.now();

        // 1. Send dedicated postMessage to vidsrc.pm & vidlink
        if (frame.contentWindow) {
            try {
                frame.contentWindow.postMessage({ action: "seek", time: target }, "*");
                frame.contentWindow.postMessage({ type: "MEDIA_SEEK", time: target }, "*");
            } catch (e) {}
        }

        // 2. Direct DOM fallback if accessible
        try {
            const rootDoc = frame.contentDocument;
            if (rootDoc) {
                const v = rootDoc.querySelector("video");
                if (v) {
                    v.currentTime = target;
                }
            }
        } catch (e) {}

        // 3. Format timestamp and display TV toast
        const formatTime = (s) => {
            const h = Math.floor(s / 3600);
            const m = Math.floor((s % 3600) / 60);
            const sec = Math.floor(s % 60);
            if (h > 0) {
                return `${h}:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;
            }
            return `${m}:${sec.toString().padStart(2, '0')}`;
        };

        const prefix = deltaSeconds > 0 ? "⏩ +" : "⏪ -";
        this.showToast(`${prefix}${Math.abs(deltaSeconds)}s  (${formatTime(target)})`);
    },

    showToast(text) {
        if (!this.toastEl || !this.toastTextEl) return;
        this.toastTextEl.textContent = text;
        this.toastEl.classList.remove("hidden");
        // Force reflow for CSS transition
        void this.toastEl.offsetWidth;
        this.toastEl.classList.add("active");

        clearTimeout(this.toastTimeout);
        this.toastTimeout = setTimeout(() => {
            if (this.toastEl) {
                this.toastEl.classList.remove("active");
                setTimeout(() => {
                    if (this.toastEl && !this.toastEl.classList.contains("active")) {
                        this.toastEl.classList.add("hidden");
                    }
                }, 250);
            }
        }, 1400);
    },

    showHudTemporarily() {
        if (!this.isOpen) return;
        if (this.hud) {
            this.hud.classList.remove("fade-out");
            this.hud.classList.add("fade-in");
        }

        clearTimeout(this.hideTimeout);
        this.hideTimeout = setTimeout(() => {
            if (this.isOpen) {
                this.hideHud();
            }
        }, 3500);
    },

    hideHud() {
        if (this.hud) {
            this.hud.classList.remove("fade-in");
            this.hud.classList.add("fade-out");
        }
        // Remove focus styling from top HUD buttons if unfocused
        const isHudFocused = window.Navigation && window.Navigation.currentFocus &&
            (window.Navigation.currentFocus.id === "player-back-btn" || window.Navigation.currentFocus.classList.contains("server-btn"));
        if (isHudFocused && window.Navigation) {
            window.Navigation.currentFocus.classList.remove("focused");
            window.Navigation.currentFocus.blur();
            window.Navigation.currentFocus = null;
        }
    },

    close() {
        this.isOpen = false;
        clearTimeout(this.hideTimeout);
        clearTimeout(this.toastTimeout);
        clearInterval(this.progressInterval);
        this.recordProgress();

        if (this.frame) {
            this.frame.src = "about:blank";
        }
        if (this.container) {
            this.container.classList.remove("active");
            this.container.classList.add("hidden");
        }
        if (this.toastEl) {
            this.toastEl.classList.remove("active");
            this.toastEl.classList.add("hidden");
        }

        if (window.TizenBridge) {
            window.TizenBridge.preventScreenSaver(false);
        }

        // Re-render home continue watching row
        if (window.App && typeof window.App.renderContinueWatchingRow === "function") {
            window.App.renderContinueWatchingRow();
        }

        // Return focus to Details Modal or last focused element
        if (window.App && window.App.isModalOpen) {
            const playBtn = document.getElementById("modal-play-btn");
            if (playBtn && window.Navigation) {
                window.Navigation.setFocus(playBtn);
            }
        } else if (window.Navigation) {
            window.Navigation.moveFocus(40);
        }
    }
};

window.Player = Player;
