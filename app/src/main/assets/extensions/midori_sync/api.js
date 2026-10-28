/* global ExtensionAPI, ChromeUtils */
this.midoriSyncStorage = class extends ExtensionAPI {
  getAPI() {
    const { FormHistory } = ChromeUtils.importESModule("resource://gre/modules/FormHistory.sys.mjs");
    const fields = ["fieldname", "value", "firstUsed", "lastUsed", "timesUsed"];
    const sensitive = /^(?:password|passwd|pwd|passphrase|currentpassword|newpassword|otp|onetimecode|ccnumber|cccsc|cccvv|cvv|cvc|accesstoken|refreshtoken|csrftoken)$/;
    function allowed(row) {
      return typeof row.fieldname === "string" && row.fieldname.length > 0 && row.fieldname.length <= 200 &&
        typeof row.value === "string" && row.value.length > 0 && row.value.length <= 200 &&
        !/[\p{Cc}\p{Cs}]/u.test(row.fieldname + row.value) &&
        !sensitive.test(row.fieldname.toLowerCase().replace(/[^a-z0-9]/g, "")) &&
        FormHistory.isAllowedEntry(row.fieldname, row.value);
    }
    async function rows(query = {}) {
      const result = await FormHistory.search(["guid", ...fields], query);
      if (result.length > 8192) throw new Error("form_history_capacity");
      return result.filter(allowed);
    }
    return { midoriSyncStorage: { async request(json) {
      if (json.length > 262144) throw new Error("request_too_large");
      const request = JSON.parse(json);
      if (request.action === "list") return JSON.stringify(await rows());
      if (request.action !== "apply" || !allowed(request.identity)) throw new Error("invalid_request");
      const identity = { fieldname: request.identity.fieldname, value: request.identity.value };
      const current = await rows(identity);
      if (request.value === null) {
        for (const row of current) await FormHistory.update({ op: "remove", guid: row.guid });
      } else {
        const value = request.value;
        if (!allowed(value) || value.fieldname !== identity.fieldname || value.value !== identity.value ||
            !Number.isSafeInteger(value.firstUsed) || value.firstUsed < 1 ||
            !Number.isSafeInteger(value.lastUsed) || value.lastUsed < value.firstUsed ||
            !Number.isSafeInteger(value.timesUsed) || value.timesUsed < 1) throw new Error("invalid_record");
        const update = Object.fromEntries(fields.map(key => [key, value[key]]));
        if (current.length) {
          await FormHistory.update({ op: "update", guid: current[0].guid, ...update });
          for (const row of current.slice(1)) await FormHistory.update({ op: "remove", guid: row.guid });
        } else {
          await FormHistory.update({ op: "add", ...update });
        }
      }
      return JSON.stringify(await rows(identity));
    } } };
  }
};
