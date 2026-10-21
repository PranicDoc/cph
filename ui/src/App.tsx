/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { UiVersionProvider } from "./ui-version";
import {
  createBrowserRouter,
  createRoutesFromElements,
  RouterProvider,
  Route,
} from "react-router-dom";
import { Toaster } from "sonner";
import Layout from "./components/Layout";
import PublicShell from "./components/PublicShell";
import Patients from "./pages/Patients";
import Dashboard from "./pages/Dashboard";
import Registration from "./pages/Registration";
import Session from "./pages/Session";
import PatientHealthForm from "./pages/PatientHealthForm";
import Login from "./pages/Login";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";
import AdminRoute from "./components/admin/AdminRoute";
import AdminLayout from "./components/admin/AdminLayout";
import AccountsPage from "./pages/admin/AccountsPage";
import TransactionsPage from "./pages/admin/TransactionsPage";
import GlobalConfigPage from "./pages/admin/GlobalConfigPage";
import PackagesPage from "./pages/admin/PackagesPage";
import MasterDataPage from "./pages/admin/MasterDataPage";
import LegalDocumentsPage from "./pages/admin/LegalDocumentsPage";
import RolesPage from "./pages/admin/RolesPage";
import RoleFormPage from "./pages/admin/RoleFormPage";
import { PatientProvider } from "./context/PatientContext";
import { PdfList } from "./pages/PdfList";
import CaseSummary from "./pages/CaseSummary";
import Profile from "./pages/Profile";
import Unauthorized from "./pages/Unauthorized";
import Forbidden from "./pages/Forbidden";
import NotFound from "./pages/NotFound";
import PatientFeedback from "./pages/Feedback";
import OAuth2Success from "./components/OAuth2Success";
import HealerRegistrationPage from "./pages/admin/HealerRegistrationPage";
import AcceptInvite from "./pages/AcceptInvite";
import Signup from "./pages/Signup";
import LegalCenter from "./pages/LegalCenter";

// react-router v7 data router. The route tree below is the exact same JSX
// tree that previously lived inside the declarative router element pair — it
// is only wrapped in createRoutesFromElements + createBrowserRouter so that
// data APIs (notably useBlocker, used by the discard-unsaved-changes guard on
// the role create/edit form) are available. No loaders/actions/errorElement/
// lazy routes are introduced: this is a mechanical API swap only.
const router = createBrowserRouter(
  createRoutesFromElements(
    <>
      <Route path="/login" element={<Login />} />
      <Route path="/unauthorized" element={<Unauthorized />} />
      <Route path="/forbidden" element={<Forbidden />} />
      <Route path="/oauth2/success" element={<OAuth2Success />} />

      {/* Public pages — minimal branded shell, no nav */}
      <Route element={<PublicShell />}>
        <Route path="/self-register" element={<Registration />} />
        <Route
          path="/self-register/:healerId/:uuid"
          element={<Registration />}
        />
        <Route path="/feedback" element={<PatientFeedback />} />
        <Route path="/patient-health/:caseId" element={<PatientHealthForm />} />
        <Route path="/accept-invite" element={<AcceptInvite />} />
        <Route path="/signup" element={<Signup />} />
      </Route>

      {/* Protected healer routes */}
      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="patients" element={<Patients />} />
          <Route path="registration" element={<Registration />} />
          <Route path="pdf-list" element={<PdfList />} />
          <Route path="session/:healingCaseId" element={<Session />} />
          <Route path="cases/:healingCaseId/summary" element={<CaseSummary />} />
          <Route path="profile" element={<Profile />} />
        </Route>
        {/* Sibling of the Layout route, not nested inside it: /legal-center
            must stay behind ProtectedRoute's gate check but must not render
            Layout's nav/sidebar chrome (UI-SPEC item 1). */}
        <Route path="/legal-center" element={<LegalCenter />} />
      </Route>

      {/* Admin routes — ADMIN + SUPADM only */}
      <Route element={<AdminRoute />}>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<AccountsPage />} />
          <Route path="accounts" element={<AccountsPage />} />
          <Route
            path="accounts/:ownerType/:ownerId/transactions"
            element={<TransactionsPage />}
          />
          <Route path="global-config" element={<GlobalConfigPage />} />
          <Route path="packages" element={<PackagesPage />} />
          <Route path="master-data" element={<MasterDataPage />} />
          <Route path="healer-registration" element={<HealerRegistrationPage />} />
          <Route path="legal-documents" element={<LegalDocumentsPage />} />
          <Route path="roles" element={<RolesPage />} />
          <Route path="roles/new" element={<RoleFormPage />} />
          <Route path="roles/:id/edit" element={<RoleFormPage />} />
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
