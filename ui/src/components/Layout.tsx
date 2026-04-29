import { Link, Outlet, useLocation } from "react-router-dom";
import { Activity } from "lucide-react";
import { cn } from "../lib/utils";

export default function Layout() {
  const location = useLocation();

  const navItems = [
    // { name: "Patients", path: "/" },
    // { name: "Registration", path: "/registration" },
    // { name: "Healing Session", path: "/session" },
  ];

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-gradient-to-r from-primary-dark to-primary text-white sticky top-0 z-50 shadow-lg shadow-primary/20">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-3 text-2xl font-bold">
            <div className="w-10 h-10 bg-white/20 rounded-xl flex items-center justify-center">
              <Activity className="w-6 h-6 text-white" />
            </div>
            <span>PranicDoc</span>
          </Link>

          <nav className="hidden md:flex items-center gap-1 bg-white/10 p-1 rounded-xl">
            {navItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                className={cn(
                  "px-5 py-2 rounded-lg text-sm font-medium transition-colors",
                  location.pathname === item.path
                    ? "bg-white text-primary"
                    : "text-white/70 hover:bg-white/15 hover:text-white"
                )}
              >
                {item.name}
              </Link>
            ))}
          </nav>

          <div className="flex items-center gap-4">
            <span className="bg-secondary text-white px-3 py-1 rounded-full text-xs font-semibold">
              v1.0 Draft
            </span>
          </div>
        </div>
      </header>

      <main className="flex-1 max-w-7xl w-full mx-auto p-6">
        <Outlet />
      </main>
    </div>
  );
}
