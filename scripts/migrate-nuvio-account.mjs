const requiredEnv = [
  "OLD_SUPABASE_URL",
  "OLD_SUPABASE_ANON_KEY",
  "NEW_SUPABASE_URL",
  "NEW_SUPABASE_ANON_KEY",
  "NEW_SUPABASE_SERVICE_ROLE_KEY",
  "MIGRATION_EMAIL",
  "MIGRATION_PASSWORD",
];

for (const name of requiredEnv) {
  if (!process.env[name]) {
    console.error(`${name} is required.`);
    process.exit(1);
  }
}

const oldUrl = process.env.OLD_SUPABASE_URL;
const oldAnonKey = process.env.OLD_SUPABASE_ANON_KEY;
const newUrl = process.env.NEW_SUPABASE_URL;
const newAnonKey = process.env.NEW_SUPABASE_ANON_KEY;
const newServiceKey = process.env.NEW_SUPABASE_SERVICE_ROLE_KEY;
const email = process.env.MIGRATION_EMAIL;
const password = process.env.MIGRATION_PASSWORD;

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}: ${text}`);
  }
  return text ? JSON.parse(text) : null;
}

function anonHeaders(key, jwt = null) {
  return {
    apikey: key,
    ...(jwt ? { Authorization: `Bearer ${jwt}` } : {}),
    "Content-Type": "application/json",
  };
}

function adminHeaders() {
  return {
    apikey: newServiceKey,
    Authorization: `Bearer ${newServiceKey}`,
    "Content-Type": "application/json",
  };
}

async function signIn(url, anonKey) {
  return fetchJson(`${url}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: anonHeaders(anonKey),
    body: JSON.stringify({ email, password }),
  });
}

async function ensureNewUser() {
  const list = await fetchJson(`${newUrl}/auth/v1/admin/users?per_page=1000`, {
    headers: adminHeaders(),
  });
  const existing = list.users?.find((user) => user.email?.toLowerCase() === email.toLowerCase());
  if (existing) {
    await fetchJson(`${newUrl}/auth/v1/admin/users/${existing.id}`, {
      method: "PUT",
      headers: adminHeaders(),
      body: JSON.stringify({ password, email_confirm: true }),
    });
    return existing.id;
  }

  const created = await fetchJson(`${newUrl}/auth/v1/admin/users`, {
    method: "POST",
    headers: adminHeaders(),
    body: JSON.stringify({ email, password, email_confirm: true }),
  });
  return created.id;
}

