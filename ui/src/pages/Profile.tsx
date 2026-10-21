import { useState } from "react";
import {
  User, Mail, Phone, Activity,
  CreditCard, Pencil, CheckCircle2, X, Lock,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { useUiVersion } from "../ui-version";
import { PlanPicker } from "../components/PlanPicker";
import { Skeleton } from "../components/ui/skeleton";
import {
  useHealerProfile, useUpdateProfile, useChangePassword,
  useLinkGoogle, useUnlinkGoogle,
} from "../hooks/useHealerProfile";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";

const GOOGLE_G_ICON = "https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg";

function getInitials(name: string): string {
  return name.split(" ").map((p) => p[0]).join("").toUpperCase().slice(0, 2);
}

const inputCls = "border border-primary px-3 py-2";
const readCls =
  "bg-background rounded-lg px-3 py-2 text-sm text-text";

function ReadField({
  icon: Icon, label, value, mono = false,
}: { icon: React.ElementType; label: string; value?: string | null; mono?: boolean }) {
  return (
    <div className="flex items-start gap-3">
      <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
        <Icon className="w-4 h-4 text-primary" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium text-text-light uppercase tracking-wide mb-1">{label}</p>
        <p className={`${readCls} ${mono ? "font-mono" : ""} truncate`}>{value || "—"}</p>
      </div>
    </div>
  );
}

export default function Profile() {
  const { isNewView } = useUiVersion();
  const { user } = useAuth();
  const { data: profile, isLoading } = useHealerProfile();
  const updateMutation = useUpdateProfile();
  const changePwMutation = useChangePassword();
  const linkGoogleMutation = useLinkGoogle();
  const unlinkGoogleMutation = useUnlinkGoogle();

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ fullName: "", email: "", phone: "" });
  const [showUpgrade, setShowUpgrade] = useState(false);
  const [pwEditing, setPwEditing] = useState(false);
  const [pwDraft, setPwDraft] = useState({ current: "", newPwd: "", confirm: "" });
  const [confirmingUnlink, setConfirmingUnlink] = useState(false);

  const displayName = profile?.fullName ?? user?.fullName ?? "";
  const displayEmail = profile?.email ?? user?.email ?? "";
  const displayPhone = profile?.phone ?? user?.phone ?? "";
  const displayLevel = profile?.healingLevel;

  const enterEdit = () => {
    setDraft({ fullName: displayName, email: displayEmail, phone: displayPhone });
    setEditing(true);
  };

  const cancelEdit = () => setEditing(false);

  const saveEdit = () => {
    updateMutation.mutate(
      { fullName: draft.fullName, email: draft.email, phone: draft.phone || undefined },
      { onSuccess: () => setEditing(false) }
    );
  };

  const cancelPwEdit = () => {
    setPwEditing(false);
    setPwDraft({ current: "", newPwd: "", confirm: "" });
  };

  const savePw = () => {
    if (pwDraft.newPwd.length < 8) {
      toast.error("Password must be at least 8 characters", { duration: 5000 });
      return;
    }
    if (pwDraft.newPwd !== pwDraft.confirm) {
      toast.error("Passwords do not match", { duration: 5000 });
      return;
    }
    changePwMutation.mutate(
      {
        currentPassword: profile?.hasPassword !== false ? (pwDraft.current || undefined) : undefined,
        newPassword: pwDraft.newPwd,
        confirmPassword: pwDraft.confirm,
      },
      {
        onSuccess: () => {
          toast.success("Password updated");
          setPwEditing(false);
          setPwDraft({ current: "", newPwd: "", confirm: "" });
        },
      }
    );
  };

  const handleLinkGoogle = () => {
    if (!window.google) {
      toast.error("Couldn't link your Google account. Please try again.", { duration: 5000 });
      return;
    }
    window.google.accounts.id.initialize({
      client_id: process.env.GOOGLE_CLIENT_ID as string,
      callback: (response) => {
        if (response?.credential) {
          linkGoogleMutation.mutate({ idToken: response.credential });
        }
        // Popup dismissed by the user without choosing an account: GIS never
        // invokes this callback in that case — silent no-op, no toast.
      },
    });
    window.google.accounts.id.prompt((notification) => {
      // DEBUG: temporary — surfaces why One Tap didn't display/was skipped
      // instead of failing completely silently. Remove once GIS is confirmed
      // working end-to-end.
      if (notification.isNotDisplayed()) {
        console.warn("GIS not displayed:", notification.getNotDisplayedReason());
        toast.error(`Google sign-in unavailable: ${notification.getNotDisplayedReason()}`, {
          duration: 8000,
        });
      } else if (notification.isSkippedMoment()) {
        console.warn("GIS skipped:", notification.getSkippedReason());
      }
    });
  };

  const handleConfirmUnlink = () => {
    unlinkGoogleMutation.mutate(undefined, {
      onSuccess: () => setConfirmingUnlink(false),
    });
  };

  if (!user) return null;

  return (
    <div className={isNewView ? "hlx hl-cols" : "max-w-2xl mx-auto space-y-5"}>

      {/* ── Hero banner ── */}
      <div className="bg-gradient-to-r from-primary-dark to-primary rounded-2xl p-6 flex items-center gap-5">
        {user.avatarUrl ? (
          <img
            src={user.avatarUrl}
            alt={displayName}
            className="w-20 h-20 rounded-full object-cover ring-2 ring-white/40 shrink-0"
          />
        ) : (
          <span className="w-20 h-20 flex items-center justify-center rounded-full bg-white/20 text-white text-2xl font-bold ring-2 ring-white/40 shrink-0">
            {getInitials(displayName || "?")}
          </span>
        )}
        <div className="min-w-0">
          {isLoading ? (
            <>
              <Skeleton className="h-7 w-40 bg-white/20 mb-2" />
              <Skeleton className="h-4 w-52 bg-white/20" />
            </>
          ) : (
            <>
              <p className="text-2xl font-bold text-white truncate">{displayName}</p>
              <p className="text-sm text-white/70 truncate mt-0.5">{displayEmail}</p>
            </>
          )}
        </div>
      </div>

      {/* ── Personal Information ── */}
      <div className="bg-card border border-border rounded-xl shadow-sm p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-primary-dark">Personal Information</h2>
          {!editing ? (
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={enterEdit}
              className="h-auto w-auto p-1.5 hover:bg-background text-text-light hover:text-primary"
              title="Edit"
            >
              <Pencil className="w-4 h-4" />
            </Button>
          ) : (
            <div className="flex items-center gap-1">
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={saveEdit}
                disabled={updateMutation.isPending}
                className="h-auto w-auto p-1.5 hover:bg-success/10 text-success hover:text-success"
                title="Save"
              >
                <CheckCircle2 className="w-4 h-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={cancelEdit}
                disabled={updateMutation.isPending}
                className="h-auto w-auto p-1.5 hover:bg-red-50 text-red-400 hover:text-red-400"
                title="Cancel"
              >
                <X className="w-4 h-4" />
              </Button>
            </div>
          )}
        </div>

        {isLoading ? (
          <div className="space-y-4">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="flex items-start gap-3">
                <Skeleton className="w-8 h-8 rounded-lg shrink-0" />
                <div className="flex-1 space-y-1.5">
                  <Skeleton className="h-3 w-16" />
                  <Skeleton className="h-9 w-full rounded-lg" />
                </div>
              </div>
            ))}
          </div>
        ) : editing ? (
          <div className="space-y-4">
            <div className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-6">
                <User className="w-4 h-4 text-primary" />
              </div>
              <div className="flex-1">
                <Label className="block text-xs font-medium text-text-light uppercase tracking-wide mb-1">Full Name</Label>
                <Input
                  type="text"
                  className={inputCls}
                  value={draft.fullName}
                  onChange={(e) => setDraft((d) => ({ ...d, fullName: e.target.value }))}
                  required
                />
              </div>
            </div>
            <div className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-6">
                <Mail className="w-4 h-4 text-primary" />
              </div>
              <div className="flex-1">
                <Label className="block text-xs font-medium text-text-light uppercase tracking-wide mb-1">Email</Label>
                <Input
                  type="email"
                  className={inputCls}
                  value={draft.email}
                  onChange={(e) => setDraft((d) => ({ ...d, email: e.target.value }))}
                  required
                />
              </div>
            </div>
            <div className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-6">
                <Phone className="w-4 h-4 text-primary" />
              </div>
              <div className="flex-1">
                <Label className="block text-xs font-medium text-text-light uppercase tracking-wide mb-1">Phone</Label>
                <Input
                  type="tel"
                  className={inputCls}
                  value={draft.phone}
                  placeholder="Optional"
                  onChange={(e) => setDraft((d) => ({ ...d, phone: e.target.value }))}
                />
              </div>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <ReadField icon={User} label="Full Name" value={displayName} />
            <ReadField icon={Mail} label="Email" value={displayEmail} />
            <ReadField icon={Phone} label="Phone" value={displayPhone} />
          </div>
        )}
      </div>

      {/* ── Password / Security ── */}
      <div className="bg-card border border-border rounded-xl shadow-sm p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-primary-dark">
            {profile?.hasPassword !== false ? "Change Password" : "Set Password"}
          </h2>
          {!pwEditing ? (
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => setPwEditing(true)}
              className="h-auto w-auto p-1.5 hover:bg-background text-text-light hover:text-primary"
              title="Edit"
              aria-label="Edit"
            >
              <Pencil className="w-4 h-4" />
            </Button>
          ) : (
            <div className="flex items-center gap-1">
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={savePw}
                disabled={changePwMutation.isPending}
                className="h-auto w-auto p-1.5 hover:bg-success/10 text-success hover:text-success"
                title={profile?.hasPassword !== false ? "Change Password" : "Set Password"}
                aria-label={profile?.hasPassword !== false ? "Change Password" : "Set Password"}
              >
                <CheckCircle2 className="w-4 h-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={cancelPwEdit}
                disabled={changePwMutation.isPending}
                className="h-auto w-auto p-1.5 hover:bg-red-50 text-red-400 hover:text-red-400"
                title="Discard"
                aria-label="Discard"
              >
                <X className="w-4 h-4" />
              </Button>
            </div>
          )}
        </div>

        {isLoading ? (
          <div className="flex items-start gap-3">
            <Skeleton className="w-8 h-8 rounded-lg shrink-0" />
            <div className="flex-1 space-y-1.5">
              <Skeleton className="h-3 w-16" />
              <Skeleton className="h-9 w-full rounded-lg" />
            </div>
          </div>
        ) : pwEditing ? (
          <div className="space-y-4">
            {profile?.hasPassword !== false && (
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-6">
                  <Lock className="w-4 h-4 text-primary" />
                </div>
                <div className="flex-1">
                  <Label className="block text-xs font-medium text-text-light uppercase tracking-wide mb-1">
                    Current Password
                  </Label>
                  <Input
                    type="password"
                    className={inputCls}
                    autoComplete="current-password"
                    value={pwDraft.current}
                    onChange={(e) => setPwDraft((d) => ({ ...d, current: e.target.value }))}
                  />
                </div>
              </div>
            )}
            <div className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-6">
                <Lock className="w-4 h-4 text-primary" />
              </div>
              <div className="flex-1">
                <Label className="block text-xs font-medium text-text-light uppercase tracking-wide mb-1">
                  New Password
                </Label>
                <Input
                  type="password"
                  className={inputCls}
                  autoComplete="new-password"
                  value={pwDraft.newPwd}
                  onChange={(e) => setPwDraft((d) => ({ ...d, newPwd: e.target.value }))}
                />
              </div>
            </div>
            <div className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-6">
                <Lock className="w-4 h-4 text-primary" />
              </div>
              <div className="flex-1">
                <Label className="block text-xs font-medium text-text-light uppercase tracking-wide mb-1">
                  Confirm Password
                </Label>
                <Input
                  type="password"
                  className={inputCls}
                  autoComplete="new-password"
                  value={pwDraft.confirm}
                  onChange={(e) => setPwDraft((d) => ({ ...d, confirm: e.target.value }))}
                />
              </div>
            </div>
          </div>
        ) : (
          <div className="flex items-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
              <Lock className="w-4 h-4 text-primary" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-text-light uppercase tracking-wide mb-1">Password</p>
              <p className={`${readCls} truncate`}>
                {profile?.hasPassword !== false ? "••••••••" : "No password set"}
              </p>
            </div>
          </div>
        )}
      </div>

      {/* ── Google Account ── */}
      <div className="bg-card border border-border rounded-xl shadow-sm p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-primary-dark">Google Account</h2>
        </div>

        {isLoading ? (
          <div className="flex items-start gap-3">
            <Skeleton className="w-8 h-8 rounded-lg shrink-0" />
            <div className="flex-1 space-y-1.5">
              <Skeleton className="h-3 w-16" />
              <Skeleton className="h-9 w-full rounded-lg" />
            </div>
          </div>
        ) : profile?.hasGoogleLinked ? (
          <div className="flex items-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
              <img src={GOOGLE_G_ICON} alt="Google" className="w-4 h-4" />
            </div>
            <div className="flex-1 min-w-0 space-y-2">
              <p className="flex items-center gap-1.5 text-sm text-success truncate">
                <CheckCircle2 className="w-4 h-4 shrink-0" />
                Connected as {profile.email}
              </p>
              {profile?.hasPassword !== false ? (
                <Button
                  type="button"
                  variant="link"
                  onClick={() => setConfirmingUnlink(true)}
                  className="h-auto p-0 text-xs font-medium text-danger hover:text-danger"
                >
                  Unlink Google Account
                </Button>
              ) : (
                <div>
                  <Button
                    type="button"
                    variant="link"
                    disabled
                    className="h-auto p-0 text-xs font-medium text-danger cursor-not-allowed"
                  >
                    Unlink Google Account
                  </Button>
                  <p className="text-xs text-danger mt-1">Set a password first</p>
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="flex items-start gap-3">
            <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
              <img src={GOOGLE_G_ICON} alt="Google" className="w-4 h-4" />
            </div>
            <div className="flex-1 min-w-0 space-y-2">
              <p className="bg-background rounded-lg px-3 py-2 text-sm text-text-light truncate">Not linked</p>
              <p className="text-xs text-text-light">Link your Google account for faster sign-in.</p>
              <Button
                type="button"
                variant="outline"
                onClick={handleLinkGoogle}
                disabled={linkGoogleMutation.isPending}
                className="gap-2 py-2 px-3 h-auto bg-white hover:bg-gray-50"
              >
                {linkGoogleMutation.isPending ? (
                  <Spinner size="sm" className="text-gray-700" />
                ) : (
                  <img src={GOOGLE_G_ICON} alt="Google" className="w-4 h-4" />
                )}
                <span className="text-sm font-medium text-gray-700">
                  {linkGoogleMutation.isPending ? "Linking…" : "Link Google Account"}
                </span>
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* ── Account Details (read-only) ── */}
      <div className="bg-card border border-border rounded-xl shadow-sm p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-primary-dark">Account Details</h2>
          <Lock className="w-3.5 h-3.5 text-text-light" />
        </div>
        {isLoading ? (
          <div className="space-y-4">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="flex items-start gap-3">
                <Skeleton className="w-8 h-8 rounded-lg shrink-0" />
                <div className="flex-1 space-y-1.5">
                  <Skeleton className="h-3 w-16" />
                  <Skeleton className="h-9 w-full rounded-lg" />
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="space-y-4">
            {displayLevel && <ReadField icon={Activity} label="Healing Level" value={displayLevel} />}
          </div>
        )}
      </div>

      {/* ── Subscription ── */}
      <div className="bg-card border border-border rounded-xl shadow-sm p-5">
        <h2 className="text-sm font-semibold text-primary-dark mb-1">Plan & Billing</h2>
        <p className="text-xs text-text-light mb-4">View and upgrade your subscription plan.</p>
        <Button
          onClick={() => setShowUpgrade(true)}
          className="gap-2 px-4 py-2 h-auto"
        >
          <CreditCard className="w-4 h-4" />
          Upgrade Plan
        </Button>
      </div>

      <ConfirmDialog
        open={confirmingUnlink}
        onOpenChange={setConfirmingUnlink}
        title="Unlink Google account?"
        description="You will no longer be able to sign in with Google."
        confirmLabel={unlinkGoogleMutation.isPending ? "Unlinking…" : "Confirm Unlink"}
        destructive
        loading={unlinkGoogleMutation.isPending}
        onConfirm={handleConfirmUnlink}
      />

      {/* ── Upgrade modal ── */}
      {user && (
        <Dialog open={showUpgrade} onOpenChange={setShowUpgrade}>
          <DialogContent className="w-full sm:max-w-3xl gap-0 rounded-t-2xl sm:rounded-2xl max-h-[92vh] flex flex-col p-0 max-sm:top-auto max-sm:bottom-0 max-sm:translate-y-0 max-sm:max-w-full">
            <DialogTitle className="sr-only">Upgrade Plan</DialogTitle>
            <div className="sm:hidden flex justify-center pt-3 pb-1 shrink-0">
              <div className="w-10 h-1 bg-border rounded-full" />
            </div>
            <div className="flex-1 min-h-0 overflow-y-auto p-5 sm:p-8">
              <PlanPicker
                ownerId={user.id}
                ownerType="HEALER"
                onComplete={() => setShowUpgrade(false)}
              />
            </div>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}
