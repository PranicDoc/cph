// ── User Profile Icon — Type Definitions ──

/** Minimal user information displayed in the profile popover */
export interface UserInfo {
  fullName: string;
  email: string;
  avatarUrl?: string;
}

/** Auth context contract the component expects */
export interface AuthContextValue {
  user: UserInfo | null;
  logout: () => void;
}

/** Props for the UserProfileIcon component */
export interface UserProfileIconProps {
  /** Callback when "Settings" is clicked */
  onSettings?: () => void;
  /** Additional CSS classes for the root container */
  className?: string;
}
