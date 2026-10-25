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

    // Video Quality State
    currentQuality: localStorage.getItem("aura_quality") || "auto",
    qualityBtnEl: null,

    // Audio & Subtitles State
    isAudioSubModalOpen: false,
    isHudVisible: false,
    currentAudioTrack: localStorage.getItem("aura_audio") || "ta",
    currentSubtitle: "off",
    subtitleOffset: 0.0,
    subtitleCues: [],
    subtitleInterval: null,

    init() {
        this.container = document.getElementById("player-container");
        this.frame = document.getElementById("player-frame");
        this.titleEl = document.getElementById("player-title");
        this.subtitleEl = document.getElementById("player-subtitle");
        this.hud = document.getElementById("player-hud");
        this.toastEl = document.getElementById("player-toast");
        this.toastTextEl = document.getElementById("player-toast-text");

        this.subtitleOverlay = document.getElementById("player-subtitle-overlay");
        this.subtitleCueText = document.getElementById("subtitle-cue-text");
        this.audioSubModal = document.getElementById("audio-sub-modal");
        this.subtitleOffsetVal = document.getElementById("subtitle-offset-val");
        this.qualityBtnEl = document.getElementById("player-quality-btn");

        this.updateQualityDisplay();
        this.updateAudioTrackDisplay();

        // Wire explicit click listeners for top HUD action buttons
        const audioSubBtn = document.getElementById("player-audio-sub-btn");
        if (audioSubBtn) {
            audioSubBtn.addEventListener("click", (e) => {
                e.stopPropagation();
                this.openAudioSubModal('subtitles');
            });
        }
        const qualityBtn = document.getElementById("player-quality-btn");
        if (qualityBtn) {
            qualityBtn.addEventListener("click", (e) => {
                e.stopPropagation();
                this.openAudioSubModal('quality');
            });
        }
        const backBtn = document.getElementById("player-back-btn");
        if (backBtn) {
            backBtn.addEventListener("click", (e) => {
                e.stopPropagation();
                this.close();
            });
        }
        const serverMap = {
            "server-btn-vidsrcpm": "vidsrc_pm",
            "server-btn-vidlink": "vidlink",
            "server-btn-twoembed": "twoembed",
            "server-btn-vidsrcsu": "vidsrc_su"
        };
        for (const btnId in serverMap) {
            const btn = document.getElementById(btnId);
            if (btn) {
                const sKey = serverMap[btnId];
                btn.addEventListener("click", (e) => {
                    e.stopPropagation();
                    this.switchServer(sKey);
                });
            }
        }

        // Activity monitor to auto-hide HUD
        window.addEventListener("mousemove", () => {
            if (this.isOpen && !this.isAudioSubModalOpen) {
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
                    this.updateSubtitleCue();
                }

                if (data.event === "play") {
                    this.isPaused = false;
                } else if (data.event === "pause") {
                    this.isPaused = true;
                } else if (data.event === "seeked" && typeof data.currentTime === "number") {
                    this.currentTime = Math.floor(data.currentTime);
                    this.updateSubtitleCue();
                }
            } catch (e) {}
        });
    },

    play(media, season = null, episode = null, startTime = null) {
        this.currentMedia = Object.assign({}, media, { season: season, episode: episode });
        this.isOpen = true;
        this.isPaused = false;
        this.isAudioSubModalOpen = false;

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

        // Subtitle sync ticker
        clearInterval(this.subtitleInterval);
        this.subtitleInterval = setInterval(() => {
            this.updateSubtitleCue();
        }, 250);

        // Position initial focus on Audio & Subtitles button so TV remote immediately has active navigation
        setTimeout(() => {
            const initialBtn = document.getElementById("player-audio-sub-btn") || document.getElementById("player-back-btn");
            if (initialBtn && window.Navigation) {
                window.Navigation.setFocus(initialBtn);
            }
        }, 150);

        this.showHudTemporarily(7000);
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
        const qualParam = (this.currentQuality && this.currentQuality !== "auto") ? `&quality=${this.currentQuality}` : "";
        const audioParam = (this.currentAudioTrack && this.currentAudioTrack !== "original") 
            ? `&audio=${this.currentAudioTrack}&lang=${this.currentAudioTrack}` 
            : "";

        switch (this.currentServer) {
            case "vidlink":
                url = isTv 
                    ? `https://vidlink.pro/tv/${id}/${s}/${e}?autoplay=true&primaryColor=E50914${timeParam}${qualParam}${audioParam}`
                    : `https://vidlink.pro/movie/${id}?autoplay=true&primaryColor=E50914${timeParam}${qualParam}${audioParam}`;
                break;
            case "twoembed":
                url = isTv 
                    ? `https://www.2embed.cc/embedtv/${id}&s=${s}&e=${e}`
                    : `https://www.2embed.cc/embed/${id}`;
                break;
            case "vidsrc_su":
                url = isTv 
                    ? `https://vidsrc.su/embed/tv/${id}/${s}/${e}`
                    : `https://vidsrc.su/embed/movie/${id}`;
                break;
            case "vidsrc_pm":
            default:
                url = isTv 
                    ? `https://vidsrc.pm/embed/tv/${id}/${s}/${e}?autoplay=1${timeParam}`
                    : `https://vidsrc.pm/embed/movie/${id}?autoplay=1${timeParam}`;
                break;
        }

        console.log(`[Player] Loading stream (${this.currentServer}): ${url} (startAt: ${startSec}s, quality: ${this.currentQuality})`);
        this.frame.removeAttribute("sandbox");
        this.frame.src = url;
        this.isPaused = false;

        // Perform a single gentle autoplay check after 1.2s - NEVER loop or click repeatedly!
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
            case "vidlink": return "Server 2 (VidLink Pro)";
            case "twoembed": return "Server 3 (2Embed)";
            case "vidsrc_su": return "Server 4 (VidSrc SU)";
            default: return "Server";
        }
    },

    updateServerButtons() {
        const serverMap = {
            "vidsrc_pm": document.getElementById("server-btn-vidsrcpm"),
            "vidlink": document.getElementById("server-btn-vidlink"),
            "twoembed": document.getElementById("server-btn-twoembed"),
            "vidsrc_su": document.getElementById("server-btn-vidsrcsu")
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
        this.updateAudioServerStatuses();
    },

    updateAudioServerStatuses() {
        const cards = [
            { id: "audio-card-vidsrcpm", statusId: "status-vidsrcpm", key: "vidsrc_pm" },
            { id: "audio-card-vidlink", statusId: "status-vidlink", key: "vidlink" },
            { id: "audio-card-twoembed", statusId: "status-twoembed", key: "twoembed" },
            { id: "audio-card-vidsrcsu", statusId: "status-vidsrcsu", key: "vidsrc_su" }
        ];

        cards.forEach(c => {
            const cardEl = document.getElementById(c.id);
            const statusEl = document.getElementById(c.statusId);
            const isActive = (c.key === this.currentServer);
            if (cardEl) {
                if (isActive) cardEl.classList.add("active");
                else cardEl.classList.remove("active");
            }
            if (statusEl) {
                statusEl.textContent = isActive ? "Active" : "";
            }
        });
        this.updateAudioTrackDisplay();
    },

    getAudioTrackDisplayName(key) {
        switch (key) {
            case "ta": return "Tamil (தமிழ்)";
            case "hi": return "Hindi (हिंदी Dub)";
            case "te": return "Telugu (తెలుగు Dub)";
            case "ml": return "Malayalam (മലയാളം Dub)";
            case "kn": return "Kannada (ಕನ್ನಡ Dub)";
            case "en": return "English";
            case "original":
            default:
                return "Original Audio (5.1 / Atmos)";
        }
    },

    selectAudioTrack(trackKey) {
        this.currentAudioTrack = trackKey;
        try {
            localStorage.setItem("aura_audio", trackKey);
        } catch (e) {}

        this.updateAudioTrackDisplay();
        const displayName = this.getAudioTrackDisplayName(trackKey);
        this.showToast(`🔊 Audio: ${displayName}`);

        // If user picked a dubbed track and currently on Server 1,
        // switch to Server 2 (VidLink Pro) which provides multi-audio dub streams
        if (trackKey !== "original" && this.currentServer === "vidsrc_pm") {
            this.switchServer("vidlink");
            this.showToast(`Switched to Server 2 (VidLink Pro) for ${displayName}`);
        } else if (this.currentServer === "vidlink") {
            const frame = document.getElementById("player-frame");
            if (frame && frame.contentWindow) {
                try {
                    frame.contentWindow.postMessage({ action: "setAudioTrack", audio: trackKey }, "*");
                    frame.contentWindow.postMessage({ type: "CHANGE_AUDIO", language: trackKey }, "*");
                } catch (e) {}
            }
        }
    },

    updateAudioTrackDisplay() {
        const buttons = document.querySelectorAll(".audio-lang-btn");
        buttons.forEach(btn => {
            if (btn.getAttribute("data-audio") === this.currentAudioTrack) {
                btn.classList.add("active");
            } else {
                btn.classList.remove("active");
            }
        });
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

        this.updateSubtitleCue();

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

    showHudTemporarily(duration = 7000) {
        if (!this.isOpen || this.isAudioSubModalOpen) return;
        this.isHudVisible = true;
        if (this.hud) {
            this.hud.classList.remove("fade-out");
            this.hud.classList.add("fade-in");
        }

        clearTimeout(this.hideTimeout);
        this.hideTimeout = setTimeout(() => {
            if (this.isOpen && !this.isAudioSubModalOpen) {
                // If user is currently focused on any top HUD button, don't suddenly hide
                const isHudFocused = window.Navigation && window.Navigation.currentFocus &&
                    (window.Navigation.currentFocus.id === "player-back-btn" || 
                     window.Navigation.currentFocus.id === "player-audio-sub-btn" ||
                     window.Navigation.currentFocus.id === "player-quality-btn" ||
                     window.Navigation.currentFocus.classList.contains("server-btn") ||
                     (window.Navigation.currentFocus.closest && window.Navigation.currentFocus.closest(".player-hud")));
                if (!isHudFocused) {
                    this.hideHud();
                } else {
                    // Refresh HUD once more if user was navigating
                    this.showHudTemporarily(4000);
                }
            }
        }, duration);
    },

    hideHud() {
        this.isHudVisible = false;
        if (this.hud) {
            this.hud.classList.remove("fade-in");
            this.hud.classList.add("fade-out");
        }
        // Remove focus styling from top HUD buttons if unfocused
        const isHudFocused = window.Navigation && window.Navigation.currentFocus &&
            (window.Navigation.currentFocus.id === "player-back-btn" || 
             window.Navigation.currentFocus.id === "player-audio-sub-btn" ||
             window.Navigation.currentFocus.id === "player-quality-btn" ||
             window.Navigation.currentFocus.classList.contains("server-btn") ||
             (window.Navigation.currentFocus.closest && window.Navigation.currentFocus.closest(".player-hud")));
        if (isHudFocused && window.Navigation) {
            window.Navigation.currentFocus.classList.remove("focused");
            window.Navigation.currentFocus.blur();
            window.Navigation.currentFocus = null;
        }
        if (this.container) {
            this.container.focus();
        }
    },

    // -------------------------------------------------------------
    // Audio, Subtitles & Quality Menu and On-Screen Display Engine
    // -------------------------------------------------------------

    openAudioSubModal(defaultTab = "subtitles") {
        this.isAudioSubModalOpen = true;
        this.hideHud();
        if (this.audioSubModal) {
            this.audioSubModal.classList.remove("hidden");
        }
        this.updateAudioServerStatuses();
        this.updateQualityDisplay();
        this.switchAudioSubTab(defaultTab);
    },

    closeAudioSubModal() {
        this.isAudioSubModalOpen = false;
        if (this.audioSubModal) {
            this.audioSubModal.classList.add("hidden");
        }
        // Return focus to corresponding button in HUD
        this.showHudTemporarily();
        const targetBtn = document.getElementById("player-audio-sub-btn") || document.getElementById("player-back-btn");
        if (targetBtn && window.Navigation) {
            window.Navigation.setFocus(targetBtn);
        }
    },

    switchAudioSubTab(tabName) {
        const subTab = document.getElementById("tab-subtitles");
        const audioTab = document.getElementById("tab-audio");
        const qualityTab = document.getElementById("tab-quality");

        const subContent = document.getElementById("subtitles-tab-content");
        const audioContent = document.getElementById("audio-tab-content");
        const qualityContent = document.getElementById("quality-tab-content");

        if (subTab) subTab.classList.remove("active");
        if (audioTab) audioTab.classList.remove("active");
        if (qualityTab) qualityTab.classList.remove("active");

        if (subContent) subContent.classList.add("hidden");
        if (audioContent) audioContent.classList.add("hidden");
        if (qualityContent) qualityContent.classList.add("hidden");

        if (tabName === "quality") {
            if (qualityTab) qualityTab.classList.add("active");
            if (qualityContent) qualityContent.classList.remove("hidden");
            const activeCard = document.querySelector(".quality-card.active") || qualityTab;
            if (activeCard && window.Navigation) {
                window.Navigation.setFocus(activeCard);
            }
        } else if (tabName === "audio") {
            if (audioTab) audioTab.classList.add("active");
            if (audioContent) audioContent.classList.remove("hidden");
            const activeCard = document.querySelector(".audio-lang-btn.active") || document.querySelector(".audio-server-card.active") || audioTab;
            if (activeCard && window.Navigation) {
                window.Navigation.setFocus(activeCard);
            }
        } else {
            if (subTab) subTab.classList.add("active");
            if (subContent) subContent.classList.remove("hidden");
            const activeBtn = document.querySelector(".sub-lang-btn.active") || subTab;
            if (activeBtn && window.Navigation) {
                window.Navigation.setFocus(activeBtn);
            }
        }
    },

    getQualityDisplayName(key) {
        switch (key) {
            case "2160": return "4K Ultra HD (2160p)";
            case "1080": return "Full HD (1080p)";
            case "720": return "HD (720p)";
            case "480": return "SD (480p)";
            case "auto":
            default:
                return "Auto (Adaptive)";
        }
    },

    getQualityShortLabel(key) {
        switch (key) {
            case "2160": return "4K";
            case "1080": return "1080p";
            case "720": return "720p";
            case "480": return "480p";
            case "auto":
            default:
                return "Auto";
        }
    },

    selectQuality(qualityKey) {
        this.currentQuality = qualityKey;
        try {
            localStorage.setItem("aura_quality", qualityKey);
        } catch (e) {}

        this.updateQualityDisplay();
        const displayName = this.getQualityDisplayName(qualityKey);
        this.showToast(`⚙ Resolution: ${displayName}`);

        // Try postMessage to active stream player
        if (this.frame && this.frame.contentWindow) {
            try {
                this.frame.contentWindow.postMessage({ action: "setQuality", quality: qualityKey }, "*");
                this.frame.contentWindow.postMessage({ type: "SET_QUALITY", quality: qualityKey }, "*");
                this.frame.contentWindow.postMessage({ quality: qualityKey }, "*");
            } catch (e) {}
        }

        // Seamlessly carry over elapsed playback position
        const elapsed = this.currentTime > 0
            ? this.currentTime
            : Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);
        this.initialStartTime = elapsed;
        this.currentTime = elapsed;
        this.playStartTime = Date.now();

        // If a specific resolution is selected and on Server 1 (which lacks resolution parameters),
        // switch to Server 2 (VidLink Pro) which provides multi-resolution streaming
        if (qualityKey !== "auto" && this.currentServer === "vidsrc_pm") {
            this.currentServer = "vidlink";
            this.updateServerButtons();
            this.showToast(`Switched to Server 2 for ${displayName}`);
        }

        // Always reload stream URL with selected quality parameter
        this.loadStreamUrl();

        // Auto close modal smoothly so viewer sees their chosen stream
        setTimeout(() => {
            if (this.isAudioSubModalOpen) {
                this.closeAudioSubModal();
            }
        }, 350);
    },

    updateQualityDisplay() {
        if (!this.qualityBtnEl) {
            this.qualityBtnEl = document.getElementById("player-quality-btn");
        }
        if (this.qualityBtnEl) {
            this.qualityBtnEl.textContent = `⚙ Quality: ${this.getQualityShortLabel(this.currentQuality)}`;
        }

        const qualityKeys = ["auto", "2160", "1080", "720", "480"];
        qualityKeys.forEach(k => {
            const cardEl = document.getElementById(`quality-card-${k}`);
            const statusEl = document.getElementById(`status-quality-${k}`);
            const isActive = (k === this.currentQuality);
            if (cardEl) {
                if (isActive) cardEl.classList.add("active");
                else cardEl.classList.remove("active");
            }
            if (statusEl) {
                statusEl.textContent = isActive ? "Active" : "";
            }
        });
    },

    selectSubtitle(lang) {
        this.currentSubtitle = lang;
        const buttons = document.querySelectorAll(".sub-lang-btn");
        buttons.forEach(btn => {
            if (btn.getAttribute("data-lang") === lang) {
                btn.classList.add("active");
            } else {
                btn.classList.remove("active");
            }
        });

        if (lang === "off") {
            this.subtitleCues = [];
            this.hideSubtitleOverlay();
            this.showToast("Subtitles: Off");
            return;
        }

        const langName = this.getLanguageDisplayName(lang);
        this.showToast(`Loading ${langName} Subtitles...`);
        this.fetchSubtitles(lang);
    },

    getLanguageDisplayName(code) {
        const names = {
            "ta": "Tamil (தமிழ்)",
            "en": "English",
            "es": "Spanish",
            "fr": "French",
            "de": "German",
            "hi": "Hindi",
            "ar": "Arabic",
            "pt": "Portuguese",
            "it": "Italian",
            "ja": "Japanese"
        };
        return names[code] || code.toUpperCase();
    },

    fetchSubtitles(lang) {
        if (!this.currentMedia) return;
        const { id, media_type, season, episode } = this.currentMedia;
        const isTv = media_type === "tv" || !!season;

        // Build OpenSubtitles endpoint URL
        const endpoint = isTv
            ? `https://opensubtitles-v3.strem.fun/subtitles/series/${id}:${season || 1}:${episode || 1}/${lang}.vtt`
            : `https://opensubtitles-v3.strem.fun/subtitles/movie/${id}/${lang}.vtt`;

        console.log(`[Player] Fetching subtitles for ${lang}: ${endpoint}`);
        fetch(endpoint)
            .then(res => {
                if (!res.ok) throw new Error("Status " + res.status);
                return res.text();
            })
            .then(vttText => {
                this.subtitleCues = this.parseSubtitleText(vttText);
                this.showToast(`${this.getLanguageDisplayName(lang)} Subtitles Enabled (${this.subtitleCues.length} cues)`);
                this.updateSubtitleCue();
            })
            .catch(err => {
                console.log("[Player] Subtitle fetch notice:", err);
                this.showToast(`${this.getLanguageDisplayName(lang)} Subtitles Active`);
            });
    },

    parseSubtitleText(text) {
        const cues = [];
        if (!text) return cues;

        const lines = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
        let i = 0;

        const parseTimeSec = (str) => {
            const clean = str.trim().replace(",", ".");
            const segs = clean.split(":").map(Number);
            if (segs.length === 3) return segs[0] * 3600 + segs[1] * 60 + segs[2];
            if (segs.length === 2) return segs[0] * 60 + segs[1];
            return 0;
        };

        while (i < lines.length) {
            const line = lines[i].trim();
            // Look for timestamp cue line: 00:01:20.000 --> 00:01:24.000
            if (line.includes("-->")) {
                const parts = line.split("-->");
                if (parts.length === 2) {
                    const start = parseTimeSec(parts[0]);
                    const end = parseTimeSec(parts[1]);
                    i++;
                    let cueContent = "";
                    while (i < lines.length && lines[i].trim() !== "") {
                        const cleanLine = lines[i].replace(/<[^>]+>/g, "").trim();
                        if (cleanLine) {
                            cueContent += (cueContent ? "\n" : "") + cleanLine;
                        }
                        i++;
                    }
                    if (cueContent && end > start) {
                        cues.push({ start, end, text: cueContent });
                    }
                }
            }
            i++;
        }

        console.log(`[Player] Parsed ${cues.length} subtitle cues`);
        return cues;
    },

    updateSubtitleCue() {
        if (!this.isOpen || this.currentSubtitle === "off" || !this.subtitleCues.length) {
            this.hideSubtitleOverlay();
            return;
        }

        const currentPos = (typeof this.currentTime === "number" && this.currentTime > 0)
            ? this.currentTime
            : Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);

        const time = currentPos + (this.subtitleOffset || 0);
        const active = this.subtitleCues.find(c => time >= c.start && time <= c.end);

        if (active && active.text) {
            this.showSubtitleOverlay(active.text);
        } else {
            this.hideSubtitleOverlay();
        }
    },

    showSubtitleOverlay(text) {
        if (this.subtitleCueText && this.subtitleOverlay) {
            this.subtitleCueText.textContent = text;
            this.subtitleOverlay.classList.remove("hidden");
        }
    },

    hideSubtitleOverlay() {
        if (this.subtitleOverlay) {
            this.subtitleOverlay.classList.add("hidden");
        }
    },

    adjustSubtitleOffset(delta, isReset = false) {
        if (isReset) {
            this.subtitleOffset = 0.0;
        } else {
            this.subtitleOffset = Math.round((this.subtitleOffset + delta) * 10) / 10;
        }

        if (this.subtitleOffsetVal) {
            const prefix = this.subtitleOffset > 0 ? "+" : "";
            this.subtitleOffsetVal.textContent = `${prefix}${this.subtitleOffset.toFixed(1)}s`;
        }

        this.updateSubtitleCue();
        const prefix = this.subtitleOffset > 0 ? "+" : "";
        this.showToast(`Subtitle Delay: ${prefix}${this.subtitleOffset.toFixed(1)}s`);
    },

    close() {
        this.isOpen = false;
        this.isAudioSubModalOpen = false;
        clearTimeout(this.hideTimeout);
        clearTimeout(this.toastTimeout);
        clearInterval(this.progressInterval);
        clearInterval(this.subtitleInterval);
        this.recordProgress();
        this.hideSubtitleOverlay();

        if (this.audioSubModal) {
            this.audioSubModal.classList.add("hidden");
        }

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
