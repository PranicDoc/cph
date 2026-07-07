// Minimal ambient typings for Google Identity Services (GIS), loaded globally
// via the <script src="https://accounts.google.com/gsi/client" async> tag in
// index.html. Only the surface used by the Profile page's Google Account
// link flow is declared here.
export {};

interface GsiPromptMomentNotification {
  isDisplayMoment(): boolean;
  isDisplayed(): boolean;
  isNotDisplayed(): boolean;
  getNotDisplayedReason(): string;
  isSkippedMoment(): boolean;
  getSkippedReason(): string;
  isDismissedMoment(): boolean;
  getDismissedReason(): string;
  getMomentType(): string;
}

declare global {
  interface Window {
    google?: {
      accounts: {
        oauth2: {
          initTokenClient(config: {
            client_id: string;
            scope: string;
            callback: (response: { access_token?: string; error?: string }) => void;
          }): { requestAccessToken(): void };
          initCodeClient(config: {
            client_id: string;
            scope: string;
            callback: (response: { code?: string; error?: string }) => void;
          }): { requestCode(): void };
        };
        id: {
          initialize(config: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }): void;
          prompt(momentListener?: (notification: GsiPromptMomentNotification) => void): void;
        };
      };
    };
  }
}
