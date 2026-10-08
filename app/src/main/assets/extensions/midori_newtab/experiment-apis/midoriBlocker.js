/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

"use strict";

/* global ExtensionAPI, Services */

const PREF_BLOCKER_ENABLED = "midori.blocker.enabled";
const BLOCKER_SERVICE_URI = "resource:///modules/MidoriBlockerService.sys.mjs";

function safeCount(value) {
  const count = Number(value);
  return Number.isFinite(count) && count > 0 ? Math.trunc(count) : 0;
}

this.midoriBlocker = class extends ExtensionAPI {
  getAPI() {
    const { tabManager } = this.extension;

    function browserIdForTabId(tabId) {
      try {
        const tab = tabManager.get(Number(tabId) || 0);
        return Number(tab?.browser?.browsingContext?.top?.browserId || 0);
      } catch (_) {
        return 0;
      }
    }

    return {
      midoriBlocker: {
        async getStatsSummary(tabId) {
          let service;
          try {
            service = ChromeUtils.importESModule(BLOCKER_SERVICE_URI)
              .MidoriBlockerService;
          } catch (err) {
            throw new Error(`MidoriBlockerService unavailable: ${err}`);
          }

          let enabled = true;
          try {
            enabled = Services.prefs.getBoolPref(PREF_BLOCKER_ENABLED, true);
          } catch (_) {
            enabled = true;
          }

          const totalBlocked = safeCount(service.getGlobalStats().totalBlocked);

          let pageBlocked = 0;
          const browserId = browserIdForTabId(tabId);
          if (browserId) {
            try {
              pageBlocked = safeCount(service.getBlockedCount(browserId));
            } catch (_) {
              pageBlocked = 0;
            }
          }

          return {
            totalBlocked,
            totalRequests: null,
            categories: {
              scripts: 0,
              frames: 0,
              xhr: 0,
              images: 0,
              media: 0,
              fonts: 0,
              other: 0,
            },
            pageBlocked,
            pageRequests: null,
            capabilities: { requestCounts: false, pageRequestCounts: false, categories: false },
            enabled,
            state: "ready",
          };
        },
      },
    };
  }
};
