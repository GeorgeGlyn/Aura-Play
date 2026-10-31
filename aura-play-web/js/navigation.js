// Spatial D-Pad & Remote Navigation Engine for TV and Touch
const Navigation = {
    currentFocus: null,
    isTvMode: true,

    init() {
        window.addEventListener("keydown", (e) => {
            this.handleKeyDown(e);
        });

        // When mouse moves or touch occurs, keep focus updated smoothly
        document.addEventListener("pointerdown", (e) => {
            const focusable = e.target.closest(".focusable");
            if (focusable) {
                this.setFocus(focusable, false);
            }
        });
    },

    setFocus(element, scrollIntoView = true) {
        if (!element) {
            return;
        }
        if (this.currentFocus) {
            this.currentFocus.classList.remove("focused");
        }
        this.currentFocus = element;
        this.currentFocus.classList.add("focused");
        this.currentFocus.focus();

        if (scrollIntoView) {
            this.ensureVisible(element);
        }
    },

    ensureVisible(element) {
        // Horizontal scroll inside row carousels
        const row = element.closest(".carousel-items");
        if (row) {
            const elLeft = element.offsetLeft;
            const elWidth = element.offsetWidth;
            const rowWidth = row.offsetWidth;
            const scrollTarget = elLeft - (rowWidth / 2) + (elWidth / 2);
            row.scrollTo({
                left: Math.max(0, scrollTarget),
                behavior: "smooth"
            });
        }

        // Vertical scroll in viewport
        element.scrollIntoView({
            behavior: "smooth",
            block: "nearest",
            inline: "nearest"
        });
    },

    handleKeyDown(e) {
        const keyCode = e.keyCode;

        // If Quality Picker is open
        const picker = document.getElementById("quality-picker-overlay");
        if (picker) {
            if (keyCode === 27) { // Escape / Back
                e.preventDefault();
                picker.remove();
                const dlBtn = document.getElementById("modal-download-btn");
                if (dlBtn) this.setFocus(dlBtn);
                return;
            }
            if (keyCode === 13) { // Enter / OK
                e.preventDefault();
                if (this.currentFocus) this.currentFocus.click();
                return;
            }
            if ([37, 38, 39, 40].includes(keyCode)) {
                e.preventDefault();
                this.moveFocus(keyCode);
                return;
            }
            return;
        }

        // If Delete Download Confirmation Modal is open
        const deleteModal = document.getElementById("delete-download-modal");
        if (deleteModal && !deleteModal.classList.contains("hidden")) {
            if (keyCode === 27) { // Escape / Back
                e.preventDefault();
                if (window.DownloadUI) window.DownloadUI.closeDeleteModal();
                return;
            }
            if (keyCode === 13) { // Enter / OK
                e.preventDefault();
                if (this.currentFocus) this.currentFocus.click();
                return;
            }
            if (keyCode === 37) { // Left -> Focus Cancel
                e.preventDefault();
                const cancelBtn = document.getElementById("delete-download-cancel-btn");
                if (cancelBtn) this.setFocus(cancelBtn);
                return;
            }
            if (keyCode === 39) { // Right -> Focus Remove
                e.preventDefault();
                const confirmBtn = document.getElementById("delete-download-confirm-btn");
                if (confirmBtn) this.setFocus(confirmBtn);
                return;
            }
            return;
        }

        // Wake and refresh online cinema stream player HUD on any key
        if (window.Player && window.Player.isOpen) {
            window.Player.showHudTemporarily();
        }

        // Special D-pad handling when NativePlayer is playing offline video
        if (window.NativePlayer && window.NativePlayer.isOpen) {
            if (!window.NativePlayer.isHudVisible) {
                // If controls are hidden, remote keys perform immediate video actions
                if (keyCode === 13) { // Center / OK -> Toggle Play/Pause & reveal HUD
                    e.preventDefault();
                    window.NativePlayer.togglePlayPause();
                    return;
                }
                if (keyCode === 37) { // Left -> Seek -10s
                    e.preventDefault();
                    window.NativePlayer.seek(-10);
                    return;
                }
                if (keyCode === 39) { // Right -> Seek +10s
                    e.preventDefault();
                    window.NativePlayer.seek(10);
                    return;
                }
                if (keyCode === 38 || keyCode === 40) { // Up / Down -> Wake controls
                    e.preventDefault();
                    window.NativePlayer.showHud();
                    const playBtn = document.getElementById("native-play-btn");
                    if (playBtn) {
                        this.setFocus(playBtn);
                    }
                    return;
                }
            } else {
                // When HUD is visible, any key resets the HUD auto-hide timer
                window.NativePlayer.showHud();
            }
        }

        // Special D-pad handling when online Cinema Stream Player is open
        if (window.Player && window.Player.isOpen) {
            const audioSubModal = document.getElementById("audio-sub-modal");
            const isAudioModalOpen = window.Player.isAudioSubModalOpen || (audioSubModal && !audioSubModal.classList.contains("hidden"));

            // When Audio & Subtitles / Quality TV Modal is open
            if (isAudioModalOpen) {
                if (keyCode === 27 || keyCode === 10009) { // Escape / Back
                    e.preventDefault();
                    window.Player.closeAudioSubModal();
                    window.Player.hideHud();
                    return;
                }
                if (keyCode === 13 || keyCode === 32 || keyCode === 65379 || keyCode === 10252) { // Enter / OK / Space / TV Select
                    e.preventDefault();
                    this.activateAudioSubModalElement();
                    return;
                }
                if ([37, 38, 39, 40].includes(keyCode)) {
                    e.preventDefault();
                    this.navigateAudioSubModal(keyCode);
                    return;
                }
                return;
            }

            // If Escape or Back key is pressed while player is open
            if (keyCode === 27 || keyCode === 10009) {
                e.preventDefault();
                // If Quality Picker overlay is open, remove it
                const picker = document.getElementById("quality-picker-overlay");
                if (picker) {
                    picker.remove();
                    return;
                }
                // If Player HUD / Controls / Top Menu is open, hide it and stay in fullscreen video!
                const isFocusedInHud = this.currentFocus && (
                    this.currentFocus.id === "player-back-btn" || 
                    this.currentFocus.id === "player-audio-sub-btn" ||
                    this.currentFocus.id === "player-quality-btn" ||
                    this.currentFocus.id === "player-volume-btn" ||
                    this.currentFocus.id === "player-unmute-banner" ||
                    this.currentFocus.classList.contains("server-btn") ||
                    (this.currentFocus.closest && this.currentFocus.closest(".player-hud") !== null)
                );
                if (window.Player.isHudVisible || isFocusedInHud) {
                    window.Player.hideHud();
                    return;
                }
                // If HUD is already hidden, exit player
                window.Player.close();
                return;
            }

            const isTopHud = this.currentFocus && (
                this.currentFocus.id === "player-back-btn" || 
                this.currentFocus.id === "player-audio-sub-btn" ||
                this.currentFocus.id === "player-quality-btn" ||
                this.currentFocus.id === "player-volume-btn" ||
                this.currentFocus.id === "player-unmute-banner" ||
                this.currentFocus.classList.contains("server-btn") ||
                (this.currentFocus.closest && this.currentFocus.closest(".player-hud") !== null)
            );

            if (keyCode === 13 || keyCode === 32) { // OK / Enter / Space
                e.preventDefault();
                if (isTopHud && this.currentFocus && typeof this.currentFocus.click === "function") {
                    this.currentFocus.click();
                    return;
                }
                window.Player.togglePlayPause();
                return;
            }

            if (keyCode === 38) { // Up -> Show HUD and focus buttons
                e.preventDefault();
                window.Player.showHudTemporarily(8000);
                if (!isTopHud) {
                    const topBtn = document.getElementById("player-audio-sub-btn") || 
                                   document.getElementById("player-quality-btn") || 
                                   document.getElementById("player-back-btn");
                    if (topBtn) this.setFocus(topBtn);
                }
                return;
            } else if (keyCode === 40) { // Down -> Hide HUD and resume pure fullscreen control
                e.preventDefault();
                window.Player.hideHud();
                return;
            } else if (keyCode === 37) { // Left -> Navigate Top HUD or Seek -10s
                e.preventDefault();
                if (isTopHud) {
                    window.Player.showHudTemporarily(8000);
                    const hudButtons = [
                        document.getElementById("player-back-btn"),
                        document.getElementById("player-audio-sub-btn"),
                        document.getElementById("player-quality-btn"),
                        document.getElementById("player-volume-btn"),
                        document.getElementById("server-btn-vidsrcpm"),
                        document.getElementById("server-btn-vidlink"),
                        document.getElementById("server-btn-twoembed"),
                        document.getElementById("server-btn-vidsrcsu")
                    ].filter(Boolean);
                    const idx = hudButtons.indexOf(this.currentFocus);
                    if (idx > 0) {
                        this.setFocus(hudButtons[idx - 1]);
                    }
                } else {
                    window.Player.seek(-10);
                }
                return;
            } else if (keyCode === 39) { // Right -> Navigate Top HUD or Seek +10s
                e.preventDefault();
                if (isTopHud) {
                    window.Player.showHudTemporarily(8000);
                    const hudButtons = [
                        document.getElementById("player-back-btn"),
                        document.getElementById("player-audio-sub-btn"),
                        document.getElementById("player-quality-btn"),
                        document.getElementById("player-volume-btn"),
                        document.getElementById("server-btn-vidsrcpm"),
                        document.getElementById("server-btn-vidlink"),
                        document.getElementById("server-btn-twoembed"),
                        document.getElementById("server-btn-vidsrcsu")
                    ].filter(Boolean);
                    const idx = hudButtons.indexOf(this.currentFocus);
                    if (idx >= 0 && idx < hudButtons.length - 1) {
                        this.setFocus(hudButtons[idx + 1]);
                    }
                } else {
                    window.Player.seek(10);
                }
                return;
            }
        }

        // D-pad Direction Keys
        // 37 = Left, 38 = Up, 39 = Right, 40 = Down, 13 = Enter
        if ([37, 38, 39, 40, 13].includes(keyCode)) {
            // If focused element is an <input> and typing, let it type unless pressing down/up
            if (document.activeElement && document.activeElement.tagName === "INPUT") {
                if (keyCode === 13) {
                    document.activeElement.blur();
                    return;
                }
                if (keyCode === 37 || keyCode === 39) {
                    return; // Allow moving cursor inside search input
                }
            }

            e.preventDefault();

            if (keyCode === 13) {
                if (this.currentFocus) {
                    this.currentFocus.click();
                }
                return;
            }

            this.moveFocus(keyCode);
        }
    },

    getActiveScope() {
        // If Quality Picker Overlay is open, scope to it
        const picker = document.getElementById("quality-picker-overlay");
        if (picker) {
            return picker;
        }
        // If Delete Download Confirmation Modal is open, scope to it
        const deleteModal = document.getElementById("delete-download-modal");
        if (deleteModal && !deleteModal.classList.contains("hidden")) {
            return deleteModal;
        }
        // If Native offline player is open, scope to native player controls
        if (window.NativePlayer && window.NativePlayer.isOpen) {
            return document.getElementById("native-player-container");
        }
        // If Player is open:
        if (window.Player && window.Player.isOpen) {
            if (window.Player.isAudioSubModalOpen) {
                return document.getElementById("audio-sub-modal");
            }
            return document.getElementById("player-container");
        }
        // If Details Modal is open, scope to modal
        if (window.App && window.App.isModalOpen) {
            return document.getElementById("details-modal");
        }
        // Otherwise entire document body so top navbar + main content are both navigable
        return document.body;
    },

    moveFocus(direction) {
        const scope = this.getActiveScope();
        if (!scope) {
            return;
        }

        const focusables = Array.from(scope.querySelectorAll(".focusable:not([disabled])"))
            .filter(el => {
                const rect = el.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0 && window.getComputedStyle(el).display !== "none";
            });

        if (focusables.length === 0) {
            return;
        }

        if (!this.currentFocus || !scope.contains(this.currentFocus)) {
            this.setFocus(focusables[0]);
            return;
        }

        const next = this.findNextFocusable(this.currentFocus, focusables, direction);
        if (next) {
            this.setFocus(next);
        }
    },

    findNextFocusable(current, focusables, direction) {
        // Explicit smooth navigation when Online Cinema Stream Player is open
        if (window.Player && window.Player.isOpen) {
            // Down (40) from top HUD (Server 1, Server 2, Server 3, or Back) -> Move directly down to Play Video button
            if (direction === 40 && (current.classList.contains("server-btn") || current.id === "player-back-btn")) {
                const playBtn = document.getElementById("player-play-btn");
                if (playBtn) {
                    return playBtn;
                }
            }
            // Up (38) from bottom stream action buttons -> Return directly to active server button or Back button
            if (direction === 38 && current.classList.contains("stream-btn")) {
                const activeServer = document.querySelector(".server-btn.active") || 
                                     document.getElementById("server-btn-vidlink") || 
                                     document.querySelector(".server-btn");
                if (activeServer) {
                    return activeServer;
                }
            }
        }

        // 1. Navigation bar D-pad traversal
        if (current.classList.contains("nav-link")) {
            const navLinks = Array.from(document.querySelectorAll(".nav-link.focusable"));
            const idx = navLinks.indexOf(current);

            if (direction === 37) { // Left
                return idx > 0 ? navLinks[idx - 1] : null;
            }
            if (direction === 39) { // Right
                return idx < navLinks.length - 1 ? navLinks[idx + 1] : null;
            }
            if (direction === 40) { // Down -> Move down to hero or top content
                if (window.App && window.App.currentTab === "search") {
                    const searchInput = document.getElementById("search-input");
                    if (searchInput) {
                        return searchInput;
                    }
                }
                const heroPlay = document.getElementById("hero-play-btn");
                if (heroPlay && window.getComputedStyle(heroPlay).display !== "none" && !heroPlay.closest(".hidden")) {
                    return heroPlay;
                }
                const firstContent = focusables.find(el => !el.classList.contains("nav-link"));
                if (firstContent) {
                    return firstContent;
                }
            }
            if (direction === 38) { // Up
                return null; // Already at top
            }
        }

        // 2. Up from Hero Billboard action buttons or Search Input -> Goes directly to active Nav Link
        if (direction === 38) {
            if (current.closest(".hero-actions") || current.closest(".hero-billboard") || current.id === "search-input") {
                const activeNav = document.querySelector(".nav-link.active") || document.querySelector(".nav-link");
                if (activeNav) {
                    return activeNav;
                }
            }
        }

        // 3. Try row-based movement first if inside a carousel or button row
        const currentRow = current.closest(".carousel-items") || current.closest(".stream-control-card") || current.closest(".player-servers");

        // 37 = Left, 39 = Right
        if (direction === 37 || direction === 39) {
            if (currentRow) {
                const rowItems = Array.from(currentRow.querySelectorAll(".focusable"));
                const currentIndex = rowItems.indexOf(current);
                if (direction === 37 && currentIndex > 0) {
                    return rowItems[currentIndex - 1];
                }
                if (direction === 39 && currentIndex < rowItems.length - 1) {
                    return rowItems[currentIndex + 1];
                }
            }
        }

        // 4. Geometric spatial navigation for cross-row or general layout
        const currentRect = current.getBoundingClientRect();
        const currentCenter = {
            x: currentRect.left + currentRect.width / 2,
            y: currentRect.top + currentRect.height / 2
        };

        let bestCandidate = null;
        let bestDistance = Infinity;

        for (const candidate of focusables) {
            if (candidate === current) {
                continue;
            }

            const cRect = candidate.getBoundingClientRect();
            const cCenter = {
                x: cRect.left + cRect.width / 2,
                y: cRect.top + cRect.height / 2
            };

            const dx = cCenter.x - currentCenter.x;
            const dy = cCenter.y - currentCenter.y;

            let isValid = false;

            switch (direction) {
                case 37: // Left
                    isValid = dx < -10;
                    break;
                case 39: // Right
                    isValid = dx > 10;
                    break;
                case 38: // Up
                    isValid = dy < -10;
                    break;
                case 40: // Down
                    isValid = dy > 10;
                    break;
            }

            if (isValid) {
                // Weight the primary direction more heavily
                let distance;
                if (direction === 37 || direction === 39) {
                    distance = Math.sqrt((dx * 2) * (dx * 2) + dy * dy);
                } else {
                    distance = Math.sqrt(dx * dx + (dy * 2) * (dy * 2));
                }

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestCandidate = candidate;
                }
            }
        }

        return bestCandidate;
    },

    activateAudioSubModalElement() {
        const el = this.currentFocus || document.activeElement;
        if (!el) return;

        // 1. Header Tabs
        const subTab = el.closest("#tab-subtitles");
        if (subTab) {
            window.Player.switchAudioSubTab("subtitles", false);
            return;
        }
        const audioTab = el.closest("#tab-audio");
        if (audioTab) {
            window.Player.switchAudioSubTab("audio", false);
            return;
        }
        const qualityTab = el.closest("#tab-quality");
        if (qualityTab) {
            window.Player.switchAudioSubTab("quality", false);
            return;
        }
        const closeBtn = el.closest("#audio-sub-close-btn");
        if (closeBtn) {
            window.Player.closeAudioSubModal();
            return;
        }

        // 2. Subtitle Language Button
        const subBtn = el.closest(".sub-lang-btn");
        if (subBtn) {
            const lang = subBtn.getAttribute("data-lang");
            if (lang) {
                window.Player.selectSubtitle(lang);
                return;
            }
        }

        // 3. Quality Card Button
        const qualityCard = el.closest(".quality-card");
        if (qualityCard) {
            const qKey = qualityCard.id ? qualityCard.id.replace("quality-card-", "") : null;
            if (qKey) {
                window.Player.selectQuality(qKey);
                return;
            }
        }

        // 4. Audio Language Button
        const audioLangBtn = el.closest(".audio-lang-btn");
        if (audioLangBtn) {
            const trackKey = audioLangBtn.getAttribute("data-audio");
            if (trackKey) {
                window.Player.selectAudioTrack(trackKey);
                return;
            }
        }

        // 5. Audio Server Card
        const audioServerCard = el.closest(".audio-server-card");
        if (audioServerCard) {
            const serverMap = {
                "audio-card-vidsrcpm": "vidsrc_pm",
                "audio-card-vidlink": "vidlink",
                "audio-card-twoembed": "twoembed",
                "audio-card-vidsrcsu": "vidsrc_su"
            };
            const sKey = serverMap[audioServerCard.id];
            if (sKey) {
                window.Player.switchServer(sKey);
                window.Player.closeAudioSubModal();
                return;
            }
        }

        // 6. Audio Volume Control Buttons
        const volBtn = el.closest(".audio-vol-btn");
        if (volBtn) {
            if (volBtn.id === "btn-vol-tv-down") {
                window.Player.adjustTvVolume(-1);
                return;
            } else if (volBtn.id === "btn-vol-tv-up") {
                window.Player.adjustTvVolume(1);
                return;
            } else if (volBtn.id === "btn-vol-100") {
                window.Player.setVolume(1.0);
                return;
            } else if (volBtn.id === "btn-vol-mute") {
                window.Player.toggleMute();
                return;
            }
        }

        // 7. Timing Sync Buttons
        if (el.closest("#timing-minus-btn")) {
            window.Player.adjustSubtitleOffset(-0.5);
            return;
        }
        if (el.closest("#timing-plus-btn")) {
            window.Player.adjustSubtitleOffset(0.5);
            return;
        }
        if (el.closest("#timing-reset-btn")) {
            window.Player.adjustSubtitleOffset(0, true);
            return;
        }

        // Fallback
        if (typeof el.click === "function") {
            el.click();
        }
    },

    navigateAudioSubModal(direction) {
        const cur = this.currentFocus || document.activeElement;
        const modal = document.getElementById("audio-sub-modal");
        if (!modal) return;

        const headerTabs = [
            document.getElementById("tab-subtitles"),
            document.getElementById("tab-audio"),
            document.getElementById("tab-quality"),
            document.getElementById("audio-sub-close-btn")
        ].filter(Boolean);

        const isHeader = headerTabs.some(t => t === cur || (cur && t.contains(cur)));

        // A. Header Tabs: [Subtitles, Audio & Servers, Quality, Close]
        if (isHeader) {
            const activeHeaderBtn = headerTabs.find(t => t === cur || (cur && t.contains(cur)));
            const idx = headerTabs.indexOf(activeHeaderBtn);

            if (direction === 37) { // Left
                if (idx > 0) {
                    const nextTab = headerTabs[idx - 1];
                    this.setFocus(nextTab);
                    if (nextTab.id === "tab-subtitles") window.Player.switchAudioSubTab("subtitles", true);
                    else if (nextTab.id === "tab-audio") window.Player.switchAudioSubTab("audio", true);
                    else if (nextTab.id === "tab-quality") window.Player.switchAudioSubTab("quality", true);
                }
                return;
            }
            if (direction === 39) { // Right
                if (idx < headerTabs.length - 1) {
                    const nextTab = headerTabs[idx + 1];
                    this.setFocus(nextTab);
                    if (nextTab.id === "tab-subtitles") window.Player.switchAudioSubTab("subtitles", true);
                    else if (nextTab.id === "tab-audio") window.Player.switchAudioSubTab("audio", true);
                    else if (nextTab.id === "tab-quality") window.Player.switchAudioSubTab("quality", true);
                }
                return;
            }
            if (direction === 40) { // Down -> Enter active tab content
                const activeBody = modal.querySelector(".audio-sub-body:not(.hidden)");
                if (activeBody) {
                    const firstItem = activeBody.querySelector(".focusable.active") || activeBody.querySelector(".focusable");
                    if (firstItem) {
                        this.setFocus(firstItem);
                    }
                }
                return;
            }
            return;
        }

        // B. Subtitles Tab Content
        const subContent = document.getElementById("subtitles-tab-content");
        if (subContent && !subContent.classList.contains("hidden") && (subContent === cur || subContent.contains(cur))) {
            const subBtns = Array.from(subContent.querySelectorAll(".sub-lang-btn.focusable"));
            const timingBtns = Array.from(subContent.querySelectorAll(".timing-btn.focusable"));

            const sIdx = subBtns.indexOf(cur.closest(".sub-lang-btn") || cur);
            if (sIdx !== -1) {
                const COLS = 3;
                const row = Math.floor(sIdx / COLS);
                const col = sIdx % COLS;

                if (direction === 37) { // Left (stay within current row)
                    if (col > 0) {
                        this.setFocus(subBtns[sIdx - 1]);
                    }
                    return;
                }
                if (direction === 39) { // Right (stay within current row)
                    if (col < COLS - 1 && sIdx + 1 < subBtns.length) {
                        this.setFocus(subBtns[sIdx + 1]);
                    }
                    return;
                }
                if (direction === 38) { // Up
                    if (row === 0) {
                        // Move directly to Header Tab matching the column
                        const targetTab = headerTabs[Math.min(col, headerTabs.length - 1)];
                        if (targetTab) this.setFocus(targetTab);
                    } else {
                        this.setFocus(subBtns[sIdx - COLS]);
                    }
                    return;
                }
                if (direction === 40) { // Down
                    if (sIdx + COLS < subBtns.length) {
                        this.setFocus(subBtns[sIdx + COLS]);
                    } else if (sIdx === 8) {
                        // Portuguese (col 2) -> Move to Japanese (col 1 in row below)
                        this.setFocus(subBtns[subBtns.length - 1]);
                    } else {
                        // Bottom row (Italian at 9, Japanese at 10) -> Move to Timing row
                        const tIdx = Math.min(col, timingBtns.length - 1);
                        if (timingBtns[tIdx]) this.setFocus(timingBtns[tIdx]);
                        else if (timingBtns.length > 0) this.setFocus(timingBtns[0]);
                    }
                    return;
                }
                return;
            }

            // Timing row (-0.5s, +0.5s, Reset)
            const tIdx = timingBtns.indexOf(cur.closest(".timing-btn") || cur);
            if (tIdx !== -1) {
                if (direction === 37 && tIdx > 0) {
                    this.setFocus(timingBtns[tIdx - 1]);
                    return;
                }
                if (direction === 39 && tIdx < timingBtns.length - 1) {
                    this.setFocus(timingBtns[tIdx + 1]);
                    return;
                }
                if (direction === 38) { // Up -> Return to subtitle button directly above
                    if (tIdx === 0 && subBtns[9]) {
                        this.setFocus(subBtns[9]); // Italian
                    } else if (tIdx === 1 && subBtns[10]) {
                        this.setFocus(subBtns[10]); // Japanese
                    } else if (subBtns.length > 0) {
                        this.setFocus(subBtns[subBtns.length - 1]);
                    }
                    return;
                }
                return;
            }
        }

        // C. Quality Tab Content
        const qualityContent = document.getElementById("quality-tab-content");
        if (qualityContent && !qualityContent.classList.contains("hidden") && (qualityContent === cur || qualityContent.contains(cur))) {
            const qualityCards = Array.from(qualityContent.querySelectorAll(".quality-card.focusable"));
            const qIdx = qualityCards.indexOf(cur.closest(".quality-card") || cur);

            if (qIdx !== -1) {
                if (direction === 38) { // Up
                    if (qIdx === 0) {
                        const qTab = document.getElementById("tab-quality") || headerTabs[2];
                        if (qTab) this.setFocus(qTab);
                    } else {
                        this.setFocus(qualityCards[qIdx - 1]);
                    }
                    return;
                }
                if (direction === 40) { // Down
                    if (qIdx < qualityCards.length - 1) {
                        this.setFocus(qualityCards[qIdx + 1]);
                    }
                    return;
                }
                if (direction === 37) { // Left -> Switch to Audio Tab
                    const aTab = document.getElementById("tab-audio");
                    if (aTab) {
                        window.Player.switchAudioSubTab("audio", true);
                        this.setFocus(aTab);
                    }
                    return;
                }
                if (direction === 39) { // Right -> Focus Close button
                    const closeBtn = document.getElementById("audio-sub-close-btn");
                    if (closeBtn) this.setFocus(closeBtn);
                    return;
                }
                return;
            }
        }

        // D. Audio & Servers Tab Content
        const audioContent = document.getElementById("audio-tab-content");
        if (audioContent && !audioContent.classList.contains("hidden") && (audioContent === cur || audioContent.contains(cur))) {
            const audioBtns = Array.from(audioContent.querySelectorAll(".audio-lang-btn.focusable"));
            const volBtns = Array.from(audioContent.querySelectorAll(".audio-vol-btn.focusable"));
            const serverCards = Array.from(audioContent.querySelectorAll(".audio-server-card.focusable"));

            // 1. Audio Language Buttons (3 columns)
            const aIdx = audioBtns.indexOf(cur.closest(".audio-lang-btn") || cur);
            if (aIdx !== -1) {
                const COLS = 3;
                const row = Math.floor(aIdx / COLS);
                const col = aIdx % COLS;

                if (direction === 37) { // Left
                    if (col > 0) this.setFocus(audioBtns[aIdx - 1]);
                    return;
                }
                if (direction === 39) { // Right
                    if (col < COLS - 1 && aIdx + 1 < audioBtns.length) this.setFocus(audioBtns[aIdx + 1]);
                    return;
                }
                if (direction === 38) { // Up
                    if (row === 0) {
                        const targetTab = headerTabs[Math.min(col, headerTabs.length - 1)];
                        if (targetTab) this.setFocus(targetTab);
                    } else {
                        this.setFocus(audioBtns[aIdx - COLS]);
                    }
                    return;
                }
                if (direction === 40) { // Down -> Move to next row or to Volume controls
                    if (aIdx + COLS < audioBtns.length) {
                        this.setFocus(audioBtns[aIdx + COLS]);
                    } else if (volBtns.length > 0) {
                        const targetVolIdx = Math.min(col, volBtns.length - 1);
                        this.setFocus(volBtns[targetVolIdx]);
                    } else if (serverCards.length > 0) {
                        this.setFocus(serverCards[0]);
                    }
                    return;
                }
                return;
            }

            // 2. Audio Volume Controls (4 buttons: 100%, 75%, 50%, Mute)
            const vIdx = volBtns.indexOf(cur.closest(".audio-vol-btn") || cur);
            if (vIdx !== -1) {
                if (direction === 37) { // Left
                    if (vIdx > 0) this.setFocus(volBtns[vIdx - 1]);
                    return;
                }
                if (direction === 39) { // Right
                    if (vIdx < volBtns.length - 1) this.setFocus(volBtns[vIdx + 1]);
                    return;
                }
                if (direction === 38) { // Up -> Return to audio language grid
                    if (audioBtns.length > 0) {
                        const targetAudioIdx = Math.min(vIdx + 3, audioBtns.length - 1);
                        this.setFocus(audioBtns[targetAudioIdx]);
                    } else {
                        const aTab = document.getElementById("tab-audio") || headerTabs[1];
                        if (aTab) this.setFocus(aTab);
                    }
                    return;
                }
                if (direction === 40) { // Down -> Move to Streaming Server Cards
                    if (serverCards.length > 0) {
                        this.setFocus(serverCards[0]);
                    }
                    return;
                }
                return;
            }

            // 3. Audio Server Cards (1 column)
            const sCardIdx = serverCards.indexOf(cur.closest(".audio-server-card") || cur);
            if (sCardIdx !== -1) {
                if (direction === 38) { // Up
                    if (sCardIdx === 0) {
                        if (volBtns.length > 0) {
                            this.setFocus(volBtns[0]);
                        } else if (audioBtns.length > 0) {
                            this.setFocus(audioBtns[audioBtns.length - 1]);
                        } else {
                            const aTab = document.getElementById("tab-audio") || headerTabs[1];
                            if (aTab) this.setFocus(aTab);
                        }
                    } else {
                        this.setFocus(serverCards[sCardIdx - 1]);
                    }
                    return;
                }
                if (direction === 40) { // Down
                    if (sCardIdx < serverCards.length - 1) {
                        this.setFocus(serverCards[sCardIdx + 1]);
                    }
                    return;
                }
                if (direction === 37) { // Left -> Switch to Subtitles Tab
                    const subTab = document.getElementById("tab-subtitles");
                    if (subTab) {
                        window.Player.switchAudioSubTab("subtitles", true);
                        this.setFocus(subTab);
                    }
                    return;
                }
                if (direction === 39) { // Right -> Switch to Quality Tab
                    const qTab = document.getElementById("tab-quality");
                    if (qTab) {
                        window.Player.switchAudioSubTab("quality", true);
                        this.setFocus(qTab);
                    }
                    return;
                }
                return;
            }
        }

        // Fallback
        this.moveFocus(direction);
    }
};

window.Navigation = Navigation;
