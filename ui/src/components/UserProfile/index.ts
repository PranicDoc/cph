// ── User Profile Icon — Standalone Export ──

export { UserProfileIcon, default } from "./UserProfileIcon";

// Context & Provider
export {
  UserProfileAuthProvider,
  useUserProfileAuth,
  UserProfileAuthContext,
} from "./UserProfileAuthContext";

// Types (re-export for consumers)
export type {
  UserInfo,
  AuthContextValue,
  UserProfileIconProps,
} from "./types/user-profile";
