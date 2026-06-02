export type Profile = {
  id: string;
  user_id: string;
  profile_index: number;
  name: string;
  avatar_color_hex: string;
  avatar_id: string | null;
  avatar_url: string | null;
  uses_primary_addons: boolean;
  uses_primary_plugins: boolean;
  pin_enabled: boolean;
  pin_locked_until: string | null;
  created_at: string;
  updated_at: string;
};

export type RemoteEntry = {
  url: string;
  name: string | null;
  enabled: boolean;
  sort_order: number;
};

export type Notice = {
  kind: "success" | "error" | "info";
  text: string;
} | null;

export type WatchProgressRow = {
  id: string;
  profile_id: number;
  content_id: string;
  content_type: string;
  video_id: string;
  season: number | null;
  episode: number | null;
  position: number;
  duration: number;
  last_watched: number;
  progress_key: string;
};

export type LibraryItemRow = {
  id: string;
  profile_id: number;
  content_id: string;
  content_type: string;
  name: string;
  poster: string | null;
  poster_shape: string;
  background: string | null;
  description: string | null;
  release_info: string | null;
  imdb_rating: number | null;
  genres: string[];
  added_at: number;
};

export type WatchedItemRow = {
  id: string;
  profile_id: number;
  content_id: string;
  content_type: string;
  title: string;
  season: number | null;
  episode: number | null;
  watched_at: number;
};

export type TabKey = "addons" | "plugins" | "watch-progress" | "library" | "watched" | "profiles" | "super-admin";
