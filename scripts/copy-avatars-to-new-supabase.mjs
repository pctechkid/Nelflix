const oldUrl = process.env.OLD_SUPABASE_URL;
const oldAnonKey = process.env.OLD_SUPABASE_ANON_KEY;
const newUrl = process.env.NEW_SUPABASE_URL;
const newServiceKey = process.env.NEW_SUPABASE_SERVICE_ROLE_KEY;

if (!oldUrl || !oldAnonKey || !newUrl || !newServiceKey) {
  console.error('OLD_SUPABASE_URL, OLD_SUPABASE_ANON_KEY, NEW_SUPABASE_URL, and NEW_SUPABASE_SERVICE_ROLE_KEY are required.');
  process.exit(1);
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}: ${await response.text()}`);
  }
  return response.json();
}

const avatars = await requestJson(`${oldUrl}/rest/v1/rpc/get_avatar_catalog`, {
  method: 'POST',
  headers: {
    apikey: oldAnonKey,
    'Content-Type': 'application/json',
  },
  body: '{}',
});

const rows = avatars.map((avatar) => ({
  id: avatar.id,
  display_name: avatar.display_name ?? '',
  storage_path: avatar.storage_path ?? '',
  category: avatar.category ?? 'character',
  sort_order: avatar.sort_order ?? 0,
  is_active: avatar.is_active ?? true,
  bg_color: avatar.bg_color ?? null,
}));

if (rows.length > 0) {
  const response = await fetch(`${newUrl}/rest/v1/avatar_catalog?on_conflict=id`, {
    method: 'POST',
    headers: {
      apikey: newServiceKey,
      Authorization: `Bearer ${newServiceKey}`,
      'Content-Type': 'application/json',
      Prefer: 'resolution=merge-duplicates',
    },
    body: JSON.stringify(rows),
  });
  if (!response.ok) {
    throw new Error(`avatar_catalog seed failed: ${response.status} ${response.statusText}: ${await response.text()}`);
  }
}

let copied = 0;
for (const avatar of rows) {
  if (!avatar.storage_path) continue;

  const source = await fetch(`${oldUrl}/storage/v1/object/public/avatars/${encodeURIComponent(avatar.storage_path)}`);
  if (!source.ok) {
    console.warn(`Skipped ${avatar.storage_path}: ${source.status} ${source.statusText}`);
    continue;
  }

  const bytes = await source.arrayBuffer();
  const contentType = source.headers.get('content-type') ?? 'application/octet-stream';
  const upload = await fetch(`${newUrl}/storage/v1/object/avatars/${encodeURIComponent(avatar.storage_path)}`, {
    method: 'POST',
    headers: {
      apikey: newServiceKey,
      Authorization: `Bearer ${newServiceKey}`,
      'Content-Type': contentType,
      'x-upsert': 'true',
    },
    body: bytes,
  });

  if (!upload.ok) {
    throw new Error(`Upload failed for ${avatar.storage_path}: ${upload.status} ${upload.statusText}: ${await upload.text()}`);
  }
  copied += 1;
}

console.log(`Seeded ${rows.length} avatar rows and copied ${copied} avatar images.`);
