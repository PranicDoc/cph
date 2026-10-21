import { useState } from "react";
import { Link, Outlet, useLocation } from "react-router-dom";
import { Ellipsis, Moon, Sun } from "lucide-react";
import { cn } from "@/lib/utils";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import UserProfileIcon from "../../components/UserProfile";
import { useAuth } from "../../context/AuthContext";
import { useUiVersion } from "../../ui-version";
import type { SidebarNavItem } from "../../components/Sidebar";
import { BrandMark } from "../components/BrandMark";

export interface ShellNavItem extends SidebarNavItem {
  /** Heading the item sits under in the sidebar. Items without one come first. */
  group?: string;
}

interface NewShellProps {
  items: ShellNavItem[];
  /** Order of the sidebar headings; "" is the group of items without a heading. */
  groupOrder?: string[];
  /** Shown before the page name in the breadcrumb (e.g. "Admin"). */
  sectionLabel?: string;
}

const MAX_TABS = 5;

function isActive(item: SidebarNavItem, pathname: string): boolean {
  return item.exact ? pathname === item.path : pathname.startsWith(item.path);
}

function groupItems(items: ShellNavItem[], order: string[] = [""]): [string, ShellNavItem[]][] {
  const byGroup = new Map<string, ShellNavItem[]>();
  for (const item of items) {
    const key = item.group ?? "";
    byGroup.set(key, [...(byGroup.get(key) ?? []), item]);
  }
  const keys = [...order.filter((k) => byGroup.has(k)), ...[...byGroup.keys()].filter((k) => !order.includes(k))];
  return keys.map((k) => [k, byGroup.get(k)!]);
}

function initials(name: string): string {
  return name
    .split(" ")
    .map((p) => p[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);
}

function ThemeButton() {
  const { resolvedTheme, setTheme } = useUiVersion();
  const Icon = resolvedTheme === "dark" ? Sun : Moon;
  return (
    <button
      type="button"
      aria-label="Switch theme"
      onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
      className="hl-icon-btn"
    >
      <Icon className="size-[18px]" aria-hidden="true" />
    </button>
  );
}

function MobileTabs({ items, pathname }: { items: ShellNavItem[]; pathname: string }) {
  const [moreOpen, setMoreOpen] = useState(false);
  const needsMore = items.length > MAX_TABS;
  const tabs = needsMore ? items.slice(0, MAX_TABS - 1) : items;
  const rest = needsMore ? items.slice(MAX_TABS - 1) : [];
  const restActive = rest.some((i) => isActive(i, pathname));

  return (
    <>
      <nav aria-label="Main" className="hl-mtabs hl-glass flex md:hidden">
        {tabs.map((item) => (
          <Link
            key={item.path}
            to={item.path}
            aria-current={isActive(item, pathname) ? "page" : undefined}
            className="hl-mtab"
          >
            <item.icon className="size-[18px]" aria-hidden="true" />
            <span>{item.label}</span>
          </Link>
        ))}
        {needsMore && (
          <button
            type="button"
            className={cn("hl-mtab", restActive && "is-active")}
            onClick={() => setMoreOpen(true)}
          >
            <Ellipsis className="size-[18px]" aria-hidden="true" />
            <span>More</span>
          </button>
        )}
      </nav>
      {needsMore && (
        <Dialog open={moreOpen} onOpenChange={setMoreOpen}>
          <DialogContent className="hl-sheet max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full max-sm:rounded-b-none gap-1 p-4">
            <DialogTitle className="px-2 pb-2 text-base font-semibold">More</DialogTitle>
            {rest.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                onClick={() => setMoreOpen(false)}
                aria-current={isActive(item, pathname) ? "page" : undefined}
                className="hl-nav"
              >
                <item.icon className="size-[18px]" aria-hidden="true" />
                {item.label}
              </Link>
            ))}
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}

/**
 * The new view's app shell: floating glass sidebar and topbar on desktop, a glass header and a
 * floating tab bar on phones. It only supplies chrome; the page is whatever the route renders
 * into the Outlet, so routes, data and permissions are exactly the classic ones.
 */
export function NewShell({ items, groupOrder, sectionLabel }: NewShellProps) {
  const { pathname } = useLocation();
  const { user } = useAuth();
  const name = user?.fullName ?? "";
  const current = items
    .filter((i) => isActive(i, pathname))
    .sort((a, b) => b.path.length - a.path.length)[0];

  return (
    <div className="hl-shell">
      <aside className="hl-side hidden md:flex">
        <Link to="/" className="hl-brand">
          <BrandMark />
          <span>HealingARK</span>
        </Link>
        <nav aria-label="Sidebar" className="flex flex-col gap-1">
          {groupItems(items, groupOrder).map(([group, groupItemsList]) => (
            <div key={group || "main"} className="flex flex-col gap-1">
              {group && <h2 className="hl-nav-group">{group}</h2>}
              {groupItemsList.map((item) => (
                <Link
                  key={item.path}
                  to={item.path}
                  aria-current={isActive(item, pathname) ? "page" : undefined}
                  className="hl-nav"
                >
                  <item.icon className="size-[18px]" aria-hidden="true" />
                  {item.label}
                </Link>
              ))}
            </div>
          ))}
        </nav>
        <div className="hl-side-foot">
          <span className="hl-avatar" aria-hidden="true">{initials(name)}</span>
          <div className="min-w-0">
            <div className="truncate text-[13.5px] font-semibold">{name}</div>
          </div>
        </div>
      </aside>

      <div className="hl-main-col">
        <div className="hl-topbar hl-glass hidden md:flex">
          <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-sm">
            {sectionLabel && (
              <>
                <span className="text-text-light">{sectionLabel}</span>
                <span aria-hidden="true" className="text-text-light">/</span>
              </>
            )}
            <span className="font-semibold">{current?.label}</span>
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <ThemeButton />
            <UserProfileIcon />
          </div>
        </div>

        <header className="hl-mhead hl-glass flex md:hidden">
          <BrandMark size={32} />
          <span className="flex-1 truncate font-semibold">{current?.label ?? "HealingARK"}</span>
          <ThemeButton />
          <UserProfileIcon />
        </header>

        <main className="hl-content">
          <div className="hl-canvas">
            <Outlet />
          </div>
        </main>

        <MobileTabs items={groupItems(items, groupOrder).flatMap(([, list]) => list)} pathname={pathname} />
      </div>
    </div>
  );
}

export default NewShell;
