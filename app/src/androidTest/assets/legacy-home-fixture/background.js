(async () => {
  await browser.storage.local.set({midori_bookmarks: {Personal: [{id: "kept-site", title: "Kept shortcut", url: "https://example.org/kept"}]}, midori_bookmark_categories: ["Personal"], notesStore: {text: "Keep my note"}});
  localStorage.setItem("tabStore", JSON.stringify({showSpeedDials: false, background: {type: "Local", id: "fixture-image"}}));
  const opening = indexedDB.open("midori-wallpapers", 1);
  opening.onupgradeneeded = () => opening.result.createObjectStore("images");
  opening.onsuccess = () => {
    const db = opening.result;
    const transaction = db.transaction("images", "readwrite");
    transaction.objectStore("images").put(new Blob(["retained image"], {type: "image/png"}), "fixture-image");
    transaction.oncomplete = () => { db.close(); browser.storage.local.set({fixtureReady: true}); };
  };
})();
