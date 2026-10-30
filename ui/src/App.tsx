/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import type { ComponentType } from "react";
import { UiVersionProvider, versioned } from "./ui-version";
import {
  createBrowserRouter,
  createRoutesFromElements,
  RouterProvider,
  Route,
} from "react-router-dom";
import { Toaster } from "sonner";
import Layout from "./components/Layout";
import PublicShell from "./components/PublicShell";
import Login from "./pages/Login";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";
import AdminRoute from "./components/admin/AdminRoute";
import AdminLayout from "./components/admin/AdminLayout";
import { PatientProvider } from "./context/PatientContext";
import Unauthorized from "./pages/Unauthorized";
import Forbidden from "./pages/Forbidden";
import NotFound from "./pages/NotFound";
import OAuth2Success from "./components/OAuth2Success";

// Pages load when first opened, so the login screen doesn't download the whole app.
const page = (load: () => Promise<{ default: ComponentType }>) => async () => ({
  Component: (await load()).default,
});

// Each shell has a classic and a new-view version; the profile menu switches between them.
// Same routes either way, only the chrome differs (see docs/superpowers/plans/2026-10-21-new-view-dual-ui.md).
const HealerLayout = versioned(Layout, () => import("./ui-new/shell/HealerShell"));
const AdminLayoutRoute = versioned(AdminLayout, () => import("./ui-new/shell/AdminShell"));
const PublicLayout = versioned(PublicShell, () => import("./ui-new/shell/NewPublicShell"));

// react-router v7 data router. The route tree below is the exact same JSX
// tree that previously lived inside the declarative router element pair — it
// is only wrapped in createRoutesFromElements + createBrowserRouter so that
// data APIs (notably useBlocker, used by the discard-unsaved-changes guard on
// the role create/edit form) are available. Pages use route `lazy` (see `page`).
const router = createBrowserRouter(
  createRoutesFromElements(
    <>
      <Route path="/login" element={<Login />} />
      <Route path="/unauthorized" element={<Unauthorized />} />
      <Route path="/forbidden" element={<Forbidden />} />
      <Route path="/oauth2/success" element={<OAuth2Success />} />

      {/* Public pages — minimal branded shell, no nav */}
      <Route element={<PublicLayout />}>
        <Route path="/self-register" lazy={page(() => import("./pages/Registration"))} />
        <Route
          path="/self-register/:healerId/:uuid"
          lazy={page(() => import("./pages/Registration"))}
        />
        <Route path="/feedback" lazy={page(() => import("./pages/Feedback"))} />
        <Route path="/patient-health/:caseId" lazy={page(() => import("./pages/PatientHealthForm"))} />
        <Route path="/accept-invite" lazy={page(() => import("./pages/AcceptInvite"))} />
        <Route path="/signup" lazy={page(() => import("./pages/Signup"))} />
        <Route path="/forgot-password" lazy={page(() => import("./pages/ForgotPassword"))} />
        <Route path="/reset-password" lazy={page(() => import("./pages/ResetPassword"))} />
      </Route>

      {/* Protected healer routes */}
      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<HealerLayout />}>
          <Route index lazy={page(() => import("./pages/Dashboard"))} />
          <Route path="patients" lazy={page(() => import("./pages/Patients"))} />
          <Route path="registration" lazy={page(() => import("./pages/Registration"))} />
          <Route path="pdf-list" lazy={page(() => import("./pages/PdfList").then((m) => ({ default: m.PdfList })))} />
          <Route path="session/:healingCaseId" lazy={page(() => import("./pages/Session"))} />
          <Route path="cases/:healingCaseId/summary" lazy={page(() => import("./pages/CaseSummary"))} />
          <Route path="cases/:healingCaseId/feedback" lazy={page(() => import("./pages/ManualFeedback"))} />
          <Route path="profile" lazy={page(() => import("./pages/Profile"))} />
        </Route>
        {/* Sibling of the Layout route, not nested inside it: /legal-center
            must stay behind ProtectedRoute's gate check but must not render
            Layout's nav/sidebar chrome (UI-SPEC item 1). */}
        <Route path="/legal-center" lazy={page(() => import("./pages/LegalCenter"))} />
      </Route>

      {/* Admin routes — opened by RBAC permissions (AdminRoute), not by role */}
      <Route element={<AdminRoute />}>
        <Route path="/admin" element={<AdminLayoutRoute />}>
          <Route index lazy={page(() => import("./pages/admin/AccountsPage"))} />
          <Route path="accounts" lazy={page(() => import("./pages/admin/AccountsPage"))} />
          <Route
            path="accounts/:ownerType/:ownerId/transactions"
            lazy={page(() => import("./pages/admin/TransactionsPage"))}
          />
          <Route path="global-config" lazy={page(() => import("./pages/admin/GlobalConfigPage"))} />
          <Route path="packages" lazy={page(() => import("./pages/admin/PackagesPage"))} />
          <Route path="master-data" lazy={page(() => import("./pages/admin/MasterDataPage"))} />
          <Route path="healer-registration" lazy={page(() => import("./pages/admin/HealerRegistrationPage"))} />
          <Route path="legal-documents" lazy={page(() => import("./pages/admin/LegalDocumentsPage"))} />
          <Route path="roles" lazy={page(() => import("./pages/admin/RolesPage"))} />
          <Route path="roles/new" lazy={page(() => import("./pages/admin/RoleFormPage"))} />
          <Route path="roles/:id/edit" lazy={page(() => import("./pages/admin/RoleFormPage"))} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </>
  )
);

export { router };

export default function App() {
  return (
    <AuthProvider>
      <PatientProvider>
        <UiVersionProvider>
          <RouterProvider router={router} />
          <Toaster position="top-right" richColors duration={3000} closeButton />
        </UiVersionProvider>
      </PatientProvider>
    </AuthProvider>
  );
}
