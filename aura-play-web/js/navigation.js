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
            // When Audio & Subtitles TV Modal is open
            if (window.Player.isAudioSubModalOpen) {
                if (keyCode === 27) { // Escape
                    e.preventDefault();
                    window.Player.closeAudioSubModal();
                    return;
                }
                if (keyCode === 13) { // Enter / OK
                    e.preventDefault();
                    if (this.currentFocus) {
                        this.currentFocus.click();
                    }
                    return;
                }
                if ([37, 38, 39, 40].includes(keyCode)) {
                    e.preventDefault();
                    this.moveFocus(keyCode);
                    return;
                }
                return;
            }

            const isTopHudFocused = this.currentFocus && 
                (this.currentFocus.id === "player-back-btn" || 
                 this.currentFocus.id === "player-audio-sub-btn" ||
                 this.currentFocus.classList.contains("server-btn"));

            if (keyCode === 13 || keyCode === 32) { // OK / Enter / Space
                e.preventDefault();
                if (isTopHudFocused) {
                    this.currentFocus.click();
                    return;
                }
                window.Player.togglePlayPause();
                return;
            }

            if (keyCode === 38) { // Up -> Reveal Top HUD & Focus Audio & Subtitles or Back button
                e.preventDefault();
                if (!isTopHudFocused) {
                    window.Player.showHudTemporarily();
                    const targetBtn = document.getElementById("player-audio-sub-btn") || document.getElementById("player-back-btn");
                    if (targetBtn) this.setFocus(targetBtn);
                }
                return;
            } else if (keyCode === 40) { // Down -> Hide Top HUD, return to clean video viewing
                e.preventDefault();
                if (isTopHudFocused) {
                    window.Player.hideHud();
                }
                return;
            } else if (keyCode === 37) { // Left -> Seek -10s or navigate Top HUD
                e.preventDefault();
                if (isTopHudFocused) {
                    const hudButtons = [
                        document.getElementById("player-back-btn"),
                        document.getElementById("player-audio-sub-btn"),
                        document.getElementById("server-btn-vidsrcpm"),
                        document.getElementById("server-btn-moviesapi"),
                        document.getElementById("server-btn-twoembed"),
                        document.getElementById("server-btn-vidlink")
                    ].filter(Boolean);
                    const idx = hudButtons.indexOf(this.currentFocus);
                    if (idx > 0) {
                        this.setFocus(hudButtons[idx - 1]);
                    }
                } else {
                    window.Player.seek(-10);
                }
                return;
            } else if (keyCode === 39) { // Right -> Seek +10s or navigate Top HUD
                e.preventDefault();
                if (isTopHudFocused) {
                    const hudButtons = [
                        document.getElementById("player-back-btn"),
                        document.getElementById("player-audio-sub-btn"),
                        document.getElementById("server-btn-vidsrcpm"),
                        document.getElementById("server-btn-moviesapi"),
                        document.getElementById("server-btn-twoembed"),
                        document.getElementById("server-btn-vidlink")
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
                const playBtn = document.getElementById("stream-play-btn");
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
    }
};

window.Navigation = Navigation;
