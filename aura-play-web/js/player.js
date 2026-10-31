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
    isVideoPlaying: false,
    fallbackPlayTimer: null,
    currentAudioTrack: localStorage.getItem("aura_audio") || "ta",
    currentSubtitle: "off",
    subtitleOffset: 0.0,
    subtitleCues: [],
    subtitleInterval: null,
    timelineInterval: null,

    // Volume & Sound State
    volume: (function() {
        const v = localStorage.getItem("aura_player_volume");
        return v !== null ? parseFloat(v) : 1.0;
    })(),
    isMuted: false,
    volumeBtnEl: null,
    unmuteBannerEl: null,

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
        this.volumeBtnEl = document.getElementById("player-volume-btn");
        this.unmuteBannerEl = document.getElementById("player-unmute-banner");

        this.updateQualityDisplay();
        this.updateAudioTrackDisplay();
        this.updateVolumeDisplay();

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

        // Wire click listeners for volume controls
        if (this.volumeBtnEl) {
            this.volumeBtnEl.addEventListener("click", (e) => {
                e.stopPropagation();
                this.toggleMute();
            });
        }
        if (this.unmuteBannerEl) {
            this.unmuteBannerEl.addEventListener("click", (e) => {
                e.stopPropagation();
                this.unmute();
            });
        }
        const volTvDown = document.getElementById("btn-vol-tv-down");
        if (volTvDown) volTvDown.addEventListener("click", (e) => { e.stopPropagation(); this.adjustTvVolume(-1); });
        const volTvUp = document.getElementById("btn-vol-tv-up");
        if (volTvUp) volTvUp.addEventListener("click", (e) => { e.stopPropagation(); this.adjustTvVolume(1); });
        const vol100 = document.getElementById("btn-vol-100");
        if (vol100) vol100.addEventListener("click", (e) => { e.stopPropagation(); this.setVolume(1.0); });
        const volMute = document.getElementById("btn-vol-mute");
        if (volMute) volMute.addEventListener("click", (e) => { e.stopPropagation(); this.toggleMute(); });

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
                let data = ev.data;
                if (typeof data === "string") {
                    try { data = JSON.parse(data); } catch (e) {}
                }
                if (!data) return;
                if (data.data && typeof data.data === "object") {
                    data = Object.assign({}, data, data.data);
                }

                const eventName = (data.event || data.type || "").toLowerCase();

                if (eventName === "play" || eventName === "playing" || eventName === "media_play") {
                    this.isVideoPlaying = true;
                    this.isPaused = false;
                    this.playStartTime = Date.now();
                } else if (eventName === "pause" || eventName === "media_pause" || eventName === "ended") {
                    this.isVideoPlaying = false;
                    this.isPaused = true;
                    this.hideSubtitleOverlay();
                }

                const time = (typeof data.currentTime === "number") 
                    ? data.currentTime 
                    : ((typeof data.time === "number") ? data.time : null);

                if (typeof time === "number" && !isNaN(time) && time >= 0) {
                    this.currentTime = Math.floor(time);
                    this.initialStartTime = this.currentTime;
                    this.playStartTime = Date.now();
                    this.isVideoPlaying = true;
                    this.isPaused = false;

                    if (typeof data.duration === "number" && data.duration > 0) {
                        this.duration = Math.floor(data.duration);
                    }
                    this.recordProgress();
                    this.updateSubtitleCue();
                } else if (eventName === "seeked" && typeof data.currentTime === "number") {
                    this.currentTime = Math.floor(data.currentTime);
                    this.updateSubtitleCue();
                }

                // Listen for volume or mute updates from stream player
                if (data.muted === true) {
                    this.isMuted = true;
                    this.updateVolumeDisplay();
                } else if (data.muted === false) {
                    this.isMuted = false;
                    this.updateVolumeDisplay();
                }
                if (typeof data.volume === "number" && !isNaN(data.volume)) {
                    this.volume = Math.max(0, Math.min(1.0, data.volume > 1 ? data.volume / 100 : data.volume));
                    this.updateVolumeDisplay();
                }
            } catch (e) {}
        });
    },

    play(media, season = null, episode = null, startTime = null) {
        if (window.App && window.App.isModalOpen) {
            window.App.closeModal();
        }

        this.currentMedia = Object.assign({}, media, { season: season, episode: episode });
        this.isOpen = true;
        this.isPaused = false;
        this.isVideoPlaying = false;
        this.isAudioSubModalOpen = false;
        this.hideSubtitleOverlay();
        clearTimeout(this.fallbackPlayTimer);

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

        // Timeline ticker (advances every 1s only when video is genuinely playing)
        clearInterval(this.timelineInterval);
        this.updateTimelineDisplay();
        this.timelineInterval = setInterval(() => {
            if (this.isOpen && this.isVideoPlaying && !this.isPaused) {
                this.currentTime = (typeof this.currentTime === "number" && this.currentTime > 0)
                    ? this.currentTime + 1
                    : Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);
                this.updateTimelineDisplay();
            }
        }, 1000);

        // Subtitle sync ticker
        clearInterval(this.subtitleInterval);
        this.subtitleInterval = setInterval(() => {
            this.updateSubtitleCue();
        }, 250);

        // Position initial focus on Top HUD button so remote can immediately access Back, Audio, Quality, Server
        setTimeout(() => {
            const initialFocusBtn = document.getElementById("player-audio-sub-btn") || 
                                    document.getElementById("player-quality-btn") || 
                                    document.getElementById("player-back-btn");
            if (initialFocusBtn && window.Navigation) {
                window.Navigation.setFocus(initialFocusBtn);
            }
        }, 150);

        this.showHudTemporarily(7000);
    },

    async getImdbId() {
        if (!this.currentMedia) return null;
        if (this.currentMedia.imdb_id) return this.currentMedia.imdb_id;
        if (this.currentMedia.external_ids && this.currentMedia.external_ids.imdb_id) {
            this.currentMedia.imdb_id = this.currentMedia.external_ids.imdb_id;
            return this.currentMedia.imdb_id;
        }
        try {
            const isTv = this.currentMedia.media_type === "tv" || !!this.currentMedia.season;
            const type = isTv ? "tv" : "movie";
            if (window.TMDB && typeof window.TMDB.getExternalIds === "function") {
                const data = await window.TMDB.getExternalIds(type, this.currentMedia.id);
                if (data && data.imdb_id) {
                    this.currentMedia.imdb_id = data.imdb_id;
                    return data.imdb_id;
                }
            }
        } catch (e) {
            console.warn("[Player] Could not fetch IMDb ID:", e);
        }
        return null;
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
        const qualParam = (this.currentQuality && this.currentQuality !== "auto") 
            ? `&quality=${this.currentQuality}&res=${this.currentQuality}` 
            : "";
        const audioParam = (this.currentAudioTrack && this.currentAudioTrack !== "original") 
            ? `&audio=${this.currentAudioTrack}&lang=${this.currentAudioTrack}` 
            : "";
        const subParam = (this.activeSubtitleUrl && this.currentSubtitle !== "off")
            ? `&sub_url=${encodeURIComponent(this.activeSubtitleUrl)}&sub_label=${encodeURIComponent(this.getLanguageDisplayName(this.currentSubtitle))}`
            : "";

        switch (this.currentServer) {
            case "vidlink":
                url = isTv 
                    ? `https://vidlink.pro/tv/${id}/${s}/${e}?autoplay=true&primaryColor=E50914&multiLang=true${timeParam}${qualParam}${audioParam}${subParam}`
                    : `https://vidlink.pro/movie/${id}?autoplay=true&primaryColor=E50914&multiLang=true${timeParam}${qualParam}${audioParam}${subParam}`;
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
                    ? `https://vidsrc.pm/embed/tv/${id}/${s}/${e}?autoplay=1${timeParam}${qualParam}`
                    : `https://vidsrc.pm/embed/movie/${id}?autoplay=1${timeParam}${qualParam}`;
                break;
        }

        console.log(`[Player] Loading stream (${this.currentServer}): ${url} (startAt: ${startSec}s, quality: ${this.currentQuality}, audio: ${this.currentAudioTrack})`);
        this.frame.removeAttribute("sandbox");
        this.isVideoPlaying = false;
        this.hideSubtitleOverlay();
        clearTimeout(this.fallbackPlayTimer);

        this.frame.onload = () => {
            if (!this.isOpen) return;
            // Check direct DOM if same-origin
            try {
                const rootDoc = this.frame.contentDocument;
                if (rootDoc) {
                    const v = rootDoc.querySelector("video");
                    if (v) {
                        v.addEventListener("play", () => {
                            this.isVideoPlaying = true;
                            this.isPaused = false;
                            this.playStartTime = Date.now();
                        });
                        v.addEventListener("pause", () => {
                            this.isVideoPlaying = false;
                            this.isPaused = true;
                            this.hideSubtitleOverlay();
                        });
                        v.addEventListener("timeupdate", () => {
                            if (!v.paused) {
                                this.currentTime = Math.floor(v.currentTime);
                                this.isVideoPlaying = true;
                                this.isPaused = false;
                                this.updateSubtitleCue();
                            }
                        });
                        if (!v.paused && v.currentTime > 0) {
                            this.isVideoPlaying = true;
                        }
                    }
                }
            } catch (e) {}

            // For embed providers that don't send cross-origin postMessage:
            // Allow a buffer duration (3.5s) after iframe loads before considering stream started
            clearTimeout(this.fallbackPlayTimer);
            this.fallbackPlayTimer = setTimeout(() => {
                if (this.isOpen && !this.isPaused) {
                    this.isVideoPlaying = true;
                    this.playStartTime = Date.now();
                }
            }, 3500);
        };

        this.frame.src = url;
        this.isPaused = false;

        // Perform initial autoplay and volume application checks
        setTimeout(() => {
            if (this.isOpen) {
                this.triggerInitialPlay();
                this.applyVolumeToStream();
            }
        }, 1200);

        // Additional sync checks at 2.5s and 4s to ensure stream is unmuted
        setTimeout(() => {
            if (this.isOpen) {
                this.applyVolumeToStream();
            }
        }, 2500);

        setTimeout(() => {
            if (this.isOpen) {
                this.applyVolumeToStream();
            }
        }, 4000);
    },

    switchServer(serverKey) {
        this.isVideoPlaying = false;
        this.hideSubtitleOverlay();
        clearTimeout(this.fallbackPlayTimer);

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
        this.showToast(`🔊 Audio Dub: ${displayName} [Applied]`);

        // Carry forward elapsed playback position
        const elapsed = (typeof this.currentTime === "number" && this.currentTime > 0)
            ? this.currentTime
            : Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);
        this.initialStartTime = elapsed;
        this.currentTime = elapsed;
        this.playStartTime = Date.now();

        // If user picked a dubbed track and currently on Server 1,
        // switch to Server 2 (VidLink Pro) which provides multi-audio dub streams
        if (trackKey !== "original" && this.currentServer === "vidsrc_pm") {
            this.currentServer = "vidlink";
            this.updateServerButtons();
        }

        // Always reload stream with selected audio parameters and carried-over time
        this.loadStreamUrl();

        // Smoothly close modal after 350ms
        setTimeout(() => {
            if (this.isAudioSubModalOpen) {
                this.closeAudioSubModal();
            }
        }, 350);
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
                v.muted = this.isMuted;
                v.volume = this.isMuted ? 0 : this.volume;
                v.play().catch(() => {});
            }
        } catch (e) {}

        this.applyVolumeToStream();
    },

    togglePlayPause() {
        const frame = document.getElementById("player-frame");
        if (!frame) return;

        this.isPaused = !this.isPaused;
        if (this.isPaused) {
            this.isVideoPlaying = false;
            this.hideSubtitleOverlay();
        } else {
            this.isVideoPlaying = true;
            this.playStartTime = Date.now();
        }

        // Update center and bottom play/pause icons
        const icon = this.isPaused ? "▶" : "⏸";
        if (this.playIcon) this.playIcon.textContent = icon;
        if (this.barPlayIcon) this.barPlayIcon.textContent = icon;

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

        this.updateTimelineDisplay();
        // 3. Show sleek TV on-screen toast
        this.showToast(this.isPaused ? "⏸ Paused" : "▶ Playing");
    },

    formatTime(s) {
        if (typeof s !== "number" || isNaN(s) || s < 0) return "0:00";
        const h = Math.floor(s / 3600);
        const m = Math.floor((s % 3600) / 60);
        const sec = Math.floor(s % 60);
        if (h > 0) {
            return `${h}:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;
        }
        return `${m}:${sec.toString().padStart(2, '0')}`;
    },

    updateTimelineDisplay() {
        if (!this.isOpen) return;
        const elapsed = (typeof this.currentTime === "number" && this.currentTime > 0)
            ? this.currentTime
            : (this.isPaused ? (this.currentTime || 0) : Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0));

        const dur = this.duration || ((this.currentMedia && this.currentMedia.runtime) ? (this.currentMedia.runtime * 60) : 5400);

        if (this.currentTimeEl) this.currentTimeEl.textContent = this.formatTime(elapsed);
        if (this.totalTimeEl) this.totalTimeEl.textContent = this.formatTime(dur);

        if (this.scrubberFill && dur > 0) {
            const pct = Math.min(100, Math.max(0, (elapsed / dur) * 100));
            this.scrubberFill.style.width = `${pct}%`;
        }
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
        this.updateTimelineDisplay();

        const prefix = deltaSeconds > 0 ? "⏩ +" : "⏪ -";
        this.showToast(`${prefix}${Math.abs(deltaSeconds)}s  (${this.formatTime(target)})`);
    },

    seekTo(targetSeconds) {
        const frame = document.getElementById("player-frame");
        if (!frame) return;

        const maxDur = this.duration || 5400;
        const target = Math.max(0, Math.min(maxDur, targetSeconds));
        this.currentTime = target;
        this.initialStartTime = target;
        this.playStartTime = Date.now();

        if (frame.contentWindow) {
            try {
                frame.contentWindow.postMessage({ action: "seek", time: target }, "*");
                frame.contentWindow.postMessage({ type: "MEDIA_SEEK", time: target }, "*");
            } catch (e) {}
        }

        try {
            const rootDoc = frame.contentDocument;
            if (rootDoc) {
                const v = rootDoc.querySelector("video");
                if (v) v.currentTime = target;
            }
        } catch (e) {}

        this.updateSubtitleCue();
        this.updateTimelineDisplay();
        this.showToast(`⏱ ${this.formatTime(target)}`);
    },

    setVolume(level) {
        this.volume = Math.max(0, Math.min(1.0, Math.round(level * 100) / 100));
        this.isMuted = (this.volume === 0);
        try {
            localStorage.setItem("aura_player_volume", this.volume.toString());
        } catch (e) {}
        this.applyVolumeToStream();
        this.updateVolumeDisplay();
        const pct = Math.round(this.volume * 100);
        this.showToast(this.isMuted ? "🔇 Audio Muted" : `🔊 Volume: ${pct}%`);
    },

    toggleMute() {
        this.isMuted = !this.isMuted;
        if (!this.isMuted && this.volume <= 0.05) {
            this.volume = 1.0;
        }
        try {
            localStorage.setItem("aura_player_volume", this.volume.toString());
        } catch (e) {}
        this.applyVolumeToStream();
        this.updateVolumeDisplay();
        const pct = Math.round(this.volume * 100);
        this.showToast(this.isMuted ? "🔇 Audio Muted" : `🔊 Audio Unmuted (${pct}%)`);
    },

    unmute() {
        this.isMuted = false;
        if (this.volume <= 0.05) {
            this.volume = 1.0;
        }
        try {
            localStorage.setItem("aura_player_volume", this.volume.toString());
        } catch (e) {}
        this.applyVolumeToStream();
        this.updateVolumeDisplay();
        const pct = Math.round(this.volume * 100);
        this.showToast(`🔊 Audio Unmuted (${pct}%)`);
    },

    mute() {
        this.isMuted = true;
        this.applyVolumeToStream();
        this.updateVolumeDisplay();
        this.showToast("🔇 Audio Muted");
    },

    adjustTvVolume(delta) {
        if (window.TizenBridge && typeof window.TizenBridge.setTvVolumeUp === "function") {
            const res = delta > 0 ? window.TizenBridge.setTvVolumeUp() : window.TizenBridge.setTvVolumeDown();
            if (res !== null) {
                this.updateVolumeDisplay();
                return;
            }
        }
        // Fallback for non-Tizen
        this.adjustVolume(delta * 0.1);
    },

    adjustVolume(delta) {
        if (this.isMuted) {
            this.isMuted = false;
        }
        this.volume = Math.max(0, Math.min(1.0, Math.round((this.volume + delta) * 10) / 10));
        if (this.volume === 0) {
            this.isMuted = true;
        }
        try {
            localStorage.setItem("aura_player_volume", this.volume.toString());
        } catch (e) {}
        this.applyVolumeToStream();
        this.updateVolumeDisplay();
        const pct = Math.round(this.volume * 100);
        this.showToast(this.isMuted ? "🔇 Audio Muted" : `🔊 Volume: ${pct}%`);
    },

    applyVolumeToStream() {
        const frame = document.getElementById("player-frame");
        if (!frame) return;

        const targetVol = this.isMuted ? 0 : this.volume;

        // 1. Send all recognized postMessage volume & mute command structures
        if (frame.contentWindow) {
            const messages = [
                { action: "setVolume", volume: targetVol },
                { type: "SET_VOLUME", volume: targetVol },
                { action: "volume", value: targetVol },
                { type: "volume", value: targetVol },
                { action: this.isMuted ? "mute" : "unmute" },
                { type: this.isMuted ? "MUTE" : "UNMUTE" },
                { action: "setMuted", muted: this.isMuted },
                { type: "SET_MUTED", muted: this.isMuted },
                { event: "command", func: "setVolume", args: [Math.round(targetVol * 100)] },
                { event: "command", func: this.isMuted ? "mute" : "unMute", args: [] }
            ];
            messages.forEach(msg => {
                try { frame.contentWindow.postMessage(msg, "*"); } catch (e) {}
                try { frame.contentWindow.postMessage(JSON.stringify(msg), "*"); } catch (e) {}
            });
        }

        // 2. Direct DOM manipulation if frame is accessible
        try {
            const rootDoc = frame.contentDocument;
            if (rootDoc) {
                const videos = rootDoc.querySelectorAll("video");
                videos.forEach(v => {
                    v.muted = this.isMuted;
                    v.volume = targetVol;
                });
            }
        } catch (e) {}
    },

    updateVolumeDisplay() {
        // A. Top HUD Volume Button
        if (!this.volumeBtnEl) {
            this.volumeBtnEl = document.getElementById("player-volume-btn");
        }
        if (this.volumeBtnEl) {
            const pct = Math.round(this.volume * 100);
            if (this.isMuted || pct === 0) {
                this.volumeBtnEl.innerHTML = "🔇 Muted";
            } else if (this.volume >= 0.75) {
                this.volumeBtnEl.innerHTML = `🔊 ${pct}%`;
            } else if (this.volume >= 0.3) {
                this.volumeBtnEl.innerHTML = `🔉 ${pct}%`;
            } else {
                this.volumeBtnEl.innerHTML = `🔈 ${pct}%`;
            }
        }

        // B. Audio Modal Volume Buttons
        const btnTvLabel = document.getElementById("btn-vol-tv-label");
        const btn100 = document.getElementById("btn-vol-100");
        const btnMute = document.getElementById("btn-vol-mute");
        const muteIcon = document.getElementById("btn-vol-mute-icon");
        const muteLabel = document.getElementById("btn-vol-mute-label");

        if (btn100) btn100.classList.remove("active");
        if (btnMute) btnMute.classList.remove("active");

        if (window.TizenBridge && typeof window.TizenBridge.getTvVolume === "function") {
            const tvVol = window.TizenBridge.getTvVolume();
            if (btnTvLabel && tvVol !== null) {
                btnTvLabel.textContent = `TV Vol + (${tvVol})`;
            }
        }

        if (this.isMuted || this.volume === 0) {
            if (btnMute) btnMute.classList.add("active");
            if (muteIcon) muteIcon.textContent = "🔊";
            if (muteLabel) muteLabel.textContent = "Unmute";
        } else {
            if (muteIcon) muteIcon.textContent = "🔇";
            if (muteLabel) muteLabel.textContent = "Mute";
            if (this.volume >= 0.95 && btn100) {
                btn100.classList.add("active");
            }
        }

        // C. Floating Unmute Banner
        if (!this.unmuteBannerEl) {
            this.unmuteBannerEl = document.getElementById("player-unmute-banner");
        }
        if (this.unmuteBannerEl) {
            if (this.isMuted && this.isOpen) {
                this.unmuteBannerEl.classList.remove("hidden");
            } else {
                this.unmuteBannerEl.classList.add("hidden");
            }
        }
    },

    toggleFullscreen() {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen().catch(() => {});
        } else {
            document.exitFullscreen().catch(() => {});
        }
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
                const isFocusedInPlayer = window.Navigation && window.Navigation.currentFocus &&
                    (window.Navigation.currentFocus.id === "player-back-btn" || 
                     window.Navigation.currentFocus.id === "player-audio-sub-btn" ||
                     window.Navigation.currentFocus.id === "player-quality-btn" ||
                     window.Navigation.currentFocus.classList.contains("server-btn") ||
                     (window.Navigation.currentFocus.closest && window.Navigation.currentFocus.closest(".player-hud")));
                if (!isFocusedInPlayer) {
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

        // Remove focus styling from player controls if unfocused
        const isFocusedInPlayer = window.Navigation && window.Navigation.currentFocus &&
            (window.Navigation.currentFocus.id === "player-back-btn" || 
             window.Navigation.currentFocus.id === "player-audio-sub-btn" ||
             window.Navigation.currentFocus.id === "player-quality-btn" ||
             window.Navigation.currentFocus.classList.contains("server-btn") ||
             (window.Navigation.currentFocus.closest && window.Navigation.currentFocus.closest(".player-hud")));

        if (isFocusedInPlayer && window.Navigation) {
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

    closeAudioSubModal(returnToHud = false) {
        this.isAudioSubModalOpen = false;
        if (this.audioSubModal) {
            this.audioSubModal.classList.add("hidden");
        }
        if (returnToHud) {
            this.showHudTemporarily();
            const targetBtn = document.getElementById("player-audio-sub-btn") || document.getElementById("player-back-btn");
            if (targetBtn && window.Navigation) {
                window.Navigation.setFocus(targetBtn);
            }
        } else {
            this.hideHud();
        }
    },

    switchAudioSubTab(tabName, keepFocusOnTab = false) {
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
            if (!keepFocusOnTab && window.Navigation) {
                const activeCard = document.querySelector(".quality-card.active") || document.querySelector(".quality-card") || qualityTab;
                if (activeCard) window.Navigation.setFocus(activeCard);
            }
        } else if (tabName === "audio") {
            if (audioTab) audioTab.classList.add("active");
            if (audioContent) audioContent.classList.remove("hidden");
            if (!keepFocusOnTab && window.Navigation) {
                const activeCard = document.querySelector(".audio-lang-btn.active") || document.querySelector(".audio-server-card.active") || audioTab;
                if (activeCard) window.Navigation.setFocus(activeCard);
            }
        } else {
            if (subTab) subTab.classList.add("active");
            if (subContent) subContent.classList.remove("hidden");
            if (!keepFocusOnTab && window.Navigation) {
                const activeBtn = document.querySelector(".sub-lang-btn.active") || document.querySelector(".sub-lang-btn") || subTab;
                if (activeBtn) window.Navigation.setFocus(activeBtn);
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
        this.showToast(`⚙ Resolution: ${displayName} [Applied]`);

        // Update active class on quality cards immediately
        const qualityKeys = ["auto", "2160", "1080", "720", "480"];
        qualityKeys.forEach(k => {
            const cardEl = document.getElementById(`quality-card-${k}`);
            const statusEl = document.getElementById(`status-quality-${k}`);
            const isActive = (k === qualityKey);
            if (cardEl) {
                if (isActive) cardEl.classList.add("active");
                else cardEl.classList.remove("active");
            }
            if (statusEl) {
                statusEl.textContent = isActive ? "Active" : "";
            }
        });

        // Try postMessage to active stream player
        if (this.frame && this.frame.contentWindow) {
            try {
                this.frame.contentWindow.postMessage({ action: "setQuality", quality: qualityKey }, "*");
                this.frame.contentWindow.postMessage({ type: "SET_QUALITY", quality: qualityKey }, "*");
                this.frame.contentWindow.postMessage({ quality: qualityKey }, "*");
            } catch (e) {}
        }

        // Seamlessly carry over elapsed playback position
        this.isVideoPlaying = false;
        this.hideSubtitleOverlay();
        clearTimeout(this.fallbackPlayTimer);
        const elapsed = (typeof this.currentTime === "number" && this.currentTime > 0)
            ? this.currentTime
            : Math.floor((Date.now() - this.playStartTime) / 1000) + (this.initialStartTime || 0);
        this.initialStartTime = elapsed;
        this.currentTime = elapsed;
        this.playStartTime = Date.now();

        // Server routing logic for resolution:
        // 4K UHD: Server 1 / Server 4 provide UHD master bitrate
        // 1080p Full HD: Server 2 (VidLink Pro) provides high-FPS 1080p
        if (qualityKey === "1080" && this.currentServer === "vidsrc_pm") {
            this.currentServer = "vidlink";
            this.updateServerButtons();
        } else if (qualityKey === "2160" && this.currentServer === "vidlink") {
            this.currentServer = "vidsrc_pm";
            this.updateServerButtons();
        }

        // Always reload stream URL with selected quality parameter and preserved timestamp
        this.loadStreamUrl();

        // Auto close modal smoothly after 400ms so viewer sees their chosen stream
        setTimeout(() => {
            if (this.isAudioSubModalOpen) {
                this.closeAudioSubModal();
            }
        }, 400);
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
            this.activeSubtitleUrl = null;
            this.hideSubtitleOverlay();
            const subBtn = document.getElementById("player-audio-sub-btn");
            if (subBtn) subBtn.textContent = "💬 Audio & Subtitles";
            this.showToast("Subtitles: Off");
            setTimeout(() => {
                if (this.isAudioSubModalOpen) {
                    this.closeAudioSubModal();
                }
            }, 400);
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
            "es": "Spanish (Español)",
            "fr": "French (Français)",
            "de": "German (Deutsch)",
            "hi": "Hindi (हिंदी)",
            "ar": "Arabic (العربية)",
            "pt": "Portuguese (Português)",
            "it": "Italian (Italiano)",
            "ja": "Japanese (日本語)"
        };
        return names[code] || code.toUpperCase();
    },

    async fetchSubtitles(lang) {
        if (!this.currentMedia) return;
        const langName = this.getLanguageDisplayName(lang);
        this.showToast(`💬 Searching ${langName} Subtitles...`);

        const imdbId = await this.getImdbId();
        if (!imdbId) {
            console.warn("[Player] No IMDb ID available for subtitles");
            this.showToast(`💬 Subtitles: Metadata not found`);
            return;
        }

        const isTv = this.currentMedia.media_type === "tv" || !!this.currentMedia.season;
        const s = this.currentMedia.season || 1;
        const e = this.currentMedia.episode || 1;

        // Query Stremio OpenSubtitles v3 working endpoint
        const endpoint = isTv
            ? `https://opensubtitles-v3.strem.io/subtitles/series/${imdbId}:${s}:${e}.json`
            : `https://opensubtitles-v3.strem.io/subtitles/movie/${imdbId}.json`;

        console.log(`[Player] Querying subtitles from ${endpoint}`);
        try {
            const res = await fetch(endpoint);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            const subtitles = data.subtitles || [];

            if (!subtitles.length) {
                this.showToast(`💬 No subtitles found for this title`);
                return;
            }

            // ISO language code match table
            const langMap = {
                "ta": ["tam", "ta", "tamil"],
                "en": ["eng", "en", "english"],
                "hi": ["hin", "hi", "hindi"],
                "te": ["tel", "te", "telugu"],
                "ml": ["mal", "ml", "malayalam"],
                "kn": ["kan", "kn", "kannada"],
                "es": ["spa", "es", "spanish"],
                "fr": ["fre", "fra", "fr", "french"],
                "de": ["ger", "deu", "de", "german"],
                "ar": ["ara", "ar", "arabic"],
                "pt": ["por", "pob", "pt", "portuguese"],
                "it": ["ita", "it", "italian"],
                "ja": ["jpn", "ja", "japanese"]
            };

            const searchCodes = langMap[lang] || [lang];
            let matchedSub = subtitles.find(s => {
                const sLang = (s.lang || "").toLowerCase();
                const sName = (s.subtitleFileName || "").toLowerCase();
                return searchCodes.some(c => sLang === c || sName.includes(c));
            });

            let fallbackToEnglish = false;
            if (!matchedSub) {
                // If specific regional language not found, fallback to English subtitle
                matchedSub = subtitles.find(s => s.lang === "eng" || s.lang === "en");
                fallbackToEnglish = true;
            }

            if (!matchedSub || !matchedSub.url) {
                this.showToast(`💬 Subtitles not available for this title`);
                return;
            }

            console.log(`[Player] Downloading subtitle file: ${matchedSub.url}`);
            const subRes = await fetch(matchedSub.url);
            if (!subRes.ok) throw new Error(`Subtitle download failed: ${subRes.status}`);
            const subText = await subRes.text();

            this.subtitleCues = this.parseSubtitleText(subText);
            this.activeSubtitleUrl = matchedSub.url;

            // Update HUD action button label
            const subBtn = document.getElementById("player-audio-sub-btn");
            if (subBtn) {
                subBtn.textContent = `💬 Subs: ${fallbackToEnglish ? "English" : langName}`;
            }

            if (fallbackToEnglish) {
                this.showToast(`💬 ${langName} unavailable; English loaded (${this.subtitleCues.length} cues)`);
            } else {
                this.showToast(`💬 ${langName} Subtitles Enabled (${this.subtitleCues.length} cues)`);
            }

            this.updateSubtitleCue();

            // Smoothly close modal after selecting subtitles
            setTimeout(() => {
                if (this.isAudioSubModalOpen) {
                    this.closeAudioSubModal();
                }
            }, 600);
        } catch (err) {
            console.error("[Player] Subtitle fetch notice:", err);
            this.showToast(`💬 Subtitle fetch error`);
        }
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
        if (!this.isOpen || !this.isVideoPlaying || this.isPaused || this.currentSubtitle === "off" || !this.subtitleCues.length) {
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
        this.isPaused = true;
        this.isVideoPlaying = false;
        this.isAudioSubModalOpen = false;
        clearTimeout(this.fallbackPlayTimer);
        clearTimeout(this.hideTimeout);
        clearTimeout(this.toastTimeout);
        clearInterval(this.progressInterval);
        clearInterval(this.subtitleInterval);
        clearInterval(this.timelineInterval);
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
