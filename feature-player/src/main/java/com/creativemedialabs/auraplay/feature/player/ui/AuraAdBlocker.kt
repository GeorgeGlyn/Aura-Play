package com.creativemedialabs.auraplay.feature.player.ui

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Intelligent Ad-Blocker Engine for AuraPlay web video streaming.
 * Intercepts video pre-roll/mid-roll ad requests (VAST, VPAID, IMA SDK),
 * blocks popups and clickjacking overlays, and prevents redirect hijacks.
 */
object AuraAdBlocker {

    private val AD_DOMAINS = hashSetOf(
        // Video Ad Networks & VAST / IMA / VPAID
        "imasdk.googleapis.com",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "googleads.g.doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adinplay.com",
        "vdo.ai",
        "aniview.com",
        "playwire.com",
        "brid.tv",
        "spotxchange.com",
        "spotx.tv",
        "springserve.com",
        "tremorhub.com",
        "streamrail.com",
        "teads.tv",
        "teads.com",
        "connatix.com",
        "undertone.com",
        "innovid.com",

        // Popups, Popunders & Aggressive Ad Networks
        "adsterra.com",
        "popcash.net",
        "popads.net",
        "propellerads.com",
        "monetag.com",
        "hilltopads.com",
        "exoclick.com",
        "adcash.com",
        "yllix.com",
        "onclick.com",
        "trafficfactory.biz",
        "trafficjunky.com",
        "admaven.com",
        "ad-maven.com",
        "clickadu.com",
        "adtrue.com",
        "vidoomy.com",
        "histats.com",
        "bet365.com",
        "1xbet.com",
        "melbet.com",
        "parimatch.com",
        "mostbet.com",
        "stake.com",

        // Banner & Display Ad Networks
        "doubleclick.net",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "rubiconproject.com",
        "mgid.com",
        "infolinks.com",
        "bidswitch.net",
        "pubmatic.com",
        "smartadserver.com",
        "openx.net",
        "revcontent.com",
        "zergnet.com",
        "adroll.com",

        // Tracking & Telemetry
        "clarity.ms",
        "hotjar.com",
        "scorecardresearch.com",
        "moatads.com",
        "integralads.com",
        "iasds01.com"
    )

    private val AD_KEYWORDS = listOf(
        // Video Ad & VAST / VPAID signatures
        "/vast",
        "vast.xml",
        "vpaid",
        "vpaid.js",
        "ad_tag",
        "adtag=",
        "adserver",
        "preroll",
        "midroll",
        "postroll",
        "delivery.php",
        "adclick",
        "adtrack",
        "clicktag",
        "/ads.js",
        "adsbygoogle",

        // Popups & overlays
        "/popunder",
        "/popads",
        "pop.js",
        "popup.js",
        "popcash",
        "adsterra",
        "propellerads",
        "monetag",
        "exoclick",
        "hilltopads",
        "histats"
    )

    /**
     * Determines whether a given URL is an ad, tracker, popup, or video ad server.
     */
    fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        for (domain in AD_DOMAINS) {
            if (lower.contains(domain)) {
                return true
            }
        }
        for (keyword in AD_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true
            }
        }
        return false
    }

    /**
     * Creates an empty, successful response to silently neutralize blocked requests.
     */
    fun createEmptyResourceResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Returns a JavaScript snippet to inject into the WebView.
     * Blocks window.open, removes ad DOM elements and transparent clickjacking overlays,
     * and forces video players to skip pre-roll video ads immediately.
     */
    fun getAdBlockScript(): String {
        return """
            (function() {
                if (window.__auraAdBlockerInstalled) return;
                window.__auraAdBlockerInstalled = true;

                // 1. Block window.open completely (kills 100% of popup clicks)
                try {
                    window.open = function() { 
                        console.log('[AuraAdBlock] Intercepted popup window.open'); 
                        return null; 
                    };
                } catch(e) {}

                // 2. Prevent alerts / prompts from scam / ad scripts
                try {
                    window.alert = function() {};
                    window.confirm = function() { return false; };
                    window.prompt = function() { return null; };
                } catch(e) {}

                // 3. Inject CSS to hide ad containers and popups
                try {
                    const style = document.createElement('style');
                    style.type = 'text/css';
                    style.innerHTML = `
                        iframe[src*="ad"], iframe[src*="pop"], iframe[src*="banner"], iframe[src*="traffic"],
                        .jw-ad-ui, .vjs-ad-playing, .video-ad, .ad-container, #ad-container,
                        .ad-overlay, .preroll-blocker, .adzerk, .google-anno-skip,
                        div[class*="ad-"], div[id*="ad-"], div[class*="popup"], div[id*="popup"],
                        div[class*="banner"], div[id*="banner"], div[class*="adbox"], div[id*="adbox"] {
                            display: none !important;
                            visibility: hidden !important;
                            pointer-events: none !important;
                            width: 0 !important;
                            height: 0 !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                } catch(e) {}

                // 4. Neutralize clickjacking transparent overlays & skip HTML5 video ads
                function sanitizePage() {
                    try {
                        const adSelectors = [
                            'iframe[src*="ad"]', 'iframe[src*="pop"]', 'iframe[src*="banner"]',
                            'div[class*="ad-"]', 'div[class*="ads"]', 'div[id*="ad-"]', 'div[id*="ads"]',
                            'div[class*="popup"]', 'div[id*="popup"]', 'div[class*="banner"]', 'div[id*="banner"]',
                            '.jw-ad-ui', '.vjs-ad-playing', '.video-ad', '.ad-container', '#ad-container',
                            '.preroll-blocker', '.ad-overlay',
                            'a[href*="bet365"]', 'a[href*="adsterra"]', 'a[href*="popcash"]', 'a[href*="1xbet"]'
                        ];
                        adSelectors.forEach(function(sel) {
                            document.querySelectorAll(sel).forEach(function(el) {
                                el.remove();
                            });
                        });

                        // Remove full-screen clickjacking overlay divs
                        document.querySelectorAll('div').forEach(function(el) {
                            const style = window.getComputedStyle(el);
                            if (style.position === 'fixed' || style.position === 'absolute') {
                                const zIndex = parseInt(style.zIndex, 10);
                                if (zIndex >= 99) {
                                    if (!el.querySelector('video') && !el.querySelector('canvas') && !el.querySelector('.jw-media') && !el.querySelector('.vjs-tech') && !el.querySelector('.plyr')) {
                                        const opacity = parseFloat(style.opacity);
                                        if (opacity < 0.1 || style.backgroundColor === 'transparent' || style.backgroundColor === 'rgba(0, 0, 0, 0)') {
                                            el.remove();
                                        }
                                    }
                                }
                            }
                        });

                        // Immediately skip HTML5 video ads
                        document.querySelectorAll('video').forEach(function(v) {
                            if (v.closest('.jw-ad-ui') || v.closest('.ad-container') || v.closest('.vjs-ad-playing') || v.classList.contains('ad-video')) {
                                v.currentTime = v.duration || 999;
                                v.muted = true;
                            }
                        });
                    } catch(e) {}
                }

                sanitizePage();
                setInterval(sanitizePage, 400);
            })();
        """.trimIndent()
    }
}
