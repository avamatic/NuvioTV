# Playlists — design

Status: proposal for review (revised 2026-09-30, replaces "plan C").
Scope: Android TV first; desktop follows the same model.
File format: [playlist-format.md](playlist-format.md).

## Goal

Playlists as a general Nuvio feature. Chronological watch orders are one use.

- A playlist autoplays cleanly across anything Nuvio can play: movies,
  episodes of different shows, and any addon's titles.
- Nuvio's features and comforts all keep working: streams from the user's
  addons, intro/outro skip, Trakt, resume, source (binge group) preference,
  "Still watching?", audio and subtitle preferences.
- Playlists look and behave as much like a TV show as possible. Sections are
  shown as seasons and entries as episodes.
- Playlists are prebuilt static JSON files. The app never builds them, and they
  are not an addon.

**Upstreaming.** We keep the option of proposing this to Nuvio open. It isn't a
hard constraint, but a change that makes upstreaming less likely is called out
when it's made. In practice: no Chronio-specific fields or hosts, additive
changes to shared components, and no default source URL.

## Principles

1. **Play the real title.** An entry opens the real title through Nuvio's
   normal path. Playback never sees the playlist, so every playback feature
   works unchanged. The only new behaviour in the player is *what plays next*.
2. **Show-like presentation, not show-like data.** Playlists reuse Nuvio's show
   components (season tabs, episode row, cards, player episodes panel, Continue
   Watching) through a playlist adapter. A playlist is never disguised as an
   addon series. Faking a series (the earlier `playbackIdentity` approach)
   needed a hook everywhere the app reads an id, and it is being retired.
3. **Progress belongs to real titles.** Watched state and resume are the
   title's own, so watching an episode anywhere counts in every playlist that
   contains it. The only playlist-specific state is the user's place in the
   playlist.

## 1. Sources and settings

- **Content & Discovery → Playlists**, beside Addons and Plugins:
  - Add or remove **source URLs**. Each URL returns an index (see the format).
  - Each source lists its playlists with an on/off toggle. Disabled playlists
    don't appear on Home or in search. Identity is `(source URL, playlist id)`.
  - Refresh action and last-updated / error state per source, like addons.
- No default source. The current hard-coded tailnet default is removed, which
  also removes an upstream blocker.
- Home: each enabled playlist appears in a **Playlists** row. The row takes part
  in *Reorder home catalogs* like any other row, reusing that existing UI.
- Caching: last good copy of each index and playlist on disk. Refresh on app
  start, when a playlist is opened, and at most hourly. Honour
  `ETag`/`Last-Modified`.

## 2. Playlist screen (the "show page")

Built from the detail screen's own composables. The whole
`MetaDetailsScreen` / `MetaDetailsContent` is not reused, because it is bound
to an addon `Meta` and its ViewModel.

- **Hero**: backdrop, logo or name, description, and counts (movies, episodes,
  total runtime). The primary button is **Resume "Broken Bow"** or **Play**, like
  a show's. The secondary button is *Start from beginning*. `HeroContentSection`
  takes a whole `Meta`, so we add a small playlist hero with the same look
  (upstream-neutral: new code, no change to the shared hero).
- **Sections as seasons**: `SeasonTabs`, with one additive, optional parameter
  for labels. Today it only shows numbers and moves 0 to the end. Hidden when
  there is one section.
- **Entries as episodes**: `EpisodesRow` and its cards. The row keys progress
  and watched state by `(season, episode)`, so the adapter gives each entry
  **display coordinates** (season = section number, episode = position in the
  section) and a unique id derived from its `key`. It fills the progress and
  watched maps from each entry's **real** title. Those coordinates exist only
  on screen; a click maps back to the entry.
- **Card text**: an episode card from another show should read
  "Star Trek: Short Treks · S1E3" above "The Brightest Star". That needs one
  optional subtitle override on the card (additive).
- **Entry options** (the existing long-press menu): mark watched or unwatched
  (on the real title), and *mark previous as watched*, which is useful for
  joining a playlist midway.
- **Unplayable entries**: shown greyed with a reason, and counted in the hero.

## 3. Playback and next

Mostly built already (`PlaylistPlaybackSession`, commit `25887179`).

