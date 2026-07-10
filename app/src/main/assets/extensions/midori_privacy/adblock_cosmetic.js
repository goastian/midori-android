(function () {
    var host = String(location.hostname || '').toLowerCase();
    if (host === 'youtube.com' || host.endsWith('.youtube.com') ||
            host === 'youtu.be' || host.endsWith('.youtu.be') ||
            host === 'astiango.com' || host.endsWith('.astiango.com') ||
            host === 'astian.org' || host.endsWith('.astian.org')) {
        return;
    }

    if (window.__midoriPrivacyAdBlockerInstalled) {
        if (window.__midoriPrivacyRunAdBlocker) {
            window.__midoriPrivacyRunAdBlocker();
        }
        return;
    }

    window.__midoriPrivacyAdBlockerInstalled = true;

    var css = [
        'ins.adsbygoogle',
        'iframe[id^="google_ads_iframe_"]',
        'iframe[src*="doubleclick"]',
        'iframe[src*="googlesyndication"]',
        'iframe[src*="googleadservices"]',
        'iframe[src*="googletagservices"]',
        'iframe[src*="taboola"]',
        'iframe[src*="outbrain"]',
        'a[href^="https://ad.doubleclick.net/"]',
        'a[href^="https://adclick.g.doubleclick.net/"]',
        'a[href^="https://pubads.g.doubleclick.net/"]',
        'a[href^="https://www.googleadservices.com/pagead/aclk?"]',
        '[id^="ad-"]',
        '[id^="ad_"]',
        '[id*="-ad-"]',
        '[id*="_ad_"]',
        '[id*="adslot"]',
        '[id*="ad-slot"]',
        '[id*="outbrain"]',
        '[id*="taboola"]',
        '[class~="ad"]',
        '[class~="ads"]',
        '[class^="ad-"]',
        '[class^="ad_"]',
        '[class*=" ad-"]',
        '[class*=" ad_"]',
        '[class*=" ads-"]',
        '[class*=" ads_"]',
        '[class*="ad-slot"]',
        '[class*="adSlot"]',
        '[class*="ad_container"]',
        '[class*="ad-container"]',
        '[class*="ad__"]',
        '[class*="ads__"]',
        '[class*="advertisement"]',
        '[class*="dfp"]',
        '[class*="sponsor"]',
        '[class*="sponsored"]',
        '[class*="outbrain"]',
        '[class*="taboola"]',
        '[data-ad]',
        '[data-ad-slot]',
        '[data-ad-client]',
        '[data-testid*="ad-"]',
        '[data-testid*="advert"]',
        '[aria-label="Advertisement"]',
        '.GoogleDoubleClick-SponsorText',
        '.doubleClickAd',
        '.doubleclickAds',
        '.OUTBRAIN',
        '.Outbrain',
        '.outbrain',
        '.outbrain-ad-slot',
        '.outbrain-ads',
        '.outbrain-widget',
        '.outbrain-wrapper',
        '.TaboolaMoreToExplore_taboolaContainerWrapper__Ccf_j',
        '.ad-feedback__modal',
        '.ad-slot-dynamic',
        '.ad-slot-header__wrapper',
        '.ad-slot__ad-wrapper',
        '.stack__ads',
        '.zone__ads',
        '#js-outbrain-rightrail-ads-module',
        '#partner-zone',
        '#sponsored-outbrain-1',
        '[data-zone-label="Paid Partner Content"]'
    ].join(',') + '{display:none!important;visibility:hidden!important;}';

    function ensureStyle() {
        if (!document.documentElement) {
            return;
        }
        var style = document.getElementById('midori-privacy-adblock-css');
        if (!style) {
            style = document.createElement('style');
            style.id = 'midori-privacy-adblock-css';
            (document.head || document.documentElement).appendChild(style);
        }
        style.textContent = css;
    }

    window.__midoriPrivacyRunAdBlocker = function () {
        ensureStyle();
    };

    ensureStyle();
    window.__midoriPrivacyRunAdBlocker();

})();
