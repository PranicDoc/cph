import { useState } from "react";
import { useAuth } from "../../context/AuthContext";
import type { UserProfileIconProps } from "./types/user-profile";
import { CreditCard, LogOut, Settings } from "lucide-react";
import { PlanPicker } from "../PlanPicker";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

// ── Helpers ──
function getInitials(name: string): string {
  return name
    .split(" ")
    .map((part) => part[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);
}

// ── Component ──
export function UserProfileIcon({
  onSettings,
  className = "",
}: UserProfileIconProps) {
  const { user, logout } = useAuth();
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);

  if (!user) return null;

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className={`w-9 h-9 rounded-full overflow-hidden border-2 border-gray-200 hover:border-indigo-400 p-0 hover:bg-transparent ${className}`}
          >
            <Avatar className="w-full h-full rounded-full">
              {user.avatarUrl && (
                <AvatarImage src={user.avatarUrl} alt={user.fullName} />
              )}
              <AvatarFallback className="bg-indigo-600 text-white text-sm font-semibold rounded-none">
                {getInitials(user.fullName)}
              </AvatarFallback>
            </Avatar>
          </Button>
        </DropdownMenuTrigger>

        <DropdownMenuContent align="end" className="w-64">
          <DropdownMenuLabel className="p-0">
            <div className="flex items-center gap-3 px-4 py-3">
              <Avatar className="w-10 h-10">
                {user.avatarUrl && (
                  <AvatarImage src={user.avatarUrl} alt={user.fullName} />
                )}
                <AvatarFallback className="bg-indigo-600 text-white text-sm font-semibold">
                  {getInitials(user.fullName)}
                </AvatarFallback>
              </Avatar>
              <div className="min-w-0">
                <p className="text-sm font-medium text-gray-900 truncate">
                  {user.fullName}
                </p>
                <p className="text-xs text-gray-500 truncate font-normal">
                  {user.email}
                </p>
              </div>
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <DropdownMenuItem onSelect={() => setShowUpgradeModal(true)}>
              <CreditCard className="w-4 h-4 text-gray-400" />
              Upgrade Plan
            </DropdownMenuItem>
            {onSettings && (
              <DropdownMenuItem onSelect={() => onSettings()}>
                <Settings className="w-4 h-4 text-gray-400" />
                Settings
              </DropdownMenuItem>
            )}
            <DropdownMenuItem
              variant="destructive"
              onSelect={() => logout()}
            >
              <LogOut className="w-4 h-4" />
              Logout
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </DropdownMenuContent>
      </DropdownMenu>

      <Dialog open={showUpgradeModal} onOpenChange={setShowUpgradeModal}>
        <DialogContent className="w-full sm:max-w-3xl gap-0 rounded-t-2xl sm:rounded-2xl max-h-[92vh] flex flex-col p-0 max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full">
          <DialogTitle className="sr-only">Upgrade Plan</DialogTitle>
          <div className="sm:hidden flex justify-center pt-3 pb-1 shrink-0">
            <div className="w-10 h-1 bg-border rounded-full" />
          </div>
          <div className="flex-1 min-h-0 overflow-y-auto p-5 sm:p-8">
            <PlanPicker
              ownerId={user?.id ?? ""}
              ownerType="HEALER"
              onComplete={() => setShowUpgradeModal(false)}
            />
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

export default UserProfileIcon;
