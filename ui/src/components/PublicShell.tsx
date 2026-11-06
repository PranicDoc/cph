import { Outlet } from "react-router-dom";
import { BrandMark } from "../ui-new/components/BrandMark";

export default function PublicShell() {
  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-gradient-to-r from-primary-dark to-primary text-white shadow-lg shadow-primary/20">
        <div className="max-w-7xl mx-auto px-6 py-4 flex items-center gap-3">
          <BrandMark size={40} />
          <span className="text-2xl font-bold">HealingARK</span>
        </div>
      </header>

      <main className="flex-1 max-w-7xl w-full mx-auto p-6">
        <Outlet />
      </main>
    </div>
  );
}
