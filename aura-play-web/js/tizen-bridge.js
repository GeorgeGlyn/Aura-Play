// Samsung Tizen TV Bridge & Hardware Integration
const TizenBridge = {
    isTizen: false,

    init() {
        if (typeof window.tizen !== "undefined" || window.location.search.includes("tv=1")) {
            this.isTizen = true;
            document.body.classList.add("tizen-tv");
            console.log("[TizenBridge] Running on Samsung Tizen TV OS (or TV simulation mode)");
            this.registerTvKeys();
            this.initTvAudioControl();
        } else {
            console.log("[TizenBridge] Running in standard Web / Browser environment");
        }

        // Global back key handler (supports both Tizen Back 10009 and Desktop/Browser Escape 27 / Backspace)
        window.addEventListener("keydown", (e) => this.handleHardwareKey(e));
    },

    initTvAudioControl() {
        if (typeof window.tizen !== "undefined" && window.tizen.tvaudiocontrol) {
            try {
                window.tizen.tvaudiocontrol.setVolumeChangeListener({
                    onchanged: (volume) => {
                        console.log("[TizenBridge] TV hardware volume changed:", volume);
                        // When user increases/changes TV volume via remote, ensure video stream is unmuted
                        // and outputting 100% full digital volume so TV speakers give full power!
                        if (window.Player && window.Player.isOpen) {
                            if (window.Player.isMuted) {
                                window.Player.unmute();
                            }
                            window.Player.setVolume(1.0);
                        }
                    }
                });
                console.log("[TizenBridge] TV Audio Control listener attached. Current TV volume:", window.tizen.tvaudiocontrol.getVolume());
            } catch (err) {
                console.warn("[TizenBridge] TV audio volume listener could not be registered:", err);
            }
        }
    },

    getTvVolume() {
        if (typeof window.tizen !== "undefined" && window.tizen.tvaudiocontrol) {
            try {
                return window.tizen.tvaudiocontrol.getVolume();
            } catch (e) {}
        }
        return null;
    },

    setTvVolumeUp() {
        if (typeof window.tizen !== "undefined" && window.tizen.tvaudiocontrol) {
            try {
                window.tizen.tvaudiocontrol.setVolumeUp();
                const newVol = window.tizen.tvaudiocontrol.getVolume();
                if (window.Player && window.Player.isOpen) {
                    if (window.Player.isMuted) window.Player.unmute();
                    window.Player.setVolume(1.0);
                    window.Player.showToast(`🔊 TV Volume: ${newVol}`);
                }
                return newVol;
            } catch (e) {
                console.warn("[TizenBridge] setVolumeUp failed:", e);
            }
        }
        return null;
    },

    setTvVolumeDown() {
        if (typeof window.tizen !== "undefined" && window.tizen.tvaudiocontrol) {
            try {
                window.tizen.tvaudiocontrol.setVolumeDown();
                const newVol = window.tizen.tvaudiocontrol.getVolume();
                if (window.Player && window.Player.isOpen) {
                    window.Player.showToast(`🔉 TV Volume: ${newVol}`);
                }
                return newVol;
            } catch (e) {
                console.warn("[TizenBridge] setVolumeDown failed:", e);
            }
        }
        return null;
    },

    setTvMute(isMuted) {
        if (typeof window.tizen !== "undefined" && window.tizen.tvaudiocontrol) {
            try {
                const target = (typeof isMuted === "boolean") ? isMuted : !window.tizen.tvaudiocontrol.isMute();
                window.tizen.tvaudiocontrol.setMute(target);
                return target;
            } catch (e) {}
        }
        return null;
    },

    registerTvKeys() {
        try {
            // Note: VolumeUp, VolumeDown, and VolumeMute are deliberately NOT registered here!
            // When NOT registered, Samsung Tizen TV OS handles the physical remote volume buttons natively,
            // adjusting the TV's real hardware speaker volume (0 to 100), displaying the native TV volume HUD,
            // and controlling HDMI-eARC / Soundbars natively!
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
            e.stopImmediatePropagation();
            this.handleBackNavigation();
            return;
        }

        // Volume Mute key (Tizen 449, Browser 173, 'm' / 'M', AudioVolumeMute)
        if (keyCode === 449 || keyCode === 173 || e.key === "m" || e.key === "M" || e.key === "AudioVolumeMute") {
            if (window.Player && window.Player.isOpen) {
                e.preventDefault();
                window.Player.toggleMute();
                this.setTvMute(window.Player.isMuted);
                return;
            }
        }

        // Volume Up key (Tizen 447, Browser 175, AudioVolumeUp)
        if (keyCode === 447 || keyCode === 175 || e.key === "AudioVolumeUp") {
            const tvVol = this.setTvVolumeUp();
            if (tvVol !== null) {
                e.preventDefault();
                return;
            }
            if (window.Player && window.Player.isOpen) {
                window.Player.adjustVolume(0.1);
            }
            return;
        }

        // Volume Down key (Tizen 448, Browser 174, AudioVolumeDown)
        if (keyCode === 448 || keyCode === 174 || e.key === "AudioVolumeDown") {
            const tvVol = this.setTvVolumeDown();
            if (tvVol !== null) {
                e.preventDefault();
                return;
            }
            if (window.Player && window.Player.isOpen) {
                window.Player.adjustVolume(-0.1);
            }
            return;
        }

        // Media keys for player
        if (keyCode === 415 || keyCode === 19 || keyCode === 10252) { // Play / Pause / PlayPause
            e.preventDefault();
            if (window.Player && window.Player.isOpen && window.Player.isAudioSubModalOpen) {
                if (window.Navigation) {
                    window.Navigation.activateAudioSubModalElement();
                }
                return;
            }
            if (window.NativePlayer && window.NativePlayer.isOpen) {
                window.NativePlayer.togglePlayPause();
            } else if (window.Player && window.Player.isOpen) {
                window.Player.togglePlayPause();
            }
        } else if (keyCode === 417) { // Fast Forward
            if (window.Player && window.Player.isAudioSubModalOpen) return;
            e.preventDefault();
            if (window.NativePlayer && window.NativePlayer.isOpen) {
                window.NativePlayer.seek(10);
            } else if (window.Player && window.Player.isOpen) {
                window.Player.seek(10);
            }
        } else if (keyCode === 412) { // Rewind
            if (window.Player && window.Player.isAudioSubModalOpen) return;
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
            // 1a. If Audio, Subtitles & Quality Modal is open, close it
            const audioSubModal = document.getElementById("audio-sub-modal");
            const isModalOpen = window.Player.isAudioSubModalOpen || (audioSubModal && !audioSubModal.classList.contains("hidden"));
            if (isModalOpen) {
                window.Player.closeAudioSubModal();
                window.Player.hideHud();
                return;
            }

            // 1b. If Quality Picker overlay is open, close it
            const picker = document.getElementById("quality-picker-overlay");
            if (picker) {
                picker.remove();
                return;
            }

            // 1c. If Player HUD / Controls / Top Menu is visible, hide it and resume clean fullscreen playback
            if (window.Player.isHudVisible || (window.Navigation && window.Navigation.currentFocus && window.Navigation.currentFocus.closest && window.Navigation.currentFocus.closest(".player-hud"))) {
                window.Player.hideHud();
                return;
            }

            // 1d. Only when all menus and HUD are already closed, exit the video player
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
