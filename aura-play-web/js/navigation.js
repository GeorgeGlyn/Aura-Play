// Spatial D-Pad & Remote Navigation Engine for TV and Touch
const Navigation = {
    currentFocus: null,
    isTvMode: true,

    init() {
        window.addEventListener("keydown", (e) => this.handleKeyDown(e));

        // When mouse moves or touch occurs, keep focus updated smoothly
        document.addEventListener("pointerdown", (e) => {
            const focusable = e.target.closest(".focusable");
            if (focusable) {
                this.setFocus(focusable, false);
            }
        });
    },

    setFocus(element, scrollIntoView = true) {
        if (!element) return;
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
        // If Player is open, scope to player controls
        if (window.Player && window.Player.isOpen) {
            return document.getElementById("player-container");
        }
        // If Details Modal is open, scope to modal
        if (window.App && window.App.isModalOpen) {
            return document.getElementById("details-modal");
        }
        // Otherwise main app viewport
        return document.getElementById("app-content");
    },

    moveFocus(direction) {
        const scope = this.getActiveScope();
        if (!scope) return;

        const focusables = Array.from(scope.querySelectorAll(".focusable:not([disabled])"))
            .filter(el => {
                const rect = el.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0 && window.getComputedStyle(el).display !== "none";
            });

        if (focusables.length === 0) return;

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
        // Try row-based movement first if inside a carousel
        const currentRow = current.closest(".carousel-items");

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

        // Geometric spatial navigation for cross-row or general layout
        const currentRect = current.getBoundingClientRect();
        const currentCenter = {
            x: currentRect.left + currentRect.width / 2,
            y: currentRect.top + currentRect.height / 2
        };

        let bestCandidate = null;
        let bestDistance = Infinity;

        for (const candidate of focusables) {
            if (candidate === current) continue;

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
                if (direction === 38 || direction === 40) {
                    distance = Math.abs(dy) * 1.0 + Math.abs(dx) * 2.5;
                } else {
                    distance = Math.abs(dx) * 1.0 + Math.abs(dy) * 2.5;
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
