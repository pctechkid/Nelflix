const DEFAULT_TITLE = "Nelflix";
const DEFAULT_DESCRIPTION = "Open this title in Nelflix.";
const DEFAULT_IMAGE = "/app_logo_wordmark.png";

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function firstString(...values) {
  return values.find((value) => typeof value === "string" && value.trim())?.trim() ?? "";
}

function normalizeType(value) {
  return value === "series" ? "series" : "movie";
}

function isImdbId(value) {
  return /^tt\d+$/i.test(value);
}

async function fetchMeta(type, id) {
  if (!isImdbId(id)) return null;
  const response = await fetch(`https://v3-cinemeta.strem.io/meta/${type}/${encodeURIComponent(id)}.json`);
  if (!response.ok) return null;
  const json = await response.json();
  return json?.meta ?? null;
}

function absoluteUrl(request, pathOrUrl) {
  if (/^https?:\/\//i.test(pathOrUrl)) return pathOrUrl;
  const protocol = request.headers["x-forwarded-proto"] || "https";
  const host = request.headers["x-forwarded-host"] || request.headers.host;
  return `${protocol}://${host}${pathOrUrl.startsWith("/") ? "" : "/"}${pathOrUrl}`;
}

export default async function handler(request, response) {
  const requestUrl = new URL(request.url, `https://${request.headers.host ?? "nelflix-ronnel.vercel.app"}`);
  const type = normalizeType(requestUrl.searchParams.get("type"));
  const id = firstString(requestUrl.searchParams.get("id"));
  const appUrl = `nelflix://meta?type=${encodeURIComponent(type)}&id=${encodeURIComponent(id)}`;
  const canonicalUrl = absoluteUrl(request, `/watch?type=${encodeURIComponent(type)}&id=${encodeURIComponent(id)}`);

  let meta = null;
  try {
    meta = await fetchMeta(type, id);
  } catch {
    meta = null;
  }

  const title = firstString(meta?.name, DEFAULT_TITLE);
  const description = firstString(meta?.description, meta?.overview, DEFAULT_DESCRIPTION);
  const image = absoluteUrl(request, firstString(meta?.poster, meta?.background, DEFAULT_IMAGE));
  const ogType = type === "series" ? "video.tv_show" : "video.movie";
  const pageTitle = `${title} | Nelflix`;

  response.setHeader("Content-Type", "text/html; charset=utf-8");
  response.setHeader("Cache-Control", "s-maxage=3600, stale-while-revalidate=86400");
  response.status(200).send(`<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${escapeHtml(pageTitle)}</title>
    <meta name="description" content="${escapeHtml(description)}" />
    <link rel="canonical" href="${escapeHtml(canonicalUrl)}" />
    <meta property="og:type" content="${escapeHtml(ogType)}" />
    <meta property="og:site_name" content="Nelflix" />
    <meta property="og:title" content="${escapeHtml(title)}" />
    <meta property="og:description" content="${escapeHtml(description)}" />
    <meta property="og:image" content="${escapeHtml(image)}" />
    <meta property="og:url" content="${escapeHtml(canonicalUrl)}" />
    <meta name="twitter:card" content="summary_large_image" />
    <meta name="twitter:title" content="${escapeHtml(title)}" />
    <meta name="twitter:description" content="${escapeHtml(description)}" />
    <meta name="twitter:image" content="${escapeHtml(image)}" />
  </head>
  <body style="margin:0;background:#070707;color:#fff;font-family:Inter,Arial,sans-serif;">
    <main style="min-height:100vh;display:grid;place-items:center;padding:24px;">
      <section style="width:min(420px,100%);text-align:center;">
        <img src="${escapeHtml(image)}" alt="" style="width:180px;max-width:55vw;border-radius:10px;box-shadow:0 20px 70px rgba(0,0,0,.55);" />
        <h1 style="font-size:28px;line-height:1.15;margin:22px 0 10px;">${escapeHtml(title)}</h1>
        <p style="color:#cfcfcf;line-height:1.5;margin:0 0 24px;">${escapeHtml(description)}</p>
        <a href="${escapeHtml(appUrl)}" style="display:inline-flex;align-items:center;justify-content:center;min-height:46px;padding:0 22px;border-radius:999px;background:#e50914;color:#fff;text-decoration:none;font-weight:700;">Open in Nelflix</a>
      </section>
    </main>
    <script>
      setTimeout(function () {
        window.location.href = ${JSON.stringify(appUrl)};
      }, 250);
    </script>
  </body>
</html>`);
}
