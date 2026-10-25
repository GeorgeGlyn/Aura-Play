// Samsung Tizen TV Bridge & Hardware Integration
const TizenBridge = {
    isTizen: false,

    init() {
        if (typeof window.tizen !== "undefined" || window.location.search.includes("tv=1")) {
            this.isTizen = true;
            document.body.classList.add("tizen-tv");
            console.log("[TizenBridge] Running on Samsung Tizen TV OS (or TV simulation mode)");
            this.registerTvKeys();
        } else {
            console.log("[TizenBridge] Running in standard Web / Browser environment");
        }

        // Global back key handler (supports both Tizen Back 10009 and Desktop/Browser Escape 27 / Backspace)
        window.addEventListener("keydown", (e) => this.handleHardwareKey(e));
    },

    registerTvKeys() {
        try {
            const keysToRegister = [
                "MediaPlayPause",
                "MediaPlay",
                "MediaPause",
                "MediaFastForward",
                "MediaRewind",
                "MediaStop"
            ];
            keysToRegister.forEach(keyName => {
                try {
                    window.tizen.tvinputdevice.registerKey(keyName);
                } catch (e) {
                    // Ignore if some keys not supported on certain models
                }
            });
        } catch (err) {
            console.warn("[TizenBridge] Failed to register TV keys:", err);
        }
    },

    handleHardwareKey(e) {
        const keyCode = e.keyCode;

        // Tizen Return/Back Key (10009) or Browser Escape (27)
        if (keyCode === 10009 || keyCode === 27) {
            e.preventDefault();
            e.stopPropagation();
            this.handleBackNavigation();
            return;
        }

        // Media keys for player
        if (keyCode === 415 || keyCode === 19 || keyCode === 10252) { // Play / Pause / PlayPause
            e.preventDefault();
            if (window.NativePlayer && window.NativePlayer.isOpen) {
                window.NativePlayer.togglePlayPause();
            } else if (window.Player && window.Player.isOpen) {
                window.Player.togglePlayPause();
            }
        } else if (keyCode === 417) { // Fast Forward
            e.preventDefault();
            if (window.NativePlayer && window.NativePlayer.isOpen) {
                window.NativePlayer.seek(10);
            } else if (window.Player && window.Player.isOpen) {
                window.Player.seek(10);
            }
        } else if (keyCode === 412) { // Rewind
            e.preventDefault();
            if (window.NativePlayer && window.NativePlayer.isOpen) {
                window.NativePlayer.seek(-10);
            } else if (window.Player && window.Player.isOpen) {
                window.Player.seek(-10);
            }
        }
    },

    handleBackNavigation() {
        // 0. If native offline video player is open:
        if (window.NativePlayer && window.NativePlayer.isOpen) {
            // If controls HUD is showing, first press simply hides controls
            if (window.NativePlayer.isHudVisible) {
                window.NativePlayer.hideHud();
                return;
            }
            // If controls already hidden, exit playback
            window.NativePlayer.close();
            return;
        }

        // 0. If Delete Download modal is open, close it
        const deleteModal = document.getElementById("delete-download-modal");
        if (deleteModal && !deleteModal.classList.contains("hidden")) {
            if (window.DownloadUI) window.DownloadUI.closeDeleteModal();
            return;
        }

        // 1. If embed video player is open:
        if (window.Player && window.Player.isOpen) {
            if (window.Player.isAudioSubModalOpen) {
                window.Player.closeAudioSubModal();
                return;
            }
            window.Player.close();
            return;
        }

        // 2. If quality picker is open, close it
        const picker = document.getElementById("quality-picker-overlay");
        if (picker) { picker.remove(); return; }

        // 3. If details modal is open, close it
        if (window.App && window.App.isModalOpen) {
            window.App.closeModal();
            return;
        }

        // 4. If currently in search, watchlist, or downloads, return to home
        if (window.App && window.App.currentTab !== "home") {
            window.App.switchTab("home");
            return;
        }

        // 5. If on Home, confirm exit on Tizen
        if (this.isTizen) {
            try {
                window.tizen.application.getCurrentApplication().exit();
            } catch (err) {
                console.log("[TizenBridge] Exit called");
            }
        }
    },

    preventScreenSaver(enable) {
        if (!this.isTizen) {
            return;
        }
        try {
            if (window.webapis && window.webapis.appcommon) {
                const state = enable ? 
                    window.webapis.appcommon.AppCommonScreenSaverState.SCREEN_SAVER_OFF : 
                    window.webapis.appcommon.AppCommonScreenSaverState.SCREEN_SAVER_ON;
                window.webapis.appcommon.setScreenSaver(state);
            }
        } catch (e) {
            // Ignore
        }
    }
};

window.TizenBridge = TizenBridge;
