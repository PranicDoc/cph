/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { BrowserRouter, Routes, Route } from "react-router-dom";
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

export default function App() {
  return (
    <AuthProvider>
      <PatientProvider>
        <BrowserRouter>
          <Routes>
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
              </Route>
            </Route>

            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
        <Toaster position="top-right" richColors duration={3000} closeButton />
      </PatientProvider>
    </AuthProvider>
  );
}
