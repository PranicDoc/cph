import { useState } from "react";
import { Link, Outlet } from "react-router-dom";
import { Activity, Menu } from "lucide-react";
import UserProfileIcon from "./UserProfile";
import Sidebar from "./Sidebar";
import { healerNavItems } from "../config/healerNavItems";
import { Button } from "@/components/ui/button";

export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-gradient-to-r from-primary-dark to-primary text-white sticky top-0 z-50 shadow-lg shadow-primary/20">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon-sm"
              className="-ml-2 h-auto w-auto p-1.5 hover:bg-white/15"
              onClick={() => setSidebarOpen(!sidebarOpen)}
              aria-label="Open menu"
            >
              <Menu className="w-5 h-5 text-white" />
            </Button>
            <Link to="/" className="flex items-center gap-3 text-2xl font-bold">
              <div className="w-10 h-10 bg-white/20 rounded-xl flex items-center justify-center">
                <Activity className="w-6 h-6 text-white" />
              </div>
              <span>HealingDoc</span>
            </Link>
          </div>

          <UserProfileIcon />
        </div>
      </header>

      <div className="flex flex-1 max-w-7xl w-full mx-auto">
        <Sidebar
          items={healerNavItems}
          isOpen={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
        />
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
