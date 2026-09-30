# Chronio playlists — design (plan C)

Status: proposal for review. Scope: Android TV first; desktop follows the same model.

## Goal

Watch movies and episodes from different shows in a curated order (Chronolists,
the Star Trek Viewing Guide, later your own lists) with everything Nuvio already
does for normal titles: streams from your stream addons, intro/outro skip, Trakt
scrobbling and watched state, resume, Continue Watching.

The previous approach (a synthetic `chronio:<list>` series whose videos carry a
`playbackIdentity`) makes fake episodes pass for real ones and needs a hook at
every place Nuvio reads an ID. Plan C inverts it: a **playlist is an ordered list
of references to real titles**. Playing an entry opens the real title through the
normal path; the only new concept is *what plays next*.

## 1. Playlist feed (server)

`chronio-catalog` becomes a plain feed publisher. No Stremio protocol, no
metadata, no secrets beyond its TMDb key.

`GET /playlists.json` — index:

```json
{ "version": 1,
  "playlists": [
    { "id": "star-trek", "name": "Star Trek", "description": "…",
      "image": "https://…", "entries": 904, "source": "chronolists",
      "updatedAt": "2026-09-30T02:54:00Z", "url": "/playlists/star-trek.json" } ] }
```

`GET /playlists/<id>.json` — one playlist:

```json
{ "version": 1, "id": "star-trek", "name": "Star Trek", "description": "…", "image": "…",
  "entries": [
    { "key": "series:tt0244365:1:1", "type": "series", "id": "tt0244365",
      "season": 1, "episode": 1, "show": "Star Trek: Enterprise", "title": "Broken Bow" },
    { "key": "movie:tt0079945", "type": "movie", "id": "tt0079945",
      "title": "Star Trek: The Motion Picture" } ] }
```

- `id` is always an IMDb id; `season`/`episode` are the **real** coordinates of
  the item actually played (source corrections such as the BSG miniseries →
  S0E1–2 are applied server-side, as today).
- `key` is stable across reorders (today's `stableId` minus the list prefix) and
  is what the app stores playlist position against.
- `show`/`title` are display hints only; artwork and details come from the
  user's metadata addons (AIOMetadata/Cinemeta) exactly as for any title.
- Existing refresh, caching, per-list fallback and health reporting are kept.
  The Stremio `catalog`/`meta` routes stay until both apps ship playlists, then
  are removed.

The curated lists (Chronolists, the Star Trek Viewing Guide) live in this feed;
Trakt lists are a separate source for playlists you or others curate on Trakt
(§6).

## 2. App model (TV)

New package `com.nuvio.tv.core.playlist`:

- `Playlist(id, name, description, image, entries, source)` and
  `PlaylistEntry(key, type, id, season?, episode?, show?, title?)` with
  `videoId` = `id` or `id:season:episode`.
- `PlaylistSource` — sealed: `FeedPlaylistSource(indexUrl)` and
  `TraktPlaylistSource(listId)` (§6). Both produce the same `Playlist`, so
  browsing, playback and next-up never care where a list came from.
- `PlaylistRepository` — fetches and caches the index and playlists (OkHttp, the
  same pattern as `CollectionManagementViewModel.fetchUrl`); offline falls back
  to the last good copy.
- `PlaylistSettingsDataStore` — per-profile list of configured sources (one feed
  URL to start: `https://ambulance.tailbba64e.ts.net:7443/playlists.json`).

Playlists are a **standalone feature, not a Collection source**. The Collections
mapping showed rows are `MetaPreview` (movie/show only, no episode, no
position), items open the show's detail page, "All" tabs de-duplicate by show
id, and ~20 sites switch on source type across the editor, web config server and
sync. Bending that to ordered episode-level entries would touch the most
actively developed upstream code. A separate feature touches a handful of files
and rebases cleanly.

## 3. UI

- **Home row "Playlists"**: one card per playlist (image, name, "12 / 904"
  progress). Added via the existing home row pipeline as a new `HomeRow` type,
  toggleable like other rows.
- **Playlist screen** (`Screen.Playlist(id)`): header with *Continue* (next
  unwatched entry after the last played) and *Start from beginning*; a vertical
  list of numbered entries showing show name, SxE, title, still/poster (fetched
  lazily from the meta addons and cached), and a watched tick.
- Watched ticks come from the real watched state (`WatchedItemsPreferences` /
  Trakt), so episodes watched outside the playlist show as watched.

## 4. Playback and "next"

Selecting an entry navigates to the **Stream screen with the real title**
(`contentId = id`, `contentType`, `videoId`, `season`, `episode`) plus a new
route argument `playlistToken`. Nothing downstream needs translating: streams,
subtitles, skip segments, scrobbling and resume already work for real IDs.

`playlistToken` follows the existing `cloudSessionToken` precedent
(`CloudLibraryPlaybackSessionStore`): an in-memory map mirrored to
SharedPreferences holding `PlaylistSession(playlistId, entryIndex)`. The Stream
screen passes it to the player route.

In the player (`PlayerRuntimeController` reads the session at init):

- `recomputeNextEpisode`: when a session is present, `nextEpisode` is the next
  playlist entry, including movies and other shows. The next-episode card reads
  "Up next in Star Trek: Star Trek: Enterprise S1E4 · Unexpected".
- Movies in a playlist use the next-entry card instead of post-play
  recommendations.
- Advancing:
  - **Same show** (e.g. Enterprise S1E3 → S1E4): keep today's in-player switch
    (`switchToEpisodeStream`), which only changes video/season/episode.
  - **Different title**: `contentId`/`contentType`/artwork are immutable for the
    player's lifetime, so the player exits and relaunches through the existing
    `onPlaybackEnded` → Stream screen path (`NuvioNavHost.kt:954`), made
    playlist-aware to pass the next entry's real `contentId`/type and the token.
    Autoplay keeps working through the Stream screen's auto-select
    (`autoPlayNav`) with the current binge group as a preference.
  - The session's `entryIndex` is advanced on each switch, and "Still watching?"
    and the autoplay streak apply as they do today.

## 5. Progress and Continue Watching

- Resume and watched state are native (stored under the real IDs), so nothing
  new is needed for the entry itself.
- `PlaylistProgressStore` (per profile): `lastPlayedKey`, `updatedAt` per
  playlist. It drives *Continue* and the home card's progress.
- **Continue Watching**: an in-progress entry already appears (it is a normal
  title). To surface *the next playlist entry* after one finishes, add a
  `ContinueWatchingItem.PlaylistNext(playlistId, entry)` built from
  `PlaylistProgressStore`. It is a separate item type because `NextUpInfo` needs
  non-null season/episode and cannot represent movies. It is suppressed while
  the real show's own Next Up already points at the same episode.

## 6. Trakt lists as playlists

Any Trakt list can be opened as a playlist, keeping its mixed movie and episode
order exactly. The existing Collections resolver cannot do this: it requests
`/lists/{id}/items/movie` and `/items/show` separately, so episodes are dropped
and the interleaving of movies and shows is lost.

- **Adding**: paste a Trakt list URL or id (reusing
  `TraktPublicListSourceResolver.parseTraktListPath` and `listImportMetadata`),
  pick from your own lists (`/users/me/lists`, authenticated), or open one from
  Trakt search. Private lists need the Trakt login; public lists work with just
  the app's client id.
- **Fetching**: `GET /lists/{id}/items` with **no type filter** and
  `extended=full`, paging through `X-Pagination-Page-Count` until all items are
  loaded. One request stream keeps every type in the same sequence.
- **Order**: the list's own `sort_by`/`sort_how` from `GET /lists/{id}` (the
  order the owner chose on trakt.tv, usually `rank`), applied to the whole
  mixed list. `rank` ties break by `listed_at`. Never sort per type.
