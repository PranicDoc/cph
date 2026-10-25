import { Button } from "@/components/ui/button";
import { Link, useLocation } from "react-router-dom";
import { X } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "../lib/utils";

export interface SidebarNavItem {
  label: string;
  path: string;
  icon: LucideIcon;
  exact?: boolean;
  /** Shown only to users who hold `verb` (default READ) on any of these RBAC resources; omit for items everyone sees. */
  anyOf?: string[];
  verb?: string;
}

interface SidebarProps {
  items: SidebarNavItem[];
  isOpen: boolean;
  onClose: () => void;
}

export default function Sidebar({ items, isOpen, onClose }: SidebarProps) {
  const { pathname } = useLocation();

  const navLinks = (
    <nav className="flex flex-col gap-1 py-3 px-2">
      {items.map(({ label, path, icon: Icon, exact }) => {
        const isActive = exact ? pathname === path : pathname.startsWith(path);
        return (
          <Link
            key={path}
            to={path}
            onClick={onClose}
            className={cn(
              "flex items-center gap-3 px-2 py-2 rounded-lg text-sm font-medium transition-colors",
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
      {/* Desktop — width slides open/closed; border on inner div so it clips cleanly at width 0 */}
      <aside
        style={{ width: isOpen ? "14rem" : "0" }}
        className="hidden md:flex flex-col shrink-0 overflow-hidden transition-all duration-300 ease-in-out"
      >
        <div className="w-56 shrink-0 border-r border-border h-full">
          {navLinks}
        </div>
      </aside>

      {/* Mobile — always in DOM; backdrop fades, drawer slides, pointer-events off when closed */}
      <div
        className={cn(
          "md:hidden fixed inset-0 z-[60] flex",
          isOpen ? "pointer-events-auto" : "pointer-events-none"
        )}
      >
        <div
          className={cn(
            "absolute inset-0 bg-black/40 transition-opacity duration-300 ease-in-out",
            isOpen ? "opacity-100" : "opacity-0"
          )}
          onClick={onClose}
          aria-hidden="true"
        />
        <aside
          className={cn(
            "relative w-56 bg-card border-r border-border flex flex-col shadow-xl",
            "transition-transform duration-300 ease-in-out",
            isOpen ? "translate-x-0" : "-translate-x-full"
          )}
        >
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
          {navLinks}
        </aside>
      </div>
    </>
  );
}