- Starting an entry starts a session at that entry. The player looks up the
  playing title in the session, and the next entry replaces the show's next
  episode:
  - **Same show**: in-player switch (unchanged Nuvio path).
  - **Different title or a movie**: the player hands off to the Stream screen
    for the next title, which picks a source the way next-episode autoplay does.
- **Persisted place**: the session is stored per profile as
  `(source, playlist id, entry key)`, so it survives restarts and reorders.
  Playing a title from outside the playlist doesn't change it.
- **Player episodes panel**: in a playlist, the panel lists the playlist's
  sections and entries instead of the show's seasons. Picking an entry follows
  the same rules (same show: switch in place; otherwise hand off). Today the
  panel reads the show's episodes and can only switch episodes of the current
  show, so this is a playlist mode, not a data trick.
- **Binge group**: source preference is remembered per real show, so returning
  to Discovery prefers the source group last used for Discovery.

## 4. Continue Watching

- An in-progress entry already appears. It is a normal title, and its card
  gets "in Star Trek" context.
- After an entry finishes, the playlist's next entry appears as **Next up**.
  Episodes map onto the existing `NextUp` item with the real title, so no new
  item type is needed. Movies can't: `NextUpInfo` requires season and episode,
  and a new item type touches about 13 files. Proposal: extend `NextUpInfo` to
  allow a movie (nullable season/episode) behind a playlist flag. **Upstream
  note:** this changes a shared model, so it needs care.
- Launching a playlist's Next up restores the session, so autoplay keeps
  following the playlist.

## 5. Generator (homelab `chronio-catalog`)

- It stops being a server. It becomes a job that writes the index and one file
  per playlist following the format. Output is served as static files over
  Tailscale (the same `:7443`), from a rootless Podman Quadlet.
- Sources: Chronolists collections and the Star Trek Viewing Guide, with their
  natural sections (series and eras, the guide's own headings), and **Trakt
  lists** (below). A daily systemd timer runs it and replaces files atomically,
  keeping the last good copy per playlist as today.
- The Stremio `catalog`/`meta` routes and `playbackIdentity` are removed once
  both apps use playlists.

### Trakt lists

Generated like any other source, so the app stays source-agnostic:

- `GET /lists/{id}/items` with no type filter, paged, in the list's own
  `sort_by`/`sort_how` applied to the whole mixed list. Never sort per type.
- `movie` → movie entry; `episode` → episode entry; `season`/`show` → expanded
  in place into their episodes (specials excluded for shows). An expanded show
  becomes its own section when the list is otherwise flat.
- Keys: Trakt's list-item id, plus `:S:E` for expansions.
- Configured in the generator (list URLs, Trakt client id). Private lists need
  a token, stored as a Podman secret.

## 6. Phases

1. **Format**: parser for v1 (indexes, sections, hints, compatibility rules)
   with tests. Generator writes v1 static files, served beside today's feed.
2. **Sources UI**: Content & Discovery → Playlists (URLs, per-playlist
   toggles), multi-source repository, Home row in *Reorder home catalogs*, no
   default URL.
3. **Show page**: playlist screen from detail components (hero, sections as
   season tabs, episode cards with real progress, entry options).
4. **Playback comforts**: persisted session, playlist mode in the player
   episodes panel, Continue Watching Next up.
5. **Trakt lists** in the generator.
6. **Desktop** port of 1–4.
7. **Retire the synthetic approach**: remove `playbackIdentity` from both forks
   and the Stremio routes and old feed from the generator. This moves the forks
   back toward upstream.

## Decisions

- No migration of watch progress from the synthetic lists (2026-09-30).
- Playlists are prebuilt static files; no in-app editing (2026-09-30).
- Every source URL returns an index, even for one playlist (2026-09-30).
- Sections are shown as seasons and are chosen by the playlist author
  (2026-09-30).
- Keep upstreaming possible and flag changes that hurt it (2026-09-30).

## Open questions

- Chronolists sections: which grouping to use per list (by series? by era?).
  Proposal: use Chronolists' own grouping where it has one, otherwise one
  section.
- Search: should entries' playlists appear in search results? Proposal: not
  yet.
