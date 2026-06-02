import React, { useCallback, useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  ArrowDown,
  ArrowUp,
  Check,
  Clapperboard,
  Copy,
  Eye,
  EyeOff,
  History,
  LibraryBig,
  Loader2,
  LogOut,
  Menu,
  PackagePlus,
  Plug,
  Plus,
  RefreshCw,
  Save,
  ShieldCheck,
  Trash2,
  UploadCloud,
  UserRound,
  UsersRound,
  X,
} from "lucide-react";
import { Session } from "@supabase/supabase-js";
import { supabase } from "./supabase";
import type { LibraryItemRow, Notice, Profile, RemoteEntry, TabKey, WatchedItemRow, WatchProgressRow } from "./types";
import "./styles.css";

function normalizeManifestUrl(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return "";
  if (trimmed.endsWith("/manifest.json")) return trimmed;
  if (trimmed.endsWith("/")) return `${trimmed}manifest.json`;
  return `${trimmed}/manifest.json`;
}

function displayHost(url: string) {
  try {
    return new URL(url).host.replace(/^www\./, "");
  } catch {
    return url;
  }
}

async function discoverName(url: string) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Manifest returned ${response.status}`);
  const json = await response.json();
  return typeof json.name === "string" && json.name.trim() ? json.name.trim() : displayHost(url);
}

function isEntryTab(tab: TabKey): tab is "addons" | "plugins" {
  return tab === "addons" || tab === "plugins";
}

function titleForTab(tab: TabKey) {
  switch (tab) {
    case "addons":
      return "Addon Manager";
    case "plugins":
      return "Plugin Manager";
    case "watch-progress":
      return "Watch Progress";
    case "library":
      return "Library";
    case "watched":
      return "Watched";
    case "profiles":
      return "Profiles";
    case "super-admin":
      return "Super Admin";
  }
}

function progressKeyFor(contentId: string, season: number | null, episode: number | null) {
  return season != null && episode != null ? `${contentId}_s${season}e${episode}` : contentId;
}

function toNumber(value: string | null, fallback = 0) {
  if (value == null || value.trim() === "") return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function nullableNumber(value: string | null) {
  if (value == null || value.trim() === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function formatRuntime(ms: number) {
  if (!ms) return "0:00";
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return hours > 0
    ? `${hours}:${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`
    : `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

function formatDate(epochMs: number) {
  if (!epochMs) return "Unknown";
  return new Date(epochMs).toLocaleString();
}

type RemoteContentMeta = {
  name: string | null;
  poster: string | null;
  videos: Array<{
    season?: number | null;
    episode?: number | null;
    name?: string | null;
    title?: string | null;
  }>;
};

function isImdbId(value: string) {
  return /^tt\d+$/i.test(value);
}

async function fetchRemoteContentMeta(contentId: string, contentType: string): Promise<RemoteContentMeta | null> {
  if (!isImdbId(contentId)) return null;
  const type = contentType === "series" ? "series" : "movie";
  const response = await fetch(`https://v3-cinemeta.strem.io/meta/${type}/${contentId}.json`);
  if (!response.ok) throw new Error(`Metadata returned ${response.status}`);
  const json = await response.json();
  const meta = json?.meta;
  if (!meta) return null;
  return {
    name: typeof meta.name === "string" ? meta.name : null,
    poster: typeof meta.poster === "string" ? meta.poster : null,
    videos: Array.isArray(meta.videos) ? meta.videos : [],
  };
}

function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [authLoading, setAuthLoading] = useState(true);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session);
      setAuthLoading(false);
    });
    const { data } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession);
      setAuthLoading(false);
    });
    return () => data.subscription.unsubscribe();
  }, []);

  if (authLoading) return <ShellLoading />;
  if (!session) return <AuthScreen />;
  return <Console session={session} />;
}

function ShellLoading() {
  return (
    <main className="loading">
      <img src="/app_logo_wordmark.png" alt="Nelflix" />
      <Loader2 className="spin" size={24} />
    </main>
  );
}

