// Aura Play - Core Application Controller
// Global Ad-Block & Popup Guard: Prevent external embeds from opening popups or redirecting the TV app
(function() {
    try {
        window.open = function(url, target, features) {
            console.warn("[AuraAdBlock] Strictly blocked popup window to:", url);
            return null; // Nullify popup creation
        };
    } catch (e) {}

    // Prevent iframe-initiated redirects from hijacking or replacing our app
    window.addEventListener("beforeunload", function(e) {
        if (window.Player && window.Player.isOpen) {
            console.warn("[AuraAdBlock] Intercepted redirect attempt during playback");
        }
    });
})();

const App = {
    currentTab: "home",
    isModalOpen: false,
    selectedMedia: null,
    heroMedia: null,
    watchlist: [],
    searchDebounceTimer: null,

    async init() {
        this.loadWatchlist();
        this.bindEvents();
        await this.loadHome();

        // Initialize Navigation and Tizen bridge
        if (window.Navigation) { window.Navigation.init(); }
        if (window.TizenBridge) { window.TizenBridge.init(); }
        if (window.Player) { window.Player.init(); }

        // Initial focus on Hero Play button for TV remote
        setTimeout(() => {
            const heroPlay = document.getElementById("hero-play-btn");
            if (heroPlay && window.Navigation) {
                window.Navigation.setFocus(heroPlay);
            }
        }, 500);
    },

    bindEvents() {
        // Nav tabs
        document.querySelectorAll(".nav-link").forEach(link => {
            link.addEventListener("click", (e) => {
                const tab = e.currentTarget.dataset.tab;
                this.switchTab(tab);
            });
        });

        // Search input
        const searchInput = document.getElementById("search-input");
        if (searchInput) {
            searchInput.addEventListener("input", (e) => {
                clearTimeout(this.searchDebounceTimer);
                this.searchDebounceTimer = setTimeout(() => {
                    this.performSearch(e.target.value);
                }, 350);
            });
        }
    },

    switchTab(tab) {
        this.currentTab = tab;
        document.querySelectorAll(".nav-link").forEach(l => {
            l.classList.toggle("active", l.dataset.tab === tab);
        });

        const homeView      = document.getElementById("view-home");
        const searchView    = document.getElementById("view-search");
        const watchlistView = document.getElementById("view-watchlist");
        const downloadsView = document.getElementById("view-downloads");

        homeView.classList.add("hidden");
        searchView.classList.add("hidden");
        watchlistView.classList.add("hidden");
        downloadsView.classList.add("hidden");

        if (tab === "home") {
            homeView.classList.remove("hidden");
            this.loadHome();
        } else if (tab === "movies") {
            homeView.classList.remove("hidden");
            this.loadMoviesTab();
        } else if (tab === "tv") {
            homeView.classList.remove("hidden");
            this.loadTvTab();
        } else if (tab === "search") {
            searchView.classList.remove("hidden");
            const searchInput = document.getElementById("search-input");
            if (searchInput) {
                searchInput.focus();
                if (window.Navigation) window.Navigation.setFocus(searchInput);
            }
        } else if (tab === "watchlist") {
            watchlistView.classList.remove("hidden");
            this.renderWatchlist();
        } else if (tab === "downloads") {
            downloadsView.classList.remove("hidden");
            if (window.DownloadUI) DownloadUI.renderLibrary();
        }
    },

    loadHome() {
        try {
            // Render Netflix-style continue watching row if items exist
            this.renderContinueWatchingRow();

            // Load trending & hero immediately so Samsung TV displays content in milliseconds
            TMDB.getTrendingAll().then(trending => {
                if (trending && trending.length > 0) {
                    this.setHero(trending[0]);
                    this.renderRow("row-trending", "Trending Now", trending);
                }
            });

            TMDB.getPopularMovies().then(popularMovies => {
                if (popularMovies) this.renderRow("row-popular-movies", "Blockbuster Movies", popularMovies);
            });

            TMDB.getTopRatedTV().then(topTv => {
                if (topTv) this.renderRow("row-top-tv", "Critically Acclaimed TV Shows", topTv);
            });

            TMDB.getMoviesByGenre(28).then(actionMovies => {
                if (actionMovies) this.renderRow("row-action", "Action & Thrillers", actionMovies, "Action");
            });

            TMDB.getMoviesByGenre(878).then(sciFi => {
                if (sciFi) this.renderRow("row-scifi", "Sci-Fi & Fantasy", sciFi, "Sci-Fi");
            });

            TMDB.getMoviesByGenre(35).then(comedyMovies => {
                if (comedyMovies) this.renderRow("row-comedy", "Top Comedies", comedyMovies, "Comedy");
            });

            TMDB.getMoviesByGenre(16).then(anim => {
                if (anim) this.renderRow("row-animation", "Animation & Anime", anim, "Animation");
            });

            TMDB.getMoviesByGenre(27).then(horror => {
                if (horror) this.renderRow("row-horror", "Horror & Suspense", horror, "Horror");
            });
        } catch (err) {
            console.error("Failed to load home content:", err);
        }
    },

    loadMoviesTab() {
        TMDB.getPopularMovies().then(popular => {
            if (popular && popular.length > 0) this.setHero(popular[0]);
            this.renderRow("row-trending", "Popular Movies", popular || []);
        });

        TMDB.getTopRatedMovies().then(topRated => {
            if (topRated) this.renderRow("row-popular-movies", "Top Rated Cinema", topRated);
        });

        TMDB.getMoviesByGenre(878).then(sciFi => {
            if (sciFi) this.renderRow("row-top-tv", "Sci-Fi & Fantasy", sciFi);
        });
    },

    loadTvTab() {
        TMDB.getPopularTV().then(popularTv => {
            if (popularTv && popularTv.length > 0) this.setHero(popularTv[0]);
            this.renderRow("row-trending", "Binge-Worthy TV Series", popularTv || []);
        });

        TMDB.getTopRatedTV().then(topTv => {
            if (topTv) this.renderRow("row-popular-movies", "Top Rated Series", topTv);
        });
    },

    setHero(media) {
        this.heroMedia = media;
        const heroEl = document.getElementById("hero-billboard");
        const backdropUrl = TMDB.getBackdropUrl(media.backdrop_path);
        heroEl.style.backgroundImage = `linear-gradient(to top, #08080c 10%, rgba(8,8,12,0.4) 60%, rgba(8,8,12,0.8) 100%), url('${backdropUrl}')`;

        document.getElementById("hero-title").textContent = media.title || media.name || "Aura Premiere";
        document.getElementById("hero-overview").textContent = media.overview || "Stream in 4K Ultra HD with ATMOS audio.";
        
        const matchPercent = Math.min(99, Math.round((media.vote_average || 8) * 10) + 7);
        document.getElementById("hero-match").textContent = `${matchPercent}% Match`;

        const year = (media.release_date || media.first_air_date || "").substring(0, 4);
        document.getElementById("hero-year").textContent = year || "2024";

        // Bind Hero Play & Info Buttons
        const playBtn = document.getElementById("hero-play-btn");
        const infoBtn = document.getElementById("hero-info-btn");

        playBtn.onclick = () => window.Player.play(media);
        infoBtn.onclick = () => this.openDetails(media);
    },

    renderRow(rowId, title, items, searchQuery = null) {
        let rowContainer = document.getElementById(rowId);
        if (!rowContainer) {
            const rowsWrapper = document.getElementById("carousel-rows");
            rowContainer = document.createElement("div");
            rowContainer.id = rowId;
            rowContainer.className = "carousel-section";
            rowsWrapper.appendChild(rowContainer);
        }

        rowContainer.innerHTML = `
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                <h2 class="carousel-title" style="margin-bottom:0;">${title}</h2>
                <span class="row-see-all focusable" tabindex="0" style="color:var(--primary,#e50914);cursor:pointer;font-size:13px;font-weight:600;padding:4px 8px;">See All &rsaquo;</span>
            </div>
            <div class="carousel-items">
                ${items.filter(item => item.poster_path).map(item => `
                    <div class="media-card focusable" tabindex="0" data-id="${item.id}" data-type="${item.media_type || (item.title ? 'movie' : 'tv')}">
                        <img class="media-poster" src="${TMDB.getPosterUrl(item.poster_path)}" alt="${item.title || item.name}" loading="lazy"/>
                        <div class="card-overlay">
                            <span class="card-match">${Math.round((item.vote_average || 7.5) * 10)}% Match</span>
                            <span class="card-title">${item.title || item.name}</span>
                        </div>
                    </div>
                `).join("")}
                <div class="media-card card-view-all focusable" tabindex="0" style="display:flex;flex-direction:column;align-items:center;justify-content:center;background:linear-gradient(135deg, #221215, #141416);border:1px solid rgba(255,255,255,0.15);border-radius:8px;min-width:130px;height:195px;text-align:center;cursor:pointer;">
                    <div style="width:40px;height:40px;border-radius:50%;background:rgba(229,9,20,0.15);border:1px solid rgba(229,9,20,0.4);display:flex;align-items:center;justify-content:center;margin-bottom:8px;font-size:18px;color:#e50914;">➔</div>
                    <div style="font-weight:700;font-size:13px;color:#fff;">View All</div>
                    <div style="font-size:11px;color:#888;">Explore More</div>
                </div>
            </div>
        `;

        const viewAllHandler = () => {
            const query = searchQuery || title.replace("Movies", "").replace("Series", "").replace("TV Shows", "").replace("Now", "").trim();
            this.switchTab("search");
            const searchInput = document.getElementById("search-input");
            if (searchInput) {
                searchInput.value = query;
                this.performSearch(query);
            }
        };

        const seeAllBtn = rowContainer.querySelector(".row-see-all");
        if (seeAllBtn) seeAllBtn.addEventListener("click", viewAllHandler);

        const viewAllCard = rowContainer.querySelector(".card-view-all");
        if (viewAllCard) viewAllCard.addEventListener("click", viewAllHandler);

        // Add click events to cards
        rowContainer.querySelectorAll(".media-card:not(.card-view-all)").forEach(card => {
            const id = card.dataset.id;
            const type = card.dataset.type;
            const media = items.find(i => i.id == id) || { id, media_type: type };

            card.addEventListener("click", () => this.openDetails(media));
        });
    },

    async openDetails(media) {
        this.selectedMedia = media;
        this.isModalOpen = true;

        const mediaType = media.media_type || (media.title ? "movie" : "tv");
        const details = await TMDB.getDetails(mediaType, media.id);
        const fullMedia = details || media;
        fullMedia.media_type = mediaType;
        this.selectedMedia = fullMedia;

        const modal = document.getElementById("details-modal");
        const backdrop = document.getElementById("modal-backdrop");
        backdrop.src = TMDB.getBackdropUrl(fullMedia.backdrop_path || fullMedia.poster_path);

        document.getElementById("modal-title").textContent = fullMedia.title || fullMedia.name;
        document.getElementById("modal-overview").textContent = fullMedia.overview || "No overview available.";
        
        const year = (fullMedia.release_date || fullMedia.first_air_date || "").substring(0, 4);
        document.getElementById("modal-year").textContent = year;
        
        const matchPercent = Math.min(99, Math.round((fullMedia.vote_average || 8) * 10) + 7);
        document.getElementById("modal-match").textContent = `${matchPercent}% Match`;

        // Genres
        const genresEl = document.getElementById("modal-genres");
        genresEl.innerHTML = (fullMedia.genres || []).map(g => `<span class="genre-tag">${g.name}</span>`).join("");

        // Watchlist button state
        this.updateWatchlistBtn();

        // TV Series Seasons
        const seasonsSection = document.getElementById("modal-seasons-section");
        if (mediaType === "tv" && fullMedia.seasons && fullMedia.seasons.length > 0) {
            seasonsSection.classList.remove("hidden");
            this.renderSeasonChips(fullMedia);
        } else {
            seasonsSection.classList.add("hidden");
        }

        // Setup Play button with stream availability verification
        const playBtn = document.getElementById("modal-play-btn");
        const isAvailable = (typeof TMDB.isMediaAvailable === "function") 
            ? TMDB.isMediaAvailable(fullMedia) 
            : true;

        if (!isAvailable) {
            playBtn.innerHTML = `<span>⏳ Not Yet Released on OTT</span>`;
            playBtn.onclick = () => {
                if (window.Player && typeof window.Player.showToast === "function") {
                    window.Player.showToast("This title has not been released on digital streaming yet.");
                }
            };
        } else {
            playBtn.innerHTML = `<span>▶ Play</span>`;
            playBtn.onclick = () => window.Player.play(fullMedia);
        }

        // Setup Download button (for movies; episodes get their own per-row button)
        const dlBtn = document.getElementById("modal-download-btn");
        if (dlBtn) {
            if (mediaType === "movie") {
                dlBtn.classList.remove("hidden");
                dlBtn.onclick = () => QualityPicker.show(fullMedia);
            } else {
                // For TV shows hide the top-level download btn; download is per-episode
                dlBtn.classList.add("hidden");
            }
        }

        // Setup My List button
        const listBtn = document.getElementById("modal-list-btn");
        listBtn.onclick = () => this.toggleWatchlist(fullMedia);

        modal.classList.remove("hidden");
        modal.classList.add("active");

        // Focus modal play button for TV remote
        if (window.Navigation) {
            window.Navigation.setFocus(playBtn);
        }
    },

    renderSeasonChips(tvShow) {
        const chipsContainer = document.getElementById("season-chips");
        const validSeasons = tvShow.seasons.filter(s => s.season_number > 0);
        
        chipsContainer.innerHTML = validSeasons.map((s, idx) => `
            <button class="season-chip focusable ${idx === 0 ? 'active' : ''}" data-season="${s.season_number}">
                ${s.name || `Season ${s.season_number}`}
            </button>
        `).join("");

        chipsContainer.querySelectorAll(".season-chip").forEach(chip => {
            chip.addEventListener("click", (e) => {
                chipsContainer.querySelectorAll(".season-chip").forEach(c => c.classList.remove("active"));
                e.currentTarget.classList.add("active");
                this.loadSeasonEpisodes(tvShow.id, e.currentTarget.dataset.season);
            });
        });

        // Load season 1 by default
        if (validSeasons.length > 0) {
            this.loadSeasonEpisodes(tvShow.id, validSeasons[0].season_number);
        }
    },

    async loadSeasonEpisodes(tvId, seasonNumber) {
        const episodeListEl = document.getElementById("modal-episodes-list");
        episodeListEl.innerHTML = `<div class="loading-spinner">Loading episodes...</div>`;

        const seasonData = await TMDB.getSeason(tvId, seasonNumber);
        if (!seasonData || !seasonData.episodes) {
            episodeListEl.innerHTML = `<p class="no-episodes">No episodes found.</p>`;
            return;
        }

        episodeListEl.innerHTML = seasonData.episodes.map(ep => `
            <div class="episode-card focusable" tabindex="0" data-season="${seasonNumber}" data-episode="${ep.episode_number}">
                <div class="episode-thumb-wrap">
                    <img class="episode-thumb" src="${TMDB.getPosterUrl(ep.still_path, 'w500')}" alt="${ep.name}"/>
                    <div class="episode-play-icon">▶</div>
                </div>
                <div class="episode-info">
                    <div class="episode-header">
                        <span class="episode-num">${ep.episode_number}. ${ep.name}</span>
                        <span class="episode-runtime">${ep.runtime ? `${ep.runtime}m` : ''}</span>
                    </div>
                    <p class="episode-overview">${ep.overview || 'Stream this episode in 4K Ultra HD.'}</p>
                    <div style="margin-top:8px;display:flex;gap:10px;">
                        <button class="dl-btn dl-btn-play focusable episode-dl-btn" 
                                tabindex="0"
                                data-season="${seasonNumber}" 
                                data-episode="${ep.episode_number}"
                                style="font-size:12px;padding:6px 12px;background:rgba(229,9,20,0.15);border:1px solid rgba(229,9,20,0.4);color:#fff;">
                            ⬇ Download
                        </button>
                    </div>
                </div>
            </div>
        `).join("");

        // Click on episode launches player with season and episode numbers
        episodeListEl.querySelectorAll(".episode-card").forEach(card => {
            // Click on the card body (not the download button) → play
            card.addEventListener("click", (e) => {
                if (e.target.closest(".episode-dl-btn")) return; // handled below
                const s = card.dataset.season;
                const e2 = card.dataset.episode;
                window.Player.play(this.selectedMedia, s, e2);
            });
        });

        // Per-episode download buttons
        episodeListEl.querySelectorAll(".episode-dl-btn").forEach(btn => {
            btn.addEventListener("click", (e) => {
                e.stopPropagation();
                const s = btn.dataset.season;
                const ep = btn.dataset.episode;
                QualityPicker.show(this.selectedMedia, s, ep);
            });
        });
    },

    closeModal() {
        this.isModalOpen = false;
        const modal = document.getElementById("details-modal");
        modal.classList.remove("active");
        modal.classList.add("hidden");

        // Return focus to previous card
        if (window.Navigation) {
            window.Navigation.moveFocus(40);
        }
    },

    async performSearch(query) {
        const resultsGrid = document.getElementById("search-results-grid");
        if (!query || query.length < 2) {
            resultsGrid.innerHTML = `<p class="search-hint">Search for movies, TV series, actors, or genres...</p>`;
            return;
        }

        resultsGrid.innerHTML = `<div class="loading-spinner">Searching Aura universe...</div>`;
        const results = await TMDB.search(query);

        if (results.length === 0) {
            resultsGrid.innerHTML = `<p class="search-hint">No streamable titles found matching "${query}". Only fully released movies and TV series are shown.</p>`;
            return;
        }

        resultsGrid.innerHTML = results.filter(i => i.poster_path).map(item => `
            <div class="media-card focusable" tabindex="0" data-id="${item.id}" data-type="${item.media_type}">
                <img class="media-poster" src="${TMDB.getPosterUrl(item.poster_path)}" alt="${item.title || item.name}"/>
                <div class="card-overlay">
                    <span class="card-match">${Math.round((item.vote_average || 7) * 10)}%</span>
                    <span class="card-title">${item.title || item.name}</span>
                </div>
            </div>
        `).join("");

        resultsGrid.querySelectorAll(".media-card").forEach(card => {
            const id = card.dataset.id;
            const item = results.find(i => i.id == id);
            card.addEventListener("click", () => this.openDetails(item));
        });
    },

    loadWatchlist() {
        try {
            const data = localStorage.getItem("aura_watchlist");
            this.watchlist = data ? JSON.parse(data) : [];
        } catch (e) {
            this.watchlist = [];
        }
    },

    toggleWatchlist(media) {
        const index = this.watchlist.findIndex(i => i.id === media.id);
        if (index >= 0) {
            this.watchlist.splice(index, 1);
        } else {
            this.watchlist.push({
                id: media.id,
                title: media.title || media.name,
                poster_path: media.poster_path,
                backdrop_path: media.backdrop_path,
                media_type: media.media_type || (media.title ? "movie" : "tv"),
                vote_average: media.vote_average
            });
        }
        localStorage.setItem("aura_watchlist", JSON.stringify(this.watchlist));
        this.updateWatchlistBtn();
    },

    updateWatchlistBtn() {
        const btn = document.getElementById("modal-list-btn");
        if (!btn || !this.selectedMedia) return;
        const exists = this.watchlist.some(i => i.id === this.selectedMedia.id);
        btn.innerHTML = exists ? `<span>✓ In My List</span>` : `<span>+ My List</span>`;
    },

    renderWatchlist() {
        const grid = document.getElementById("watchlist-grid");
        if (this.watchlist.length === 0) {
            grid.innerHTML = `<p class="search-hint">Your list is empty. Add movies and TV series to watch later.</p>`;
            return;
        }

        grid.innerHTML = this.watchlist.map(item => `
            <div class="media-card focusable" tabindex="0" data-id="${item.id}" data-type="${item.media_type}">
                <img class="media-poster" src="${TMDB.getPosterUrl(item.poster_path)}" alt="${item.title}"/>
                <div class="card-overlay">
                    <span class="card-title">${item.title}</span>
                </div>
            </div>
        `).join("");

        grid.querySelectorAll(".media-card").forEach(card => {
            const id = card.dataset.id;
            const item = this.watchlist.find(i => i.id == id);
            card.addEventListener("click", () => this.openDetails(item));
        });
    },

    getContinueWatching() {
        try {
            const data = localStorage.getItem("aura_continue_watching");
            return data ? JSON.parse(data) : [];
        } catch (e) {
            return [];
        }
    },

    getContinueWatchingItem(mediaId, season = null, episode = null) {
        const list = this.getContinueWatching();
        return list.find(i => {
            if (season) {
                return (i.id == mediaId || i.downloadKey === `tv_${mediaId}_s${season}_e${episode}`) && i.season == season && i.episode == episode;
            }
            return (i.id == mediaId || i.downloadKey === `movie_${mediaId}`);
        }) || null;
    },

    updateContinueWatching(item) {
        if (!item || !item.id) return;
        const list = this.getContinueWatching();
        const key = item.season ? `${item.id}_s${item.season}_e${item.episode}` : `${item.id}`;
        
        // Remove existing entry if present
        const filtered = list.filter(i => {
            const iKey = i.season ? `${i.id}_s${i.season}_e${i.episode}` : `${i.id}`;
            return iKey !== key;
        });

        // Don't add if watched less than 5s or near end
        if (item.position > 5 && (!item.duration || item.position < (item.duration - 20))) {
            filtered.unshift({
                id: item.id,
                title: item.title,
                poster_path: item.poster_path,
                backdrop_path: item.backdrop_path,
                media_type: item.media_type || (item.season ? "tv" : "movie"),
                season: item.season || null,
                episode: item.episode || null,
                position: Math.floor(item.position),
                duration: Math.floor(item.duration || 5400),
                isOffline: !!item.isOffline,
                downloadKey: item.downloadKey || null,
                updatedAt: Date.now()
            });
        }

        // Limit to 20 items
        const trimmed = filtered.slice(0, 20);
        try {
            localStorage.setItem("aura_continue_watching", JSON.stringify(trimmed));
        } catch (e) {}

        this.renderContinueWatchingRow();
    },

    removeContinueWatching(mediaId, season = null, episode = null) {
        const list = this.getContinueWatching();
        const key = season ? `${mediaId}_s${season}_e${episode}` : `${mediaId}`;
        const filtered = list.filter(i => {
            const iKey = i.season ? `${i.id}_s${i.season}_e${i.episode}` : `${i.id}`;
            return iKey !== key;
        });
        try {
            localStorage.setItem("aura_continue_watching", JSON.stringify(filtered));
        } catch (e) {}
        this.renderContinueWatchingRow();
    },

    renderContinueWatchingRow() {
        const rowContainer = document.getElementById("row-continue-watching");
        if (!rowContainer) return;

        const list = this.getContinueWatching();
        if (list.length === 0) {
            rowContainer.classList.add("hidden");
            rowContainer.innerHTML = "";
            return;
        }

        rowContainer.classList.remove("hidden");
        rowContainer.innerHTML = `
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                <h2 class="carousel-title" style="margin-bottom:0;display:flex;align-items:center;gap:8px;">
                    <span>Continue Watching</span>
                    <span style="font-size:11px;font-weight:700;background:rgba(229,9,20,0.18);color:#e50914;padding:2px 8px;border-radius:12px;border:1px solid rgba(229,9,20,0.4);">FOR YOU</span>
                </h2>
                <span class="clear-continue-btn focusable" tabindex="0" style="color:var(--text-muted);cursor:pointer;font-size:12px;font-weight:600;padding:4px 8px;">Clear All</span>
            </div>
            <div class="carousel-items continue-watching-items">
                ${list.map(item => {
                    const dur = item.duration || 5400;
                    const pct = Math.min(100, Math.max(5, Math.round((item.position / dur) * 100)));
                    const timeLeftMin = Math.max(1, Math.ceil((dur - item.position) / 60));
                    const posterSrc = item.poster_path ? TMDB.getPosterUrl(item.poster_path) : "assets/icon.png";
                    const subtitle = item.season ? `S${item.season} E${item.episode}` : (item.isOffline ? "Offline Download" : "Movie");

                    return `
                        <div class="media-card cw-card focusable" tabindex="0" 
                             data-id="${item.id}" 
                             data-type="${item.media_type}"
                             data-season="${item.season || ''}"
                             data-episode="${item.episode || ''}"
                             data-key="${item.downloadKey || ''}"
                             data-offline="${item.isOffline ? 'true' : 'false'}"
                             data-pos="${item.position}">
                            <img class="media-poster" src="${posterSrc}" alt="${item.title}" loading="lazy"/>
                            <div class="cw-play-overlay">
                                <div class="cw-play-circle">▶</div>
                            </div>
                            <button class="cw-remove-btn focusable" tabindex="0" title="Remove from Continue Watching">✕</button>
                            <div class="cw-progress-container">
                                <div class="cw-progress-fill" style="width: ${pct}%;"></div>
                            </div>
                            <div class="card-overlay cw-info">
                                <span class="card-match" style="background:#e50914;color:#fff;">${timeLeftMin}m left</span>
                                <span class="card-title">${item.title}</span>
                                <span style="font-size:11px;color:#aaa;">${subtitle}</span>
                            </div>
                        </div>
                    `;
                }).join("")}
            </div>
        `;

        // Clear All button
        const clearBtn = rowContainer.querySelector(".clear-continue-btn");
        if (clearBtn) {
            clearBtn.addEventListener("click", () => {
                localStorage.removeItem("aura_continue_watching");
                this.renderContinueWatchingRow();
            });
        }

        // Card click -> Resume directly!
        rowContainer.querySelectorAll(".cw-card").forEach(card => {
            const cardId = card.dataset.id;
            const season = card.dataset.season ? parseInt(card.dataset.season, 10) : null;
            const episode = card.dataset.episode ? parseInt(card.dataset.episode, 10) : null;
            const isOffline = card.dataset.offline === "true";
            const downloadKey = card.dataset.key;
            const pos = parseInt(card.dataset.pos, 10) || 0;

            const removeBtn = card.querySelector(".cw-remove-btn");
            if (removeBtn) {
                removeBtn.addEventListener("click", (e) => {
                    e.stopPropagation();
                    this.removeContinueWatching(cardId, season, episode);
                });
            }

            card.addEventListener("click", (e) => {
                if (e.target.closest(".cw-remove-btn")) return;
                const item = list.find(i => {
                    const iKey = i.season ? `${i.id}_s${i.season}_e${i.episode}` : `${i.id}`;
                    const targetKey = season ? `${cardId}_s${season}_e${episode}` : `${cardId}`;
                    return iKey === targetKey;
                });
                if (!item) return;

                if (isOffline && downloadKey && window.Downloader) {
                    window.Downloader.playOffline(downloadKey);
                } else if (window.Player) {
                    window.Player.play(item, season, episode, pos);
                }
            });
        });
    }
};

window.App = App;
window.addEventListener("DOMContentLoaded", () => App.init());
