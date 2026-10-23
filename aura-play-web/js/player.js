// Aura Play - Cinema Streaming Player
const Player = {
    isOpen: false,
    currentMedia: null,
    currentServer: "vidlink",
    hideTimeout: null,
    initialStartTime: 0,
    playStartTime: 0,
    progressInterval: null,
    lastSavedPos: 0,

    init() {
        this.container = document.getElementById("player-container");
        this.frame = document.getElementById("player-frame");
        this.titleEl = document.getElementById("player-title");
        this.subtitleEl = document.getElementById("player-subtitle");
        this.hud = document.getElementById("player-hud");
        this.streamHud = document.getElementById("player-stream-hud");

        // Activity monitor to auto-hide HUD
        window.addEventListener("mousemove", () => {
            this.showHudTemporarily();
        });
        window.addEventListener("keydown", () => {
            if (this.isOpen) {
                this.showHudTemporarily();
            }
        });

        // Listen for postMessage from embed stream players for exact timestamp sync
        window.addEventListener("message", (ev) => {
            if (!this.isOpen || !ev.data) return;
            try {
                const data = typeof ev.data === "string" ? JSON.parse(ev.data) : ev.data;
                if (data && (typeof data.currentTime === "number" || typeof data.time === "number")) {
                    const time = data.currentTime || data.time;
                    if (time > 0) {
                        this.initialStartTime = time;
                        this.playStartTime = Date.now();
                        this.recordProgress();
                    }
                }
            } catch (e) {}
        });
    },

    play(media, season = null, episode = null, startTime = null) {
        this.currentMedia = Object.assign({}, media, { season: season, episode: episode });
        this.isOpen = true;

        // Check if there is an existing continue watching progress
        const saved = (window.App && typeof window.App.getContinueWatchingItem === "function")
            ? window.App.getContinueWatchingItem(media.id, season, episode)
            : null;

        this.initialStartTime = (typeof startTime === "number") ? startTime : (saved ? (saved.position || 0) : 0);
        this.playStartTime = Date.now();
        this.lastSavedPos = this.initialStartTime;

        if (window.TizenBridge) {
            window.TizenBridge.preventScreenSaver(true);
        }

        // Setup titles
        const isTv = media.media_type === "tv" || !!season;
        const mainTitle = media.title || media.name || "Now Playing";
        this.titleEl.textContent = mainTitle;

        if (isTv && season && episode) {
            this.subtitleEl.textContent = `Season ${season} • Episode ${episode}`;
        } else if (media.release_date || media.first_air_date) {
            const year = (media.release_date || media.first_air_date || "").substring(0, 4);
            this.subtitleEl.textContent = `${year} • 4K Ultra HD • ATMOS`;
        } else {
            this.subtitleEl.textContent = "Cinema HD Stream";
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

        // Auto-focus the Play button on the TV remote control bar so user can just press OK!
        setTimeout(() => {
            const playBtn = document.getElementById("stream-play-btn");
            if (playBtn && window.Navigation) {
                window.Navigation.setFocus(playBtn);
            } else {
                const backBtn = document.getElementById("player-back-btn");
                if (backBtn && window.Navigation) {
                    window.Navigation.setFocus(backBtn);
                }
            }
        }, 150);

        this.showHudTemporarily();
    },

    recordProgress() {
        if (!this.isOpen || !this.currentMedia) return;
        const elapsed = Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);
        if (elapsed < 5) return;
        this.lastSavedPos = elapsed;
        const dur = (this.currentMedia && this.currentMedia.runtime) ? (this.currentMedia.runtime * 60) : 5400;

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
            case "vidsrc_xyz":
                url = isTv 
                    ? `https://vidsrc.xyz/embed/tv?tmdb=${id}&season=${s}&episode=${e}${timeParam}`
                    : `https://vidsrc.xyz/embed/movie?tmdb=${id}${timeParam}`;
                break;
            case "vidsrc_cc":
                url = isTv 
                    ? `https://vidsrc.cc/v2/embed/tv/${id}/${s}/${e}${timeParam}`
                    : `https://vidsrc.cc/v2/embed/movie/${id}${timeParam}`;
                break;
            case "autoembed":
                url = isTv 
                    ? `https://autoembed.co/tv/tmdb/${id}-${s}-${e}${startSec > 5 ? '?time=' + startSec : ''}`
                    : `https://autoembed.co/movie/tmdb/${id}${startSec > 5 ? '?time=' + startSec : ''}`;
                break;
            case "vidlink":
            default:
                url = isTv 
                    ? `https://vidlink.pro/tv/${id}/${s}/${e}?autoplay=true&primaryColor=E50914${timeParam}`
                    : `https://vidlink.pro/movie/${id}?autoplay=true&primaryColor=E50914${timeParam}`;
                break;
        }

        console.log(`[Player] Loading stream (${this.currentServer}): ${url} (startAt: ${startSec}s)`);
        this.frame.removeAttribute("sandbox");
        this.frame.src = url;
    },

    switchServer(serverKey) {
        // Carry forward elapsed time when switching servers
        const elapsed = Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);
        this.initialStartTime = elapsed;
        this.playStartTime = Date.now();
        this.currentServer = serverKey;
        this.updateServerButtons();
        this.loadStreamUrl();
        this.showHudTemporarily();
    },

    updateServerButtons() {
        const serverMap = {
            "vidlink": document.getElementById("server-btn-vidlink"),
            "vidsrc_cc": document.getElementById("server-btn-vidsrc"),
            "vidsrc_xyz": document.getElementById("server-btn-vidsrcxyz"),
            "autoembed": document.getElementById("server-btn-autoembed")
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

    interactStream() {
        this.showHudTemporarily();
        const frame = document.getElementById("player-frame");
        if (frame) {
            frame.focus();
            if (frame.contentWindow) {
                try {
                    frame.contentWindow.focus();
                    // Dispatch standard HTML5 video message triggers across origins
                    frame.contentWindow.postMessage('{"event":"command","func":"playVideo","args":""}', '*');
                    frame.contentWindow.postMessage({ type: 'play' }, '*');
                } catch (e) {
                    // Ignore cross-origin access limitations
                }
            }
        }
        const playIcon = document.getElementById("stream-play-icon");
        if (playIcon) {
            playIcon.textContent = playIcon.textContent === "▶" ? "⏸" : "▶";
        }
    },

    togglePlayPause() {
        this.interactStream();
    },

    focusVideoFrame() {
        const frame = document.getElementById("player-frame");
        if (frame) {
            frame.focus();
            if (frame.contentWindow) {
                try {
                    frame.contentWindow.focus();
                } catch (e) {}
            }
        }
    },

    seek(seconds) {
        this.showHudTemporarily();
        const frame = document.getElementById("player-frame");
        if (frame && frame.contentWindow) {
            try {
                frame.contentWindow.postMessage({ type: 'seek', offset: seconds }, '*');
            } catch (e) {}
        }
    },

    reloadStream() {
        this.loadStreamUrl();
        this.showHudTemporarily();
        const playBtn = document.getElementById("stream-play-btn");
        if (playBtn && window.Navigation) {
            window.Navigation.setFocus(playBtn);
        }
    },

    showHudTemporarily() {
        if (!this.isOpen) {
            return;
        }
        if (this.hud) {
            this.hud.classList.remove("fade-out");
            this.hud.classList.add("fade-in");
        }
        if (this.streamHud) {
            this.streamHud.classList.remove("fade-out");
            this.streamHud.classList.add("fade-in");
        }

        clearTimeout(this.hideTimeout);
        this.hideTimeout = setTimeout(() => {
            if (this.isOpen) {
                if (this.hud) {
                    this.hud.classList.remove("fade-in");
                    this.hud.classList.add("fade-out");
                }
                if (this.streamHud) {
                    this.streamHud.classList.remove("fade-in");
                    this.streamHud.classList.add("fade-out");
                }
            }
        }, 4000);
    },

    close() {
        this.isOpen = false;
        clearTimeout(this.hideTimeout);
        clearInterval(this.progressInterval);
        this.recordProgress();
        this.frame.src = "about:blank";
        this.container.classList.remove("active");
        this.container.classList.add("hidden");

        if (window.TizenBridge) {
            window.TizenBridge.preventScreenSaver(false);
        }

        // Re-render home continue watching row
        if (window.App && typeof window.App.renderContinueWatchingRow === "function") {
            window.App.renderContinueWatchingRow();
        }

        // Return focus to Details Modal or Last focused element
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