- **Mapping each item to entries, in place**:
  - `movie` → one movie entry.
  - `episode` → one episode entry (`show.ids.imdb` + `episode.season`/`number`).
  - `season` → that season's episodes in order, expanded at the season's
    position (episode list from the metadata addon, cached).
  - `show` → the whole show's aired episodes in order, specials excluded,
    expanded at the show's position. Shown as one collapsible group on the
    playlist screen so a 200-episode show doesn't swamp the list.
  - `person` and items with no IMDb id → skipped and counted in a
    "N items not playable" note, so gaps are never silent.
- **Keys** are Trakt's own item id (`id` on the list item) plus, for expansions,
  `:S:E`. Reordering on trakt.tv keeps progress attached to the right entry.
- **Freshness**: re-fetched when the playlist screen opens (with the cached copy
  shown immediately) and at most hourly in the background, via `updated_at` from
  `GET /lists/{id}`.

## 7. Local lists (later)

Lists edited inside the app, using the same `Playlist` model.

## 8. Phases

1. **Feed**: `/playlists.json` and `/playlists/<id>.json` in chronio-catalog, with
   tests. Keep the Stremio routes for now.
2. **TV browse**: repository, settings (feed URL), home row, playlist screen, and
   entry → Stream screen with the real title. No playlist-aware next yet. This is
   already usable, with native streams, skip, Trakt and resume.
3. **TV next**: sessions, playlist-aware next card and autoplay (same-show
   in-player; cross-title relaunch), movie handling, `PlaylistProgressStore`.
4. **Continue Watching** `PlaylistNext`.
5. **Trakt-list playlists** (§6) on TV.
6. **Desktop** port of 2–5.
7. **Retire the synthetic approach**: remove the `playbackIdentity` hooks from
   both forks and the Stremio routes from chronio-catalog, which returns the
   forks closer to upstream.
8. Local lists.

## Open questions

- Should local watch progress from the synthetic lists be migrated? It is keyed
  by `chronio:<list>` S1E<position>; Trakt already has the real items from
  scrobbles. Proposal: no migration; playlist position starts from the first
  entry not marked watched.
- Episode stills: fetch per entry from the metadata addon when the playlist
  screen scrolls (cached), or add optional `image` hints to the feed from TMDb
  (the server already queries TMDb seasons). Proposal: feed hints, since they're
  cheaper on TV hardware.
