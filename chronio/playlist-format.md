# Playlist format, version 1

Status: draft. This format is kept app-neutral so it could be proposed upstream
to Nuvio unchanged.

A **playlist** is an ordered list of references to media that Nuvio can already
play: movies, episodes, or any title an installed addon serves. Its entries are
grouped into **sections**, which the app presents the way it presents a show's
seasons. Playlists are published as static JSON files. Hosting them needs no
server logic.

## Playlist sources

A user adds a **source URL**. That URL always returns an **index**, even when
the index lists a single playlist. The host can then add, rename or split
playlists without users changing anything.

```json
{
  "format": "playlist-index",
  "version": 1,
  "name": "Chronological watch orders",
  "description": "Optional.",
  "playlists": [
    { "id": "star-trek", "name": "Star Trek", "description": "…",
      "poster": "https://…", "background": "https://…", "logo": "https://…",
      "entries": 904, "updatedAt": "2026-09-30T02:54:00Z",
      "url": "star-trek.json" },
    { "id": "short-list", "name": "Inline example",
      "playlist": { "…": "a complete playlist object, see below" } }
  ]
}
```

- `url` is resolved relative to the index URL. A playlist can also be embedded
  inline under `playlist`, so a small source can be a single file.
- The index carries everything needed to show a playlist in a list or row
  (`name` and artwork, plus optional `entries` count and `updatedAt`) without
  downloading every playlist.
- `id` is unique within the index. The app identifies a playlist by
  `(source URL, id)`, which is also the key for disabling it.

## Playlist

```json
{
  "format": "playlist",
  "version": 1,
  "id": "star-trek",
  "name": "Star Trek",
  "description": "Every series and film in in-universe order.",
  "poster": "https://…", "background": "https://…", "logo": "https://…",
  "updatedAt": "2026-09-30T02:54:00Z",
  "sections": [
    {
      "title": "22nd century",
      "description": "Optional.",
      "entries": [
        { "key": "ent-1-1", "type": "series", "id": "tt0244365",
          "season": 1, "episode": 1,
          "show": "Star Trek: Enterprise", "title": "Broken Bow",
          "thumbnail": "https://…", "overview": "…", "runtime": 86,
          "released": "2001-09-26" },
        { "key": "tmp", "type": "movie", "id": "tt0079945",
          "title": "Star Trek: The Motion Picture", "thumbnail": "https://…" }
      ]
    }
  ]
}
```

### Sections

- The author decides the sections. A playlist may use one section or many.
- The app shows sections where a show shows seasons, in file order. With a
  single section the season selector is hidden, as it is for a single-season
  show.
- `title` is optional. Without it the app labels the section by position
  (for example "Part 2").

### Entries: what to play

| Field | Required | Meaning |
|---|---|---|
| `key` | yes | Unique within the playlist and stable across edits. The app stores the user's place against it, so reordering or inserting entries keeps progress. |
| `type` | yes | Addon content type, usually `movie` or `series`. Any type an addon serves is allowed. |
| `id` | yes | Content id as addons know it (`tt…` IMDb ids, or an addon's own prefix such as `kitsu:…`). Resolved through the user's installed addons, exactly like a title opened anywhere else. |
| `season`, `episode` | for episodes | Real season and episode numbers within `id`. |
| `videoId` | no | Explicit addon video id, for addons whose episode ids are not `id:season:episode`. Defaults to `id:season:episode` for episodes and `id` otherwise. |

Entries point at the real title, and playback never sees the playlist. Streams,
subtitles, skip segments, scrobbling, resume and watched state all use the real
title. Watching an episode outside a playlist therefore also counts inside it.

### Entries: how to display (optional hints)

`show`, `title`, `thumbnail`, `overview`, `runtime` (minutes) and `released`
(ISO date) let the app render the playlist immediately, before any metadata
request, and still render it when no installed addon has metadata for a title.
Metadata from the user's addons, when available, takes precedence.

## Compatibility rules

- The app ignores unknown fields. Authors may add their own fields, ideally
  prefixed with `x-`.
- A newer `version` than the app understands: the app shows the playlist name
  and says it needs an app update.
- The app skips entries it cannot play (unknown `type` or no addon for the
  `id`) and shows a count of them. It never drops them silently.
- `key` values must be unique within a playlist. The app ignores duplicates
  after the first.

## Hosting

- Serve plain JSON over HTTPS. CORS is not needed for the apps.
- The app caches the last good copy of each file and refreshes when a playlist
  is opened and periodically in the background. `ETag` / `Last-Modified` are
  honoured when present.
- Generators (for example one converting a Trakt list or a published watch
  order) run elsewhere and write these files. The app never builds playlists.
