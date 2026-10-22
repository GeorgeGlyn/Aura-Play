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

    async getTrendingAll() {
        const data = await this.request("/trending/all/day");
        return data?.results || [];
    },

    async getPopularMovies() {
        const data = await this.request("/movie/popular");
        return data?.results || [];
    },

    async getTopRatedMovies() {
        const data = await this.request("/movie/top_rated");
        return data?.results || [];
    },

    async getPopularTV() {
        const data = await this.request("/tv/popular");
        return data?.results || [];
    },

    async getTopRatedTV() {
        const data = await this.request("/tv/top_rated");
        return data?.results || [];
    },

    async getMoviesByGenre(genreId) {
        const data = await this.request("/discover/movie", {
            with_genres: genreId,
            sort_by: "popularity.desc"
        });
        return data?.results || [];
    },

    async search(query) {
        if (!query || !query.trim()) return [];
        const data = await this.request("/search/multi", {
            query: query.trim(),
            include_adult: "false"
        });
        return (data?.results || []).filter(item => item.media_type === "movie" || item.media_type === "tv");
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
