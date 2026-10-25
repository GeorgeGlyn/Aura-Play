// TMDB API Service for Aura Play
const TMDB = {
    async request(endpoint, params = {}) {
        const url = new URL(`${CONFIG.TMDB_BASE_URL}${endpoint}`);
        url.searchParams.append("api_key", CONFIG.TMDB_API_KEY);
        url.searchParams.append("language", "en-US");
        for (const key in params) {
            url.searchParams.append(key, params[key]);
        }

        try {
            const res = await fetch(url.toString());
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return await res.json();
        } catch (err) {
            console.error(`TMDB error for ${endpoint}:`, err);
            return null;
        }
    },

    getPosterUrl(path, size = "w500") {
        if (!path) return "assets/icon.png";
        return size === "original" 
            ? `${CONFIG.TMDB_IMAGE_ORIGINAL}${path}` 
            : `${CONFIG.TMDB_IMAGE_W500}${path}`;
    },

    getBackdropUrl(path) {
        if (!path) return "";
        return `${CONFIG.TMDB_IMAGE_W780}${path}`;
    },

    isMediaAvailable(item) {
        if (!item) return false;
        const type = item.media_type || (item.title ? "movie" : (item.name ? "tv" : ""));
        if (type !== "movie" && type !== "tv") return false;

        // 1. Must have a valid poster image (excludes stubs & broken database entries)
        if (!item.poster_path) return false;

        // 2. Must have a confirmed release or air date
        const dateStr = item.release_date || item.first_air_date;
        if (!dateStr) return false;

        const releaseDate = new Date(dateStr);
        const now = new Date();
        // Exclude upcoming / future unreleased titles (e.g. Jailer 2 in 2026/2027)
        if (isNaN(releaseDate.getTime()) || releaseDate > now) {
            return false;
        }

        // 3. Filter spam entries with '+' in name or 'HD+Movie'
        const title = (item.title || item.name || "");
        if (title.includes("+") || title.toLowerCase().includes("hd movie")) {
            return false;
        }

        const daysSinceRelease = (now - releaseDate) / (1000 * 60 * 60 * 24);
        const votes = item.vote_count || 0;

        // 4. Theatrical movies take at least 45 days to become available on digital OTT/VOD streaming scrapers.
        // If a title's theatrical date was less than 45 days ago, require at least 30 verified votes
        // proving an international/digital release is actively circulating.
        if (daysSinceRelease < 45 && votes < 30) {
            return false;
        }

        // 5. Exclude stubs with 0 votes and very low popularity
        if (votes === 0 && (item.popularity || 0) < 3.0) {
            return false;
        }

        return true;
    },

    filterAvailable(items = []) {
        if (!Array.isArray(items)) return [];
        return items.filter(item => this.isMediaAvailable(item));
    },

    async getTrendingAll() {
        const data = await this.request("/trending/all/day");
        return this.filterAvailable(data?.results || []);
    },

    async getPopularMovies() {
        const data = await this.request("/movie/popular");
        return this.filterAvailable(data?.results || []);
    },

    async getTopRatedMovies() {
        const data = await this.request("/movie/top_rated");
        return this.filterAvailable(data?.results || []);
    },

    async getPopularTV() {
        const data = await this.request("/tv/popular");
        return this.filterAvailable(data?.results || []);
    },

    async getTopRatedTV() {
        const data = await this.request("/tv/top_rated");
        return this.filterAvailable(data?.results || []);
    },

    async getMoviesByGenre(genreId) {
        const data = await this.request("/discover/movie", {
            with_genres: genreId,
            sort_by: "popularity.desc"
        });
        return this.filterAvailable(data?.results || []);
    },

    async getTvByGenre(genreId) {
        const data = await this.request("/discover/tv", {
            with_genres: genreId,
            sort_by: "popularity.desc"
        });
        const items = (data?.results || []).map(item => ({ ...item, media_type: "tv" }));
        return this.filterAvailable(items);
    },

    async search(query) {
        if (!query || !query.trim()) return [];
        const data = await this.request("/search/multi", {
            query: query.trim(),
            include_adult: "false"
        });
        const raw = (data?.results || []).filter(item => item.media_type === "movie" || item.media_type === "tv");
        return this.filterAvailable(raw);
    },

    async getDetails(mediaType, id) {
        return await this.request(`/${mediaType}/${id}`, {
            append_to_response: "credits,videos,recommendations"
        });
    },

    async getSeason(tvId, seasonNumber) {
        return await this.request(`/tv/${tvId}/season/${seasonNumber}`);
    }
};

window.TMDB = TMDB;
