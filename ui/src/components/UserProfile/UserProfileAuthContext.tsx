import { createContext, useContext, type ReactNode } from "react";
import type { AuthContextValue, UserInfo } from "./types/user-profile";

// ── Context ──

const UserProfileAuthContext = createContext<AuthContextValue | null>(null);

// ── Provider ──

interface UserProfileAuthProviderProps {
  user: UserInfo | null;
  logout: () => void;
  children: ReactNode;
}

export function UserProfileAuthProvider({
  user,
  logout,
  children,
}: UserProfileAuthProviderProps) {
  return (
    <UserProfileAuthContext.Provider value={{ user, logout }}>
      {children}
    </UserProfileAuthContext.Provider>
  );
}

// ── Hook ──

export function useUserProfileAuth(): AuthContextValue {
  const ctx = useContext(UserProfileAuthContext);
  if (!ctx) {
    throw new Error(
      "useUserProfileAuth must be used within a <UserProfileAuthProvider>"
    );
  }
  return ctx;
}

export { UserProfileAuthContext };
