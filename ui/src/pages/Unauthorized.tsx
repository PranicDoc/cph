import { ShieldAlert, ArrowLeft, Mail } from "lucide-react";

export default function UnauthorizedPage() {
  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-2xl shadow-lg p-8 text-center space-y-6">
        <div className="flex flex-col items-center justify-center space-y-4">
          <div className="bg-red-100 p-4 rounded-full">
            <ShieldAlert className="h-12 w-12 text-red-600" aria-hidden="true" />
          </div>
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
            Link Expired or Already Used
          </span>
        </div>

        <div className="space-y-2">
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">
            Feedback Link Unavailable
          </h1>
          <p className="text-gray-500 text-sm">
            This feedback link has either expired or your feedback has already
            been submitted. If you believe this is an error, please contact your
            healer directly to request a new link.
          </p>
        </div>

        <div className="pt-4 flex flex-col space-y-3 sm:flex-row sm:space-y-0 sm:space-x-3 justify-center">
          <button
            onClick={() => window.history.back()}
            className="inline-flex justify-center items-center px-4 py-2 border border-gray-300 shadow-sm text-sm font-medium rounded-lg text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-colors"
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            Go Back
          </button>

          <a
            href="mailto:?subject=New%20Feedback%20Link%20Request&body=Hi%2C%20my%20feedback%20link%20has%20expired.%20Could%20you%20please%20send%20me%20a%20new%20one%3F"
            className="inline-flex justify-center items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-lg text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-colors"
          >
            <Mail className="mr-2 h-4 w-4" />
            Contact Healer
          </a>
        </div>
      </div>
    </div>
  );
}
