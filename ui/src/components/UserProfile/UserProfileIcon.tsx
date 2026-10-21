import { useState } from "react";
import { useAuth } from "../../context/AuthContext";
import type { UserProfileIconProps } from "./types/user-profile";
import { CreditCard, LogOut, MessageSquare, Settings, Sparkles } from "lucide-react";
import { toast } from "sonner";
import { useUiVersion, type ThemeChoice } from "../../ui-version";
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
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
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

const FEEDBACK_HREF = "mailto:hello@healingark.in?subject=New%20view%20feedback";

// ── Component ──
export function UserProfileIcon({
  onSettings,
  className = "",
}: UserProfileIconProps) {
  const { user, logout } = useAuth();
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const { isNewView, canSwitch, setUiVersion, theme, setTheme } = useUiVersion();

  const switchView = () => {
    if (isNewView) {
      void setUiVersion("classic");
    } else {
      void setUiVersion("new");
      toast.success("New view is on. You can switch back anytime from your profile menu.");
    }
  };

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
            {canSwitch && (
              <DropdownMenuItem onSelect={switchView}>
                <Sparkles className="w-4 h-4 text-gray-400" />
                <span className="flex-1">
                  {isNewView ? "Switch to classic view" : "Try the new view"}
                </span>
                <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">
                  {isNewView ? "Beta" : "New"}
                </span>
              </DropdownMenuItem>
            )}
            {canSwitch && isNewView && (
              <DropdownMenuItem asChild>
                <a href={FEEDBACK_HREF}>
                  <MessageSquare className="w-4 h-4 text-gray-400" />
                  Share feedback
                </a>
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
          {canSwitch && isNewView && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuLabel className="text-xs font-semibold text-muted-foreground">
                Theme
              </DropdownMenuLabel>
              <DropdownMenuRadioGroup
                value={theme}
                onValueChange={(v) => setTheme(v as ThemeChoice)}
              >
                <DropdownMenuRadioItem value="dark">Dark</DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="light">Light</DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="system">System</DropdownMenuRadioItem>
              </DropdownMenuRadioGroup>
            </>
          )}
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
