// Aura Play - Cinema Streaming Player
const Player = {
    isOpen: false,
    currentMedia: null,
    currentServer: "vidlink",
    hideTimeout: null,

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
    },

    play(media, season = null, episode = null) {
        this.currentMedia = Object.assign({}, media, { season: season, episode: episode });
        this.isOpen = true;

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

    loadStreamUrl() {
        const { id, media_type, season, episode } = this.currentMedia;
        const isTv = media_type === "tv" || !!season;

        let url = "";
        const s = season || 1;
        const e = episode || 1;

        switch (this.currentServer) {
            case "vidsrc_cc":
                url = isTv 
                    ? `https://vidsrc.cc/v2/embed/tv/${id}/${s}/${e}`
                    : `https://vidsrc.cc/v2/embed/movie/${id}`;
                break;
            case "vidsrc_xyz":
                url = isTv 
                    ? `https://vidsrc.xyz/embed/tv?tmdb=${id}&season=${s}&episode=${e}`
                    : `https://vidsrc.xyz/embed/movie?tmdb=${id}`;
                break;
            case "autoembed":
                url = isTv 
                    ? `https://autoembed.co/tv/tmdb/${id}-${s}-${e}`
                    : `https://autoembed.co/movie/tmdb/${id}`;
                break;
            case "vidlink":
            default:
                url = isTv 
                    ? `https://vidlink.pro/tv/${id}/${s}/${e}?autoplay=true&primaryColor=E50914`
                    : `https://vidlink.pro/movie/${id}?autoplay=true&primaryColor=E50914`;
                break;
        }

        console.log(`[Player] Loading stream (${this.currentServer}): ${url}`);
        this.frame.src = url;
    },

    switchServer(serverKey) {
        this.currentServer = serverKey;
        this.updateServerButtons();
        this.loadStreamUrl();
        this.showHudTemporarily();
    },

    updateServerButtons() {
        const serverMap = {
            "vidlink": document.getElementById("server-btn-vidlink"),
            "vidsrc_cc": document.getElementById("server-btn-vidsrc"),
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
        this.frame.src = "about:blank";
        this.container.classList.remove("active");
        this.container.classList.add("hidden");

        if (window.TizenBridge) {
            window.TizenBridge.preventScreenSaver(false);
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