async function rpc(baseUrl, key, jwt, name, body = {}) {
  return fetchJson(`${baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: anonHeaders(key, jwt),
    body: JSON.stringify(body),
  });
}

async function tableRows(baseUrl, key, jwt, table, query = "select=*") {
  return fetchJson(`${baseUrl}/rest/v1/${table}?${query}`, {
    headers: anonHeaders(key, jwt),
  });
}

async function allTableRows(baseUrl, key, jwt, table, query = "select=*", pageSize = 1000) {
  const rows = [];
  for (let from = 0; ; from += pageSize) {
    const to = from + pageSize - 1;
    const page = await fetchJson(`${baseUrl}/rest/v1/${table}?${query}`, {
      headers: {
        ...anonHeaders(key, jwt),
        Range: `${from}-${to}`,
      },
    });
    rows.push(...page);
    if (page.length < pageSize) break;
  }
  return rows;
}

async function servicePatch(table, query, rows) {
  if (!rows.length) return;
  await fetchJson(`${newUrl}/rest/v1/${table}?${query}`, {
    method: "PATCH",
    headers: {
      ...adminHeaders(),
      Prefer: "return=minimal",
    },
    body: JSON.stringify(rows[0]),
  });
}

function profilePayload(profiles) {
  return profiles.map((profile) => ({
    profile_index: profile.profile_index,
    name: profile.name ?? "",
    avatar_color_hex: profile.avatar_color_hex ?? "#1E88E5",
    uses_primary_addons: profile.uses_primary_addons ?? false,
    uses_primary_plugins: profile.uses_primary_plugins ?? false,
    avatar_id: profile.avatar_id ?? null,
    avatar_url: profile.avatar_url ?? null,
  }));
}

function addonPayload(rows) {
  return rows
    .sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
    .map((row, index) => ({
      url: row.url,
      name: row.name ?? "",
      enabled: row.enabled ?? true,
      sort_order: row.sort_order ?? index,
    }));
}

function watchedPayload(rows) {
  return rows.map((row) => ({
    content_id: row.content_id,
    content_type: row.content_type ?? "",
    title: row.title ?? "",
    season: row.season ?? null,
    episode: row.episode ?? null,
    watched_at: row.watched_at ?? 0,
  }));
}

async function pullWatchedByProfile(baseUrl, key, jwt, profileId) {
  const rows = [];
  for (let page = 1; ; page += 1) {
    const pageRows = await rpc(baseUrl, key, jwt, "sync_pull_watched_items", {
      p_profile_id: profileId,
      p_page: page,
      p_page_size: 500,
    });
    rows.push(...pageRows.map((row) => ({ ...row, profile_id: profileId })));
    if (pageRows.length < 500) break;
  }
  return rows;
}

async function pullBlobsByProfile(baseUrl, key, jwt, name, profileIndexes, bodyForProfile) {
  const rows = [];
  for (const profileIndex of profileIndexes) {
    const profileRows = await rpc(baseUrl, key, jwt, name, bodyForProfile(profileIndex));
    rows.push(...profileRows);
  }
  return rows;
}

function progressPayload(rows) {
  return rows.map((row) => ({
    content_id: row.content_id,
    content_type: row.content_type ?? "",
    video_id: row.video_id ?? "",
    season: row.season ?? null,
    episode: row.episode ?? null,
    position: row.position ?? 0,
    duration: row.duration ?? 0,
    last_watched: row.last_watched ?? 0,
  }));
}

function libraryPayload(rows) {
  return rows.map((row) => ({
    content_id: row.content_id,
    content_type: row.content_type ?? "",
    name: row.name ?? "",
    poster: row.poster ?? null,
    poster_shape: row.poster_shape ?? "POSTER",
    background: row.background ?? null,
    description: row.description ?? null,
    release_info: row.release_info ?? null,
    imdb_rating: row.imdb_rating ?? null,
    genres: row.genres ?? [],
    added_at: row.added_at ?? 0,
  }));
}

const oldSession = await signIn(oldUrl, oldAnonKey);
const oldJwt = oldSession.access_token;
const oldUserId = oldSession.user.id;
const newUserId = await ensureNewUser();
const newSession = await signIn(newUrl, newAnonKey);
const newJwt = newSession.access_token;

const profiles = await rpc(oldUrl, oldAnonKey, oldJwt, "sync_pull_profiles");
const profileIndexes = [...new Set(profiles.map((profile) => profile.profile_index))].sort((a, b) => a - b);
await rpc(newUrl, newAnonKey, newJwt, "sync_push_profiles", {
  p_profiles: profilePayload(profiles),
});

const oldProfileRows = await allTableRows(oldUrl, oldAnonKey, oldJwt, "profiles", "select=*");
for (const oldProfile of oldProfileRows) {
  await servicePatch(
    "profiles",
    `user_id=eq.${newUserId}&profile_index=eq.${oldProfile.profile_index}`,
    [
      {
        pin_enabled: oldProfile.pin_enabled ?? false,
        pin_hash: oldProfile.pin_hash ?? null,
        pin_updated_at: oldProfile.pin_updated_at ?? null,
        failed_pin_attempts: oldProfile.failed_pin_attempts ?? 0,
        pin_locked_until: oldProfile.pin_locked_until ?? null,
      },
    ],
  );
}

const addons = await allTableRows(oldUrl, oldAnonKey, oldJwt, "addons", "select=*&order=profile_id.asc,sort_order.asc");
const plugins = await allTableRows(oldUrl, oldAnonKey, oldJwt, "plugins", "select=*&order=profile_id.asc,sort_order.asc");
const watchedItems = (
  await Promise.all(profileIndexes.map((profileIndex) => pullWatchedByProfile(oldUrl, oldAnonKey, oldJwt, profileIndex)))
).flat();
const watchProgress = await allTableRows(oldUrl, oldAnonKey, oldJwt, "watch_progress", "select=*&order=last_watched.desc");
const libraryItems = await allTableRows(oldUrl, oldAnonKey, oldJwt, "library_items", "select=*&order=added_at.desc");
const collections = await pullBlobsByProfile(
  oldUrl,
  oldAnonKey,
  oldJwt,
  "sync_pull_collections",
  profileIndexes,
  (profileIndex) => ({ p_profile_id: profileIndex }),
);
const profileSettings = await pullBlobsByProfile(
  oldUrl,
  oldAnonKey,
  oldJwt,
  "sync_pull_profile_settings_blob",
  profileIndexes,
  (profileIndex) => ({ p_profile_id: profileIndex, p_platform: "mobile" }),
);
const homeCatalogSettings = await pullBlobsByProfile(
  oldUrl,
  oldAnonKey,
  oldJwt,
  "sync_pull_home_catalog_settings",
  profileIndexes,
  (profileIndex) => ({ p_profile_id: profileIndex, p_platform: "mobile" }),
);

for (const profileIndex of profileIndexes) {
  await rpc(newUrl, newAnonKey, newJwt, "sync_push_addons", {
    p_profile_id: profileIndex,
    p_addons: addonPayload(addons.filter((row) => row.profile_id === profileIndex)),
  });
  await rpc(newUrl, newAnonKey, newJwt, "sync_push_plugins", {
    p_profile_id: profileIndex,
    p_plugins: addonPayload(plugins.filter((row) => row.profile_id === profileIndex)),
  });
  await rpc(newUrl, newAnonKey, newJwt, "sync_push_watched_items", {
    p_profile_id: profileIndex,
    p_items: watchedPayload(watchedItems.filter((row) => row.profile_id === profileIndex)),
  });
  await rpc(newUrl, newAnonKey, newJwt, "sync_push_watch_progress", {
    p_profile_id: profileIndex,
    p_entries: progressPayload(watchProgress.filter((row) => row.profile_id === profileIndex)),
  });
  await rpc(newUrl, newAnonKey, newJwt, "sync_push_library", {
    p_profile_id: profileIndex,
    p_items: libraryPayload(libraryItems.filter((row) => row.profile_id === profileIndex)),
  });
}

for (const blob of collections) {
  await rpc(newUrl, newAnonKey, newJwt, "sync_push_collections", {
    p_profile_id: blob.profile_id,
    p_collections_json: blob.collections_json ?? [],
  });
}

for (const blob of profileSettings) {
  await rpc(newUrl, newAnonKey, newJwt, "sync_push_profile_settings_blob", {
    p_profile_id: blob.profile_id,
    p_platform: "mobile",
    p_settings_json: blob.settings_json ?? {},
  });
}

for (const blob of homeCatalogSettings) {
  await rpc(newUrl, newAnonKey, newJwt, "sync_push_home_catalog_settings", {
    p_profile_id: blob.profile_id,
    p_platform: blob.platform ?? "mobile",
    p_settings_json: blob.settings_json ?? {},
  });
}

const verify = {
  profiles: (await rpc(newUrl, newAnonKey, newJwt, "sync_pull_profiles")).length,
  addons: (await allTableRows(newUrl, newAnonKey, newJwt, "addons", "select=id")).length,
  plugins: (await allTableRows(newUrl, newAnonKey, newJwt, "plugins", "select=id")).length,
  watched_items: (await allTableRows(newUrl, newAnonKey, newJwt, "watched_items", "select=id")).length,
  watch_progress: (await allTableRows(newUrl, newAnonKey, newJwt, "watch_progress", "select=id")).length,
  library_items: (await allTableRows(newUrl, newAnonKey, newJwt, "library_items", "select=id")).length,
  collections: (await allTableRows(newUrl, newAnonKey, newJwt, "collections", "select=id")).length,
  profile_settings: (await allTableRows(newUrl, newAnonKey, newJwt, "profile_settings_blobs", "select=id")).length,
  home_catalog_settings: (await allTableRows(newUrl, newAnonKey, newJwt, "home_catalog_settings", "select=id")).length,
};

console.log(JSON.stringify({
  ok: true,
  email,
  old_user_id: oldUserId,
  new_user_id: newUserId,
  pulled: {
    profiles: profiles.length,
    addons: addons.length,
    plugins: plugins.length,
    watched_items: watchedItems.length,
    watch_progress: watchProgress.length,
    library_items: libraryItems.length,
    collections: collections.length,
    profile_settings: profileSettings.length,
    home_catalog_settings: homeCatalogSettings.length,
  },
  verified_new: verify,
}, null, 2));
