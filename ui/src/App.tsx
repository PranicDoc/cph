/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { BrowserRouter, Routes, Route } from "react-router-dom";
import Layout from "./components/Layout";
import PublicShell from "./components/PublicShell";
import Patients from "./pages/Patients";
import Registration from "./pages/Registration";
import Session from "./pages/Session";
import PatientHealthForm from "./pages/PatientHealthForm";
import Login from "./pages/Login";
import Register from "./pages/Register";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";
import { PatientProvider } from "./context/PatientContext";
import { PdfList } from "./pages/PdfList";
import Unauthorized from "./pages/Unauthorized";
import PatientFeedback from "./pages/Feedback";

export default function App() {
  return (
    <AuthProvider>
      <PatientProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/unauthorized" element={<Unauthorized />} />

            {/* Public pages — minimal branded shell, no nav */}
            <Route element={<PublicShell />}>
              <Route path="/self-register" element={<Registration />} />
              <Route
                path="/self-register/:clinicId/:uuid"
                element={<Registration />}
              />
              <Route path="/feedback" element={<PatientFeedback />} />
              <Route path="/patient-health/:caseId" element={<PatientHealthForm />} />
            </Route>

            {/* Protected healer routes */}
            <Route element={<ProtectedRoute />}>
              <Route path="/" element={<Layout />}>
                <Route index element={<Patients />} />
                <Route path="registration" element={<Registration />} />
                <Route path="pdf-list" element={<PdfList />} />
                <Route path="session/:healingCaseId" element={<Session />} />
              </Route>
            </Route>
          </Routes>
        </BrowserRouter>
      </PatientProvider>
    </AuthProvider>
  );
}
