# Character repository providers

Local Character Desktop uses the same provider-neutral concepts as the Android
catalog: a source is resolved first, then queried, normalized, cached and
installed locally. Repository code never receives conversation history,
memories, prompts, API keys or models.

## Resolution

repository.rs normalizes the URL with reqwest::Url and removes fragments.
Provider-specific host matching runs before generic JSON detection:

- aicharactercards.com and its www/API subdomains use the real AI Character
  Cards API adapter ported from Android.
- chub.ai and character-tavern.com are recognized explicitly and shown as
  UNSUPPORTED until Android supplies a public adapter. Desktop does not invent
  endpoints or scrape arbitrary pages.
- URLs ending in .json/repository.json, or unknown URLs whose response has a
  compatible JSON manifest, use the validated generic provider.
- Unknown HTML is reported as an incompatible source instead of being parsed
  as JSON.

## Synchronization and cache

Sources are stored in SQLite in character_repositories. Normalized catalog
entries are stored in remote_characters, keyed by provider_id and remote_id.
A sync replaces only the snapshot belonging to that source, then Explore
rebuilds its metadata once from the complete snapshot. A source failure keeps
the previous successful snapshot and reports ERROR; it does not remove
installed characters.

## Dynamic filters

Explore derives source, language, tag and category options from the active
remote snapshot through the backend `ExploreFilterCatalog` cache. Languages
are normalized to ISO-like base codes (es, en, pt, etc.); tags and categories
are trimmed, collapsed and case-normalized. Selecting a source
narrows the available facets. If a selected option becomes invalid after a
sync or source removal, the UI resets it to Todos. NSFW visibility uses
provider metadata only and never guesses from an image or a character name.

## Installation

Installing a remote result downloads and validates the Character Card, stores
the original card and avatar under the local data directory, parses V1/V2
metadata and writes the character to SQLite. The resulting character has no
runtime dependency on its source and can be used offline. Existing installed
characters survive source deletion.

## Security limits

Only http/https URLs are accepted. Redirects are limited, responses are
size-capped, and scripts, HTML execution, binaries and unsafe URL schemes are
never executed. Provider-specific assets are restricted to their allowed
origins where the adapter knows them.