function AuthScreen() {
  const [mode, setMode] = useState<"signin" | "signup">("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    const action =
      mode === "signin"
        ? supabase.auth.signInWithPassword({ email, password })
        : supabase.auth.signUp({
            email,
            password,
            options: {
              emailRedirectTo: window.location.origin,
            },
          });
    const { error } = await action;
    setBusy(false);
    if (error) {
      setNotice({ kind: "error", text: error.message });
      return;
    }
    if (mode === "signup") {
      setNotice({ kind: "success", text: "Account created. You can sign in when email confirmation is complete." });
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <div className="auth-brand">
          <img src="/app_logo_wordmark.png" alt="Nelflix" />
          <p>Account console</p>
        </div>
        <form onSubmit={submit} className="auth-form">
          <div className="segmented" role="tablist" aria-label="Authentication mode">
            <button type="button" className={mode === "signin" ? "active" : ""} onClick={() => setMode("signin")}>
              Sign in
            </button>
            <button type="button" className={mode === "signup" ? "active" : ""} onClick={() => setMode("signup")}>
              Sign up
            </button>
          </div>
          <label>
            Email
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="email" required />
          </label>
          <label>
            Password
            <span className="password-row">
              <input
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type={showPassword ? "text" : "password"}
                autoComplete={mode === "signin" ? "current-password" : "new-password"}
                required
              />
              <button type="button" className="icon-button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "Hide password" : "Show password"}>
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </span>
          </label>
          {notice && <p className={`notice ${notice.kind}`}>{notice.text}</p>}
          <button className="primary-button" disabled={busy}>
            {busy ? <Loader2 className="spin" size={18} /> : <Check size={18} />}
            {mode === "signin" ? "Sign in" : "Create account"}
          </button>
        </form>
      </section>
    </main>
  );
}

function Console({ session }: { session: Session }) {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [activeProfile, setActiveProfile] = useState(1);
  const [addons, setAddons] = useState<RemoteEntry[]>([]);
  const [plugins, setPlugins] = useState<RemoteEntry[]>([]);
  const [watchProgress, setWatchProgress] = useState<WatchProgressRow[]>([]);
  const [libraryItems, setLibraryItems] = useState<LibraryItemRow[]>([]);
  const [watchedItems, setWatchedItems] = useState<WatchedItemRow[]>([]);
  const [watchedPage, setWatchedPage] = useState(1);
  const [watchedTotal, setWatchedTotal] = useState(0);
  const [tab, setTab] = useState<TabKey>("addons");
  const [notice, setNotice] = useState<Notice>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [isSuperAdmin, setIsSuperAdmin] = useState(false);
  const [adminBusy, setAdminBusy] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const currentProfile = profiles.find((profile) => profile.profile_index === activeProfile);
  const effectiveAddonProfile = currentProfile?.uses_primary_addons ? 1 : activeProfile;
  const effectivePluginProfile = currentProfile?.uses_primary_plugins ? 1 : activeProfile;

  const loadProfiles = useCallback(async () => {
    const { data, error } = await supabase.rpc("sync_pull_profiles");
    if (error) throw error;
    const nextProfiles = (data ?? []) as Profile[];
    setProfiles(nextProfiles);
    setActiveProfile((previous) => nextProfiles.some((profile) => profile.profile_index === previous) ? previous : nextProfiles[0]?.profile_index ?? 1);
  }, []);

  const loadEntries = useCallback(async (table: "addons" | "plugins", profileId: number) => {
    const { data, error } = await supabase
      .from(table)
      .select("url,name,enabled,sort_order")
      .eq("profile_id", profileId)
      .order("sort_order", { ascending: true });
    if (error) throw error;
    return (data ?? []) as RemoteEntry[];
  }, []);

  const loadProfileData = useCallback(async (profileId: number) => {
    const [progress, library] = await Promise.all([
      supabase.from("watch_progress").select("*").eq("profile_id", profileId).order("last_watched", { ascending: false }),
      supabase.from("library_items").select("*").eq("profile_id", profileId).order("added_at", { ascending: false }),
    ]);
    if (progress.error) throw progress.error;
    if (library.error) throw library.error;
    setWatchProgress((progress.data ?? []) as WatchProgressRow[]);
    setLibraryItems((library.data ?? []) as LibraryItemRow[]);
  }, []);

  const loadWatchedPage = useCallback(async (profileId: number, page: number) => {
    const pageSize = 50;
    const from = (page - 1) * pageSize;
    const to = from + pageSize - 1;
    const { data, error, count } = await supabase
      .from("watched_items")
      .select("*", { count: "exact" })
      .eq("profile_id", profileId)
      .order("watched_at", { ascending: false })
      .range(from, to);
    if (error) throw error;
    setWatchedItems((data ?? []) as WatchedItemRow[]);
    setWatchedTotal(count ?? 0);
  }, []);

  const refreshAll = useCallback(async () => {
    setLoading(true);
    setNotice(null);
    try {
      await loadProfiles();
    } catch (error) {
      setNotice({ kind: "error", text: error instanceof Error ? error.message : "Unable to load profiles." });
    } finally {
      setLoading(false);
    }
  }, [loadProfiles]);

  useEffect(() => {
    refreshAll();
  }, [refreshAll]);

  useEffect(() => {
    let cancelled = false;
    async function checkAdmin() {
      const { data, error } = await supabase.rpc("dashboard_is_super_admin");
      if (!cancelled && !error) setIsSuperAdmin(Boolean(data));
    }
    checkAdmin();
    return () => {
      cancelled = true;
    };
  }, [session.user.id]);

  useEffect(() => {
    let cancelled = false;
    async function run() {
      if (profiles.length === 0) return;
      setLoading(true);
      try {
        const [nextAddons, nextPlugins] = await Promise.all([
          loadEntries("addons", effectiveAddonProfile),
          loadEntries("plugins", effectivePluginProfile),
        ]);
        if (!cancelled) {
          setAddons(nextAddons);
          setPlugins(nextPlugins);
          await loadProfileData(activeProfile);
          await loadWatchedPage(activeProfile, watchedPage);
        }
      } catch (error) {
        if (!cancelled) setNotice({ kind: "error", text: error instanceof Error ? error.message : "Unable to load data." });
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    run();
    return () => {
      cancelled = true;
    };
  }, [profiles, activeProfile, watchedPage, effectiveAddonProfile, effectivePluginProfile, loadEntries, loadProfileData, loadWatchedPage]);

  useEffect(() => {
    setWatchedPage(1);
  }, [activeProfile]);

  async function saveEntries(kind: "addons" | "plugins", rows: RemoteEntry[]) {
    setSaving(true);
    setNotice(null);
    const normalized = rows
      .map((row, index) => ({ ...row, url: normalizeManifestUrl(row.url), sort_order: index }))
      .filter((row) => row.url);
    const rpcName = kind === "addons" ? "sync_push_addons" : "sync_push_plugins";
    const profileId = kind === "addons" ? effectiveAddonProfile : effectivePluginProfile;
    const payloadKey = kind === "addons" ? "p_addons" : "p_plugins";
    const { error } = await supabase.rpc(rpcName, {
      p_profile_id: profileId,
      [payloadKey]: normalized,
    });
    setSaving(false);
    if (error) {
      setNotice({ kind: "error", text: error.message });
      return;
    }
    if (kind === "addons") setAddons(normalized);
    else setPlugins(normalized);
    setNotice({ kind: "success", text: `${kind === "addons" ? "Addons" : "Plugins"} saved.` });
  }

  async function pushAddonsToAllUsers() {
    const normalized = addons
      .map((row, index) => ({ ...row, url: normalizeManifestUrl(row.url), sort_order: index }))
      .filter((row) => row.url);
    const warning = normalized.length === 0
      ? `This will remove addons from profile ${effectiveAddonProfile} for every other user because your source list is empty. Continue?`
      : `Replace profile ${effectiveAddonProfile} addons for every other user with your ${normalized.length} current addon(s)?`;
    if (!window.confirm(warning)) return;

    setAdminBusy(true);
    setNotice(null);

    const saveResult = await supabase.rpc("sync_push_addons", {
      p_profile_id: effectiveAddonProfile,
      p_addons: normalized,
    });
    if (saveResult.error) {
      setAdminBusy(false);
      setNotice({ kind: "error", text: saveResult.error.message });
      return;
    }
    setAddons(normalized);

    const { data, error } = await supabase.rpc("dashboard_push_addons_to_all_users", {
      p_profile_id: effectiveAddonProfile,
    });
    setAdminBusy(false);
    if (error) {
      setNotice({ kind: "error", text: error.message });
      return;
    }

    const result = Array.isArray(data) ? data[0] : null;
    const targetUsers = Number(result?.target_users ?? 0);
    const sourceAddons = Number(result?.source_addons ?? normalized.length);
    setNotice({
      kind: "success",
      text: `Mirrored ${sourceAddons} addon(s) to ${targetUsers} user account(s).`,
    });
  }

  async function signOut() {
    await supabase.auth.signOut();
  }

  function switchTab(nextTab: TabKey) {
    setTab(nextTab);
    setMobileMenuOpen(false);
  }

  const visibleRows = tab === "addons" ? addons : plugins;
  const stats = useMemo(() => ({
    profiles: profiles.length,
    addons: addons.length,
    plugins: plugins.length,
    progress: watchProgress.length,
    library: libraryItems.length,
    watched: watchedTotal,
    enabledAddons: addons.filter((row) => row.enabled).length,
    enabledPlugins: plugins.filter((row) => row.enabled).length,
  }), [profiles, addons, plugins, watchProgress, libraryItems, watchedTotal]);

  return (
    <main className="app-shell">
      <aside className={mobileMenuOpen ? "sidebar menu-open" : "sidebar"}>
        <div className="sidebar-head">
          <div className="brand">
            <img src="/app_logo_wordmark.png" alt="Nelflix" />
            <span>Control Center</span>
          </div>
          <button
            type="button"
            className="menu-toggle"
            onClick={() => setMobileMenuOpen((open) => !open)}
            aria-label={mobileMenuOpen ? "Close navigation menu" : "Open navigation menu"}
            aria-expanded={mobileMenuOpen}
          >
            {mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
        <div className="sidebar-menu">
          <ProfilePicker profiles={profiles} activeProfile={activeProfile} onChange={(value) => {
            setActiveProfile(value);
            setMobileMenuOpen(false);
          }} />
          <nav className="nav-tabs" aria-label="Console sections">
            <button className={tab === "addons" ? "active" : ""} onClick={() => switchTab("addons")}><PackagePlus size={17} /> Addons</button>
            <button className={tab === "plugins" ? "active" : ""} onClick={() => switchTab("plugins")}><Plug size={17} /> Plugins</button>
            <button className={tab === "watch-progress" ? "active" : ""} onClick={() => switchTab("watch-progress")}><Clapperboard size={17} /> Watch Progress</button>
            <button className={tab === "library" ? "active" : ""} onClick={() => switchTab("library")}><LibraryBig size={17} /> Library</button>
            <button className={tab === "watched" ? "active" : ""} onClick={() => switchTab("watched")}><History size={17} /> Watched</button>
            <button className={tab === "profiles" ? "active" : ""} onClick={() => switchTab("profiles")}><UsersRound size={17} /> Profiles</button>
            {isSuperAdmin && <button className={tab === "super-admin" ? "active" : ""} onClick={() => switchTab("super-admin")}><ShieldCheck size={17} /> Super Admin</button>}
          </nav>
          <div className="account-box">
            <UserRound size={17} />
            <span>{session.user.email}</span>
            <button className="icon-button" onClick={signOut} aria-label="Sign out" title="Sign out">
              <LogOut size={17} />
            </button>
          </div>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">{currentProfile?.name ?? "Profile"}</p>
            <h1>{titleForTab(tab)}</h1>
            <p className="topbar-subtitle">Manage your Nelflix account, media state, and synced sources from one compact console.</p>
          </div>
          <div className="top-actions">
            <button className="secondary-button" onClick={refreshAll} disabled={loading}>
              <RefreshCw className={loading ? "spin" : ""} size={18} />
              Refresh
            </button>
            {isEntryTab(tab) && (
              <button
                className="primary-button"
                onClick={() => saveEntries(tab, visibleRows)}
                disabled={saving}
              >
                {saving ? <Loader2 className="spin" size={18} /> : <Save size={18} />}
                Save
              </button>
            )}
          </div>
        </header>

        <section className="stats-grid" aria-label="Account summary">
          <Stat label="Profiles" value={stats.profiles} />
          <Stat label="Addons" value={`${stats.enabledAddons}/${stats.addons}`} />
          <Stat label="Plugins" value={`${stats.enabledPlugins}/${stats.plugins}`} />
          <Stat label="Progress" value={stats.progress} />
          <Stat label="Library" value={stats.library} />
          <Stat label="Watched" value={stats.watched} />
        </section>

        {notice && <p className={`notice ${notice.kind}`}>{notice.text}</p>}

        {tab === "profiles" ? (
          <ProfilesView profiles={profiles} activeProfile={activeProfile} />
        ) : tab === "super-admin" && isSuperAdmin ? (
          <SuperAdminView
            profileId={effectiveAddonProfile}
            addons={addons}
            busy={adminBusy}
            onPushAddons={pushAddonsToAllUsers}
          />
        ) : isEntryTab(tab) ? (
          <EntryManager
            kind={tab}
            profileId={tab === "addons" ? effectiveAddonProfile : effectivePluginProfile}
            sourceProfile={activeProfile}
            rows={visibleRows}
            onRowsChange={tab === "addons" ? setAddons : setPlugins}
            onSave={(rows) => saveEntries(tab, rows)}
            saving={saving}
          />
        ) : tab === "watch-progress" ? (
          <WatchProgressView
            rows={watchProgress}
            libraryItems={libraryItems}
            watchedItems={watchedItems}
            profileId={activeProfile}
            onRowsChange={setWatchProgress}
            onNotice={setNotice}
          />
        ) : tab === "library" ? (
          <LibraryView rows={libraryItems} profileId={activeProfile} onRowsChange={setLibraryItems} onNotice={setNotice} />
        ) : (
          <WatchedView
            rows={watchedItems}
            profileId={activeProfile}
            total={watchedTotal}
            page={watchedPage}
            onPageChange={setWatchedPage}
            onRowsChange={setWatchedItems}
            onTotalChange={setWatchedTotal}
            onNotice={setNotice}
          />
        )}
      </section>
    </main>
  );
}

function SuperAdminView({
  profileId,
  addons,
  busy,
  onPushAddons,
}: {
  profileId: number;
  addons: RemoteEntry[];
  busy: boolean;
  onPushAddons: () => void;
}) {
  const enabledCount = addons.filter((row) => row.enabled).length;

  return (
    <section className="admin-panel">
      <div className="admin-hero">
        <div className="admin-icon">
          <ShieldCheck size={28} />
        </div>
        <div>
          <h2>Push addons to all users</h2>
          <p>
            Mirror this super-admin profile&apos;s addon list to profile {profileId} for every other existing account.
          </p>
        </div>
      </div>

      <div className="admin-summary">
        <Stat label="Source profile" value={profileId} />
        <Stat label="Source addons" value={addons.length} />
        <Stat label="Enabled" value={enabledCount} />
      </div>

      <div className="admin-preview">
        <h3>Addon list that will be pushed</h3>
        {addons.length === 0 ? (
          <div className="empty-state">
            This source profile has no addons. Pushing now will clear addons from other users for this profile.
          </div>
        ) : (
          <div className="entry-list compact-list">
            {addons.map((addon, index) => (
              <article className="entry-row" key={`${addon.url}-${index}`}>
                <span className={addon.enabled ? "status-dot enabled" : "status-dot"} />
                <div className="entry-main">
                  <strong>{addon.name || displayHost(addon.url)}</strong>
                  <p>{addon.url}</p>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>

      <button className="primary-button admin-push-button" onClick={onPushAddons} disabled={busy}>
        {busy ? <Loader2 className="spin" size={18} /> : <UploadCloud size={18} />}
        Push addons to all users
      </button>
    </section>
  );
}

function ProfilePicker({ profiles, activeProfile, onChange }: { profiles: Profile[]; activeProfile: number; onChange: (value: number) => void }) {
  return (
    <div className="profile-stack">
      {profiles.map((profile) => (
        <button
          key={profile.profile_index}
          className={profile.profile_index === activeProfile ? "profile-chip active" : "profile-chip"}
          onClick={() => onChange(profile.profile_index)}
        >
          <span style={{ background: profile.avatar_color_hex }} />
          {profile.name || `Profile ${profile.profile_index}`}
        </button>
      ))}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="stat" data-label={label}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function EntryManager({
  kind,
  profileId,
  sourceProfile,
  rows,
  onRowsChange,
  onSave,
  saving,
}: {
  kind: "addons" | "plugins";
  profileId: number;
  sourceProfile: number;
  rows: RemoteEntry[];
  onRowsChange: (rows: RemoteEntry[]) => void;
  onSave: (rows: RemoteEntry[]) => void;
  saving: boolean;
}) {
  const [draft, setDraft] = useState("");
  const [busyUrl, setBusyUrl] = useState<string | null>(null);
  const shared = profileId !== sourceProfile;

  async function addEntry() {
    const url = normalizeManifestUrl(draft);
    if (!url || rows.some((row) => normalizeManifestUrl(row.url) === url)) return;
    setBusyUrl(url);
    let name = displayHost(url);
    try {
      name = await discoverName(url);
    } catch {
      // Keep a usable fallback name; the mobile app can still refresh the manifest later.
    }
    setBusyUrl(null);
    onRowsChange([...rows, { url, name, enabled: true, sort_order: rows.length }]);
    setDraft("");
  }

  async function refreshName(index: number) {
    const row = rows[index];
    setBusyUrl(row.url);
    try {
      const name = await discoverName(row.url);
      update(index, { name });
    } finally {
      setBusyUrl(null);
    }
  }

  function update(index: number, patch: Partial<RemoteEntry>) {
    onRowsChange(rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)));
  }

  function move(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= rows.length) return;
    const next = [...rows];
    const [item] = next.splice(index, 1);
    next.splice(target, 0, item);
    onRowsChange(next.map((row, rowIndex) => ({ ...row, sort_order: rowIndex })));
  }

  function remove(index: number) {
    onRowsChange(rows.filter((_row, rowIndex) => rowIndex !== index).map((row, rowIndex) => ({ ...row, sort_order: rowIndex })));
  }

  return (
    <section className="manager">
      <div className="manager-toolbar">
        <div>
          <h2>{kind === "addons" ? "Installed addons" : "Plugin repositories"}</h2>
          <p>{shared ? `Using profile ${profileId}'s shared list.` : `Managing profile ${profileId}.`}</p>
        </div>
        <div className="add-row">
          <input value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="https://example.com/manifest.json" />
          <button className="primary-button" onClick={addEntry} disabled={!draft.trim() || Boolean(busyUrl)}>
            {busyUrl === normalizeManifestUrl(draft) ? <Loader2 className="spin" size={18} /> : <Plus size={18} />}
            Add
          </button>
        </div>
      </div>

      <div className="entry-list">
        {rows.length === 0 ? (
          <div className="empty-state">No {kind} installed for this profile.</div>
        ) : rows.map((row, index) => (
          <article className="entry-row" key={`${row.url}-${index}`}>
            <label className="toggle" title={row.enabled ? "Enabled" : "Disabled"}>
              <input type="checkbox" checked={row.enabled} onChange={(event) => update(index, { enabled: event.target.checked })} />
              <span />
            </label>
            <div className="entry-main">
              <input className="entry-name" value={row.name ?? ""} onChange={(event) => update(index, { name: event.target.value })} placeholder={displayHost(row.url)} />
              <input className="entry-url" value={row.url} onChange={(event) => update(index, { url: event.target.value })} />
            </div>
            <div className="row-actions">
              <button className="icon-button" onClick={() => refreshName(index)} title="Refresh name" aria-label="Refresh name">
                {busyUrl === row.url ? <Loader2 className="spin" size={17} /> : <RefreshCw size={17} />}
              </button>
              <button className="icon-button" onClick={() => navigator.clipboard.writeText(row.url)} title="Copy URL" aria-label="Copy URL">
                <Copy size={17} />
              </button>
              <button className="icon-button" onClick={() => move(index, -1)} disabled={index === 0} title="Move up" aria-label="Move up">
                <ArrowUp size={17} />
              </button>
              <button className="icon-button" onClick={() => move(index, 1)} disabled={index === rows.length - 1} title="Move down" aria-label="Move down">
                <ArrowDown size={17} />
              </button>
              <button className="icon-button danger" onClick={() => remove(index)} title="Remove" aria-label="Remove">
                <Trash2 size={17} />
              </button>
            </div>
          </article>
        ))}
      </div>
      <div className="save-strip">
        <button className="primary-button" onClick={() => onSave(rows)} disabled={saving}>
          {saving ? <Loader2 className="spin" size={18} /> : <Save size={18} />}
          Save {kind}
        </button>
      </div>
    </section>
  );
}

function WatchProgressView({
  rows,
  libraryItems,
  watchedItems,
  profileId,
  onRowsChange,
  onNotice,
}: {
  rows: WatchProgressRow[];
  libraryItems: LibraryItemRow[];
  watchedItems: WatchedItemRow[];
  profileId: number;
  onRowsChange: (rows: WatchProgressRow[]) => void;
  onNotice: (notice: Notice) => void;
}) {
  const [editing, setEditing] = useState<WatchProgressRow | null | "new">(null);
  const [hideCompleted, setHideCompleted] = useState(true);
  const [remoteMetaByContentId, setRemoteMetaByContentId] = useState<Record<string, RemoteContentMeta | null>>({});
  const libraryByContentId = useMemo(() => {
    const map = new Map<string, LibraryItemRow>();
    for (const item of libraryItems) {
      map.set(item.content_id, item);
    }
    return map;
  }, [libraryItems]);
  const watchedByEpisode = useMemo(() => {
    const map = new Map<string, WatchedItemRow>();
    for (const item of watchedItems) {
      map.set(progressKeyFor(item.content_id, item.season, item.episode), item);
      map.set(item.content_id, item);
    }
    return map;
  }, [watchedItems]);
  useEffect(() => {
    let cancelled = false;
    const missingRows = rows.filter((row) => isImdbId(row.content_id) && !(row.content_id in remoteMetaByContentId));
    const uniqueRows = Array.from(new Map(missingRows.map((row) => [row.content_id, row])).values());
    if (uniqueRows.length === 0) return;

    Promise.allSettled(uniqueRows.map(async (row) => [row.content_id, await fetchRemoteContentMeta(row.content_id, row.content_type)] as const))
      .then((results) => {
        if (cancelled) return;
        setRemoteMetaByContentId((previous) => {
          const next = { ...previous };
          for (const result of results) {
            if (result.status === "fulfilled") {
              const [contentId, meta] = result.value;
              next[contentId] = meta;
            }
          }
          results.forEach((result, index) => {
            if (result.status === "rejected") {
              next[uniqueRows[index].content_id] = null;
            }
          });
          return next;
        });
      });

    return () => {
      cancelled = true;
    };
  }, [rows, remoteMetaByContentId]);
  const completedCount = rows.filter((row) => row.duration > 0 && row.position / row.duration >= 0.9).length;
  const visibleRows = hideCompleted
    ? rows.filter((row) => !(row.duration > 0 && row.position / row.duration >= 0.9))
    : rows;

  async function saveProgress(form: FormData) {
    const contentId = String(form.get("content_id") ?? "").trim();
    if (!contentId) return;
    const row = editing === "new" ? null : editing;
    const season = nullableNumber(String(form.get("season") ?? ""));
    const episode = nullableNumber(String(form.get("episode") ?? ""));
    const payload = {
      profile_id: profileId,
      content_id: contentId,
      content_type: String(form.get("content_type") ?? "movie"),
      video_id: String(form.get("video_id") ?? contentId).trim() || contentId,
      season,
      episode,
      position: toNumber(String(form.get("position") ?? "0")),
      duration: toNumber(String(form.get("duration") ?? "0")),
      last_watched: toNumber(String(form.get("last_watched") ?? ""), Date.now()),
      progress_key: progressKeyFor(contentId, season, episode),
    };
    const request = row
      ? supabase.from("watch_progress").update(payload).eq("id", row.id).select("*").single()
      : supabase.from("watch_progress").insert(payload).select("*").single();
    const { data, error } = await request;
    if (error) return onNotice({ kind: "error", text: error.message });
    onRowsChange(row ? rows.map((item) => (item.id === row.id ? data as WatchProgressRow : item)) : [data as WatchProgressRow, ...rows]);
    onNotice({ kind: "success", text: "Watch progress saved." });
    setEditing(null);
  }

  async function deleteRow(row: WatchProgressRow) {
    if (!window.confirm(`Delete progress for ${row.content_id}?`)) return;
    const { error } = await supabase.from("watch_progress").delete().eq("id", row.id);
    if (error) return onNotice({ kind: "error", text: error.message });
    onRowsChange(rows.filter((item) => item.id !== row.id));
  }

  async function deleteAll() {
    if (!window.confirm("Delete all watch progress for this profile?")) return;
    const { error } = await supabase.from("watch_progress").delete().eq("profile_id", profileId);
    if (error) return onNotice({ kind: "error", text: error.message });
    onRowsChange([]);
  }

  return (
    <section className="data-panel">
      <DataToolbar
        title="Watch Progress"
        subtitle={`${visibleRows.length} entries visible - ${hideCompleted ? `${completedCount} completed hidden` : `${completedCount} completed`}`}
        onAdd={() => setEditing("new")}
        onDeleteAll={deleteAll}
        extra={
          <label className="check-control">
            <input type="checkbox" checked={hideCompleted} onChange={(event) => setHideCompleted(event.target.checked)} />
            Hide completed
          </label>
        }
      />
      <div className="progress-list">
        {visibleRows.length === 0 ? <div className="empty-state">No watch progress for this profile.</div> : visibleRows.map((row) => {
          const percent = row.duration > 0 ? Math.min(100, Math.round((row.position / row.duration) * 100)) : 0;
          const libraryItem = libraryByContentId.get(row.content_id) ?? libraryByContentId.get(row.video_id);
          const watchedItem = watchedByEpisode.get(progressKeyFor(row.content_id, row.season, row.episode)) ?? watchedByEpisode.get(row.content_id);
          const remoteMeta = remoteMetaByContentId[row.content_id];
          const remoteEpisode = remoteMeta?.videos.find((video) => video.season === row.season && video.episode === row.episode);
          const remoteEpisodeTitle = remoteEpisode?.name || remoteEpisode?.title || null;
          const remoteTitle = remoteEpisodeTitle || remoteMeta?.name || null;
          const displayTitle = watchedItem?.title || libraryItem?.name || remoteTitle || row.content_id;
          const seriesTitle = libraryItem?.name || remoteMeta?.name || null;
          const secondaryTitle = seriesTitle && seriesTitle !== displayTitle ? seriesTitle : null;
          const poster = libraryItem?.poster || remoteMeta?.poster;
          return (
            <article className={poster ? "progress-card has-poster" : "progress-card"} key={row.id}>
              {poster && <img className="progress-poster" src={poster} alt="" />}
              <div className="media-meta">
                <h2>{displayTitle}</h2>
                <p>
                  <span className="pill">{row.content_type}</span>{" "}
                  {row.season != null && row.episode != null ? `S${row.season}E${row.episode}` : "Movie"}
                  {secondaryTitle ? ` - ${secondaryTitle}` : ""}
                </p>
                {displayTitle !== row.content_id && <p className="content-id">{row.content_id}</p>}
                <p>{formatRuntime(row.position)} / {formatRuntime(row.duration)} ({percent}%)</p>
                <div className="progress-track"><span style={{ width: `${percent}%` }} /></div>
                <p>Last watched: {formatDate(row.last_watched)}</p>
              </div>
              <RowButtons onEdit={() => setEditing(row)} onDelete={() => deleteRow(row)} />
            </article>
          );
        })}
      </div>
      {editing && (
        <WatchProgressModal
          row={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSave={saveProgress}
        />
      )}
    </section>
  );
}

function LibraryView({
  rows,
  profileId,
  onRowsChange,
  onNotice,
}: {
  rows: LibraryItemRow[];
  profileId: number;
  onRowsChange: (rows: LibraryItemRow[]) => void;
  onNotice: (notice: Notice) => void;
}) {
  const [editing, setEditing] = useState<LibraryItemRow | null | "new">(null);

  async function saveLibrary(form: FormData) {
    const contentId = String(form.get("content_id") ?? "").trim();
    if (!contentId) return;
    const row = editing === "new" ? null : editing;
    const genres = String(form.get("genres") ?? "")
      .split(",")
      .map((genre) => genre.trim())
      .filter(Boolean);
    const payload = {
      profile_id: profileId,
      content_id: contentId,
      content_type: String(form.get("content_type") ?? "movie"),
      name: String(form.get("name") ?? contentId).trim() || contentId,
      poster: String(form.get("poster") ?? "").trim() || null,
      poster_shape: row?.poster_shape ?? "POSTER",
      background: String(form.get("background") ?? "").trim() || null,
      description: String(form.get("description") ?? "").trim() || null,
      release_info: String(form.get("release_info") ?? "").trim() || null,
      imdb_rating: nullableNumber(String(form.get("imdb_rating") ?? "")),
      genres,
      added_at: row?.added_at ?? Date.now(),
    };
    const request = row
      ? supabase.from("library_items").update(payload).eq("id", row.id).select("*").single()
      : supabase.from("library_items").insert(payload).select("*").single();
    const { data, error } = await request;
    if (error) return onNotice({ kind: "error", text: error.message });
    onRowsChange(row ? rows.map((item) => (item.id === row.id ? data as LibraryItemRow : item)) : [data as LibraryItemRow, ...rows]);
    onNotice({ kind: "success", text: "Library item saved." });
    setEditing(null);
  }

  async function deleteRow(row: LibraryItemRow) {
    if (!window.confirm(`Delete ${row.name}?`)) return;
    const { error } = await supabase.from("library_items").delete().eq("id", row.id);
    if (error) return onNotice({ kind: "error", text: error.message });
    onRowsChange(rows.filter((item) => item.id !== row.id));
  }

  async function deleteAll() {
    if (!window.confirm("Delete all library items for this profile?")) return;
    const { error } = await supabase.from("library_items").delete().eq("profile_id", profileId);
    if (error) return onNotice({ kind: "error", text: error.message });
    onRowsChange([]);
  }

  return (
    <section className="data-panel">
      <DataToolbar title="Library" subtitle={`${rows.length} items saved`} onAdd={() => setEditing("new")} onDeleteAll={deleteAll} />
      <div className="library-grid">
        {rows.length === 0 ? <div className="empty-state">No library items for this profile.</div> : rows.map((row) => (
          <article className="library-card" key={row.id}>
            {row.poster ? <img src={row.poster} alt="" /> : <div className="poster-placeholder" />}
            <div>
              <h2>{row.name}</h2>
              <p><span className="pill">{row.content_type}</span> {row.release_info}</p>
              <p>{row.content_id}</p>
              <RowButtons onEdit={() => setEditing(row)} onDelete={() => deleteRow(row)} />
            </div>
          </article>
        ))}
      </div>
      {editing && (
        <LibraryModal
          row={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSave={saveLibrary}
        />
      )}
    </section>
  );
}

function WatchedView({
  rows,
  profileId,
  total,
  page,
  onPageChange,
  onRowsChange,
  onTotalChange,
  onNotice,
}: {
  rows: WatchedItemRow[];
  profileId: number;
  total: number;
  page: number;
  onPageChange: (page: number) => void;
  onRowsChange: (rows: WatchedItemRow[]) => void;
  onTotalChange: (total: number) => void;
  onNotice: (notice: Notice) => void;
}) {
  const [editing, setEditing] = useState<WatchedItemRow | null | "new">(null);
  const pageSize = 50;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));

  async function saveWatched(form: FormData) {
    const contentId = String(form.get("content_id") ?? "").trim();
    if (!contentId) return;
    const row = editing === "new" ? null : editing;
    const payload = {
      profile_id: profileId,
      content_id: contentId,
      content_type: String(form.get("content_type") ?? "movie"),
      title: String(form.get("title") ?? contentId).trim() || contentId,
      season: nullableNumber(String(form.get("season") ?? "")),
      episode: nullableNumber(String(form.get("episode") ?? "")),
      watched_at: toNumber(String(form.get("watched_at") ?? ""), row?.watched_at ?? Date.now()),
    };
    const request = row
      ? supabase.from("watched_items").update(payload).eq("id", row.id).select("*").single()
      : supabase.from("watched_items").insert(payload).select("*").single();
    const { data, error } = await request;
    if (error) return onNotice({ kind: "error", text: error.message });
    onRowsChange(row ? rows.map((item) => (item.id === row.id ? data as WatchedItemRow : item)) : [data as WatchedItemRow, ...rows]);
    if (!row) onTotalChange(total + 1);
    onNotice({ kind: "success", text: "Watched item saved." });
    setEditing(null);
  }

  async function deleteRow(row: WatchedItemRow) {
    if (!window.confirm(`Delete ${row.title}?`)) return;
    const { error } = await supabase.from("watched_items").delete().eq("id", row.id);
    if (error) return onNotice({ kind: "error", text: error.message });
    onRowsChange(rows.filter((item) => item.id !== row.id));
    onTotalChange(Math.max(0, total - 1));
  }

  async function deleteAll() {
    if (!window.confirm("Delete all watched records for this profile?")) return;
    const { error } = await supabase.from("watched_items").delete().eq("profile_id", profileId);
    if (error) return onNotice({ kind: "error", text: error.message });
    onRowsChange([]);
    onTotalChange(0);
    onPageChange(1);
  }

  return (
    <section className="data-panel">
      <DataToolbar title="Watched" subtitle={`${rows.length} records on page ${page} - ${total} total`} onAdd={() => setEditing("new")} onDeleteAll={deleteAll} />
      <div className="watched-list">
        {rows.length === 0 ? <div className="empty-state">No watched records for this profile.</div> : rows.map((row) => (
          <article className="watched-row" key={row.id}>
            <div>
              <h2>{row.title}</h2>
              <p><span className="pill">{row.content_type}</span> {row.season != null && row.episode != null ? `S${row.season}E${row.episode}` : row.content_id}</p>
            </div>
            <RowButtons onEdit={() => setEditing(row)} onDelete={() => deleteRow(row)} />
          </article>
        ))}
      </div>
      <div className="pagination">
        <button className="secondary-button" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>Previous</button>
        <span>Page {page} of {pageCount}</span>
        <button className="secondary-button" disabled={page >= pageCount} onClick={() => onPageChange(page + 1)}>Next</button>
      </div>
      {editing && (
        <WatchedModal
          row={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSave={saveWatched}
        />
      )}
    </section>
  );
}

function DataToolbar({
  title,
  subtitle,
  onAdd,
  onDeleteAll,
  extra,
}: {
  title: string;
  subtitle: string;
  onAdd: () => void;
  onDeleteAll: () => void;
  extra?: React.ReactNode;
}) {
  return (
    <div className="data-toolbar">
      <div>
        <h2>{title}</h2>
        <p>{subtitle}</p>
      </div>
      <div className="top-actions">
        {extra}
        <button className="secondary-button danger-text" onClick={onDeleteAll}>
          Delete all
        </button>
        <button className="primary-button" onClick={onAdd}>
          <Plus size={18} />
          Add
        </button>
      </div>
    </div>
  );
}

function RowButtons({ onEdit, onDelete }: { onEdit: () => void; onDelete: () => void }) {
  return (
    <div className="row-actions compact">
      <button className="secondary-button" onClick={onEdit}>Edit</button>
      <button className="secondary-button" onClick={onDelete}>Delete</button>
    </div>
  );
}

function WatchProgressModal({ row, onClose, onSave }: { row: WatchProgressRow | null; onClose: () => void; onSave: (form: FormData) => void }) {
  return (
    <EditModal title="Watch Progress Entry" onClose={onClose} onSave={onSave}>
      <input defaultValue={row?.progress_key ?? ""} placeholder="Progress key" disabled />
      <input name="content_id" defaultValue={row?.content_id ?? ""} placeholder="Content ID" required />
      <select name="content_type" defaultValue={row?.content_type ?? "movie"}>
        <option value="movie">Movie</option>
        <option value="series">Series</option>
      </select>
      <input name="video_id" defaultValue={row?.video_id ?? ""} placeholder="Video ID" />
      <div className="modal-grid-2">
        <input name="season" defaultValue={row?.season ?? ""} placeholder="Season" inputMode="numeric" />
        <input name="episode" defaultValue={row?.episode ?? ""} placeholder="Episode" inputMode="numeric" />
      </div>
      <div className="modal-grid-2">
        <input name="position" defaultValue={row?.position ?? 0} placeholder="Position ms" inputMode="numeric" />
        <input name="duration" defaultValue={row?.duration ?? 0} placeholder="Duration ms" inputMode="numeric" />
      </div>
      <input name="last_watched" defaultValue={row?.last_watched ?? Date.now()} placeholder="Last watched ms" inputMode="numeric" />
    </EditModal>
  );
}

function LibraryModal({ row, onClose, onSave }: { row: LibraryItemRow | null; onClose: () => void; onSave: (form: FormData) => void }) {
  return (
    <EditModal title="Library Item" onClose={onClose} onSave={onSave} wide>
      <input name="content_id" defaultValue={row?.content_id ?? ""} placeholder="Content ID" required />
      <input name="name" defaultValue={row?.name ?? ""} placeholder="Title" />
      <select name="content_type" defaultValue={row?.content_type ?? "movie"}>
        <option value="movie">Movie</option>
        <option value="series">Series</option>
      </select>
      <input name="poster" defaultValue={row?.poster ?? ""} placeholder="Poster URL" />
      <input name="background" defaultValue={row?.background ?? ""} placeholder="Background URL" />
      <input name="release_info" defaultValue={row?.release_info ?? ""} placeholder="Release info" />
      <input name="imdb_rating" defaultValue={row?.imdb_rating ?? ""} placeholder="IMDb rating" inputMode="decimal" />
      <textarea name="description" defaultValue={row?.description ?? ""} placeholder="Description" rows={4} />
      <input name="genres" defaultValue={row?.genres?.join(", ") ?? ""} placeholder="Genres, comma separated" />
    </EditModal>
  );
}

function WatchedModal({ row, onClose, onSave }: { row: WatchedItemRow | null; onClose: () => void; onSave: (form: FormData) => void }) {
  return (
    <EditModal title="Watched Item" onClose={onClose} onSave={onSave}>
      <input name="content_id" defaultValue={row?.content_id ?? ""} placeholder="Content ID" required />
      <input name="title" defaultValue={row?.title ?? ""} placeholder="Title" />
      <select name="content_type" defaultValue={row?.content_type ?? "movie"}>
        <option value="movie">Movie</option>
        <option value="series">Series</option>
      </select>
      <div className="modal-grid-2">
        <input name="season" defaultValue={row?.season ?? ""} placeholder="Season" inputMode="numeric" />
        <input name="episode" defaultValue={row?.episode ?? ""} placeholder="Episode" inputMode="numeric" />
      </div>
      <input name="watched_at" defaultValue={row?.watched_at ?? Date.now()} placeholder="Watched at ms" inputMode="numeric" />
    </EditModal>
  );
}

function EditModal({
  title,
  children,
  onSave,
  onClose,
  wide = false,
}: {
  title: string;
  children: React.ReactNode;
  onSave: (form: FormData) => void;
  onClose: () => void;
  wide?: boolean;
}) {
  return (
    <div className="modal-backdrop" role="presentation">
      <form
        className={wide ? "edit-modal wide" : "edit-modal"}
        onSubmit={(event) => {
          event.preventDefault();
          onSave(new FormData(event.currentTarget));
        }}
      >
        <h2>{title}</h2>
        <div className="modal-fields">{children}</div>
        <div className="modal-actions">
          <button className="secondary-button" type="button" onClick={onClose}>Close</button>
          <button className="primary-button" type="submit">Save</button>
        </div>
      </form>
    </div>
  );
}

function ProfilesView({ profiles, activeProfile }: { profiles: Profile[]; activeProfile: number }) {
  return (
    <section className="profiles-view">
      {profiles.map((profile) => (
        <article key={profile.profile_index} className={profile.profile_index === activeProfile ? "profile-card active" : "profile-card"}>
          <span className="avatar-dot" style={{ background: profile.avatar_color_hex }} />
          <div>
            <h2>{profile.name || `Profile ${profile.profile_index}`}</h2>
            <p>Profile {profile.profile_index}</p>
          </div>
          <dl>
            <div>
              <dt>Addons</dt>
              <dd>{profile.uses_primary_addons ? "Shared from profile 1" : "Own list"}</dd>
            </div>
            <div>
              <dt>Plugins</dt>
              <dd>{profile.uses_primary_plugins ? "Shared from profile 1" : "Own list"}</dd>
            </div>
            <div>
              <dt>PIN</dt>
              <dd>{profile.pin_enabled ? "Enabled" : "Off"}</dd>
            </div>
          </dl>
        </article>
      ))}
    </section>
  );
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
