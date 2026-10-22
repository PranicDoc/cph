import { Button } from "@/components/ui/button";
import { Link, useLocation } from "react-router-dom";
import { X } from "lucide-react";
import { cn } from "../lib/utils";
import { healerNavItems } from "../config/healerNavItems";
import { useMyPermissions } from "../hooks/useMyPermissions";
import { visibleNavItems } from "../lib/adminAccess";

interface HealerSidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function HealerSidebar({ isOpen, onClose }: HealerSidebarProps) {
  const { pathname } = useLocation();
  const { data: permissions } = useMyPermissions();

  const navContent = (
    <nav className="flex flex-col gap-1 p-3">
      {visibleNavItems(permissions, healerNavItems).map(({ label, path, icon: Icon, exact }) => {
        const isActive = exact ? pathname === path : pathname.startsWith(path);
        return (
          <Link
            key={path}
            to={path}
            onClick={onClose}
            className={cn(
              "flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors",
              isActive
                ? "bg-primary text-white"
                : "text-text-light hover:bg-muted/50 hover:text-text"
            )}
          >
            <Icon className="w-4 h-4 shrink-0" />
            {label}
          </Link>
        );
      })}
    </nav>
  );

  return (
    <>
      {/* Desktop — always visible */}
      <aside className="hidden md:block w-56 shrink-0 border-r border-border py-3">
        {navContent}
      </aside>

      {/* Mobile — overlay */}
      {isOpen && (
        <div className="md:hidden fixed inset-0 z-40 flex">
          {/* Backdrop */}
          <div
            className="absolute inset-0 bg-black/40"
            onClick={onClose}
            aria-hidden="true"
          />

          {/* Drawer */}
          <aside className="relative z-50 w-56 bg-card border-r border-border flex flex-col shadow-xl">
            <div className="flex items-center justify-between px-4 py-3 border-b border-border">
              <span className="text-sm font-semibold text-text">Menu</span>
              <Button
                variant="ghost"
                size="icon-xs"
                onClick={onClose}
                className="h-auto w-auto p-1 rounded-md text-text-light hover:text-text hover:bg-border"
                aria-label="Close menu"
                >
                <X className="w-4 h-4" />
                </Button>
            </div>
            {navContent}
          </aside>
        </div>
      )}
    </>
  );
}
