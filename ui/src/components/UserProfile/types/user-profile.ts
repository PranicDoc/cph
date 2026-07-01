// ── User Profile Icon — Type Definitions ──

/** Minimal user information displayed in the profile popover */
export interface UserInfo {
  id: string;
  fullName: string;
  email: string;
  phone?: string;
  avatarUrl?: string;
  role?: string;
  clinicId?: string;
  clinicCode?: string;
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
