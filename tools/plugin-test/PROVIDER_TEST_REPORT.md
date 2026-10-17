# Provider test (mock network, real plugin_runtime.js in headless Chromium)
60 providers x 8 calls (catalog, genres, getPosts, getSearchPosts, getMeta, getEpisodes, getStream, getStream isDownload).
Network was OFF, so every answer is a generic mock page: this tests runtime errors, not real site data.

| run | old runtime ok/fail | new runtime ok/fail |
|---|---|---|
| plain mock | 424 / 56 | 425 / 55 |
| Cloudflare-403 mock (retry with cf_clearance cookie from openWebView) | 293 / 187 | 333 / 147 |

Still failing with the new runtime (not runtime gaps; they need the real site / real ids):
- autoEmbed, torrentio, animesub, everything .getMeta: "Missing IMDb ID" (mock link has no imdb id)
- movieBoxWeb getPosts/getMeta/getStream: needs real API reply
- mkvDrama getEpisodes/getStream: "Link1 was not found" (mock page has no link)
- eonMovies getStream: expects a redirect to Dotflix/HubCloud
- ringz: expects its own JSON
- showbox getMeta/getEpisodes, luxMovies getStream: real page structure needed
- moviesApi: provider folder has only stream.js and no getStream export (broken in the Vega ZIP itself)
- movieBox getPosts/getSearchPosts: expects JSON
- "Plugin file missing x/episodes.js": provider has no episodes module (optional)
In the 403 mock, providers without any openWebView branch (vega getPosts, drive, multi, mod, ...) keep the 403: same in Vega.
