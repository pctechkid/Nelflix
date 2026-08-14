import { readFile } from 'node:fs/promises';
import { Client } from 'pg';

const databaseUrl = process.env.DATABASE_URL;
const sqlPath = process.argv[2] ?? 'supabase/nelflix_schema.sql';

if (!databaseUrl) {
  console.error('DATABASE_URL is required.');
  process.exit(1);
}

const sql = await readFile(sqlPath, 'utf8');
const client = new Client({
  connectionString: databaseUrl,
  ssl: { rejectUnauthorized: false },
});

try {
  await client.connect();
  await client.query(sql);
  console.log(`Applied Supabase schema from ${sqlPath}`);
} finally {
  await client.end();
}
