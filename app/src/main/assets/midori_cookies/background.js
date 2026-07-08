
var midoriCookies = {};

browser.cookies
  .set({
    url: "https://astiango.com",
    domain: ".astiango.com",
    name: "omnibar",
    value: "1",
    sameSite: "no_restriction"
  })
  .then((cookie) => console.log(`MIDORI_COOKIES - omnibar cookie set ${JSON.stringify(cookie)}`))
  .catch((err) => console.log("MIDORI_COOKIES - could not set omnibar cookie: ", err));

browser.cookies
    .set({
      url: "https://www.astiangokids.com",
      domain: ".astiangokids.com",
      name: "omnibar",
      value: "1",
      sameSite: "no_restriction"
    })
    .then((cookie) => console.log(`MIDORI_COOKIES - omnibar cookie set for junior ${JSON.stringify(cookie)}`))
    .catch((err) => console.log("MIDORI_COOKIES - could not set omnibar cookie: ", err));

let nativePort = browser.runtime.connectNative("midori_browser");
nativePort.onMessage.addListener(message => {
  console.log(`MIDORI_COOKIES - message received from native app: ${JSON.stringify(message)}`);
  if (message['code'] == "restore_cookies") {
    restoreMidoriCookies();
  }
});
nativePort.postMessage('{code: "connection_established"}');

function saveCookie(cookie) {
  if (cookie.domain != "auth.qwant.com") {
    console.log(`MIDORI_COOKIES - save ${JSON.stringify(cookie)}`);
    if (cookie.name == "ory_kratos_session") {
      nativePort.postMessage('{code: "user_logged", value: true}');
      console.log("MIDORI_COOKIES - User is logged in !!!!!");
    }
    midoriCookies[cookie.name] = cookie;
  } else {
    console.log(`MIDORI_COOKIES - ignoring cookie from auth.qwant.com domain`);
  }
}

function deleteCookie(cookie) {
  console.log(`MIDORI_COOKIES - delete ${JSON.stringify(cookie)}`);
  if (cookie.name == "ory_kratos_session") {
    nativePort.postMessage('{code: "user_logged", value: false}');
    console.log("MIDORI_COOKIES - User is logged out !!!!!");
  }
  delete midoriCookies[cookie.name];
}

function restoreMidoriCookies() {
  try {
    console.log("MIDORI_COOKIES - restore all qwant cookies");
    let promises = []
    for (const cookie of Object.values(midoriCookies)) {
      console.log(`MIDORI_COOKIES - restoring ${JSON.stringify(cookie)}`);
      promises.push(browser.cookies.set({
        url: "https://astiango.com",
        name: cookie.name,
        value: cookie.value,
        domain: cookie.domain,
        expirationDate: cookie.expirationDate,
        firstPartyDomain: cookie.firstPartyDomain,
        httpOnly: cookie.httpOnly,
        partitionKey: cookie.partitionKey,
        path: cookie.path,
        sameSite: cookie.sameSite,
        secure: cookie.secure,
        storeId: cookie.storeId
      }));
    }
    Promise.all(promises)
      .then((cookies) => {
        console.log(`MIDORI_COOKIES - restoring all cookies done`);
        nativePort.postMessage('{code: "restored", value: true}');
      })
      .catch((err) => {
          console.error("MIDORI_COOKIES - could not restore cookie: ", err);
          nativePort.postMessage('{code: "restored", value: false}');
      });
  } catch(error) {
    console.error("MIDORI_COOKIES - could not restore cookies: ", error);
    nativePort.postMessage('{code: "restored", value: false}');
  }
}

browser.cookies
  .getAll({
    domain: ".astian.org"
  })
  .then((cookies) => {
    for (const cookie of cookies) {
      console.log(`MIDORI_COOKIES - retrieving ${JSON.stringify(cookie)}`);
      saveCookie(cookie);
    }
  })
  .catch((err) => console.log("MIDORI_COOKIES - could not retrieve midori cookies: ", err));

browser.cookies.onChanged.addListener((changeInfo) => {
  if (changeInfo.cause == "explicit") {
    console.log(`MIDORI_COOKIES - Cookie changed ${JSON.stringify(changeInfo.cookie)}`)
    if (changeInfo.removed) {
      deleteCookie(changeInfo.cookie)
    } else {
      saveCookie(changeInfo.cookie);
    }
  }
});

