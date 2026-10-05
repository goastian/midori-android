package org.midorinext.android.pwa

data class WebAppSuggestion(val name: String, val url: String)

/** Sites with useful standalone experiences. Offline access depends on each site. */
object WebAppCatalog {
    val suggestions = listOf(
        WebAppSuggestion("Telegram", "https://web.telegram.org/"),
        WebAppSuggestion("Discord", "https://discord.com/app"),
        WebAppSuggestion("X", "https://x.com/"),
        WebAppSuggestion("Reddit", "https://www.reddit.com/"),
        WebAppSuggestion("Instagram", "https://www.instagram.com/"),
        WebAppSuggestion("YouTube Music", "https://music.youtube.com/"),
        WebAppSuggestion("Spotify", "https://open.spotify.com/"),
        WebAppSuggestion("SoundCloud", "https://soundcloud.com/"),
        WebAppSuggestion("Twitch", "https://www.twitch.tv/"),
        WebAppSuggestion("Netflix", "https://www.netflix.com/"),
        WebAppSuggestion("Figma", "https://www.figma.com/"),
        WebAppSuggestion("Canva", "https://www.canva.com/"),
        WebAppSuggestion("Excalidraw", "https://excalidraw.com/"),
        WebAppSuggestion("Outlook", "https://outlook.live.com/"),
        WebAppSuggestion("Notion", "https://www.notion.so/"),
        WebAppSuggestion("Trello", "https://trello.com/"),
        WebAppSuggestion("Asana", "https://asana.com/"),
        WebAppSuggestion("GitHub", "https://github.com/"),
        WebAppSuggestion("GitLab", "https://gitlab.com/"),
        WebAppSuggestion("Stack Overflow", "https://stackoverflow.com/"),
        WebAppSuggestion("Hacker News", "https://news.ycombinator.com/"),
        WebAppSuggestion("Medium", "https://medium.com/"),
        WebAppSuggestion("DEV Community", "https://dev.to/"),
        WebAppSuggestion("LinkedIn", "https://www.linkedin.com/"),
        WebAppSuggestion("Dropbox", "https://www.dropbox.com/"),
        WebAppSuggestion("Microsoft 365", "https://www.office.com/"),
        WebAppSuggestion("Google Calendar", "https://calendar.google.com/"),
        WebAppSuggestion("Google Maps", "https://www.google.com/maps/"),
        WebAppSuggestion("Google Translate", "https://translate.google.com/"),
        WebAppSuggestion("ChatGPT", "https://chatgpt.com/"),
        WebAppSuggestion("Google Gemini", "https://gemini.google.com/"),
        WebAppSuggestion("Photopea", "https://www.photopea.com/"),
        WebAppSuggestion("Squoosh", "https://squoosh.app/"),
        WebAppSuggestion("Draw.io", "https://app.diagrams.net/"),
        WebAppSuggestion("Ecosia", "https://www.ecosia.org/"),
        WebAppSuggestion("Wikipedia", "https://www.wikipedia.org/"),
        WebAppSuggestion("Duolingo", "https://www.duolingo.com/"),
        WebAppSuggestion("Chess.com", "https://www.chess.com/"),
        WebAppSuggestion("Lichess", "https://lichess.org/"),
        WebAppSuggestion("GeoGuessr", "https://www.geoguessr.com/"),
    )
}
