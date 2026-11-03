import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useAuth } from "../context/AuthContext";
import { useEraseAccount } from "../hooks/useEraseAccount";
import { EraseAccountDialog } from "./EraseAccountDialog";

/** Profile: erase my own account and all my data (type-to-confirm). */
export function DeleteMyAccount({ name, email }: { name: string; email: string }) {
  const [open, setOpen] = useState(false);
  const erase = useEraseAccount();
  const { logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="bg-card border border-danger/40 rounded-xl shadow-sm p-5 space-y-3">
      <h2 className="text-sm font-semibold text-danger">Delete my account</h2>
      <p className="text-sm text-text-light">
        Permanently erases your account, your patients, their cases and PDFs, and your credits. This cannot be
        undone. To pause instead, ask an administrator to deactivate your account.
      </p>
      <Button variant="destructive" onClick={() => setOpen(true)}>
        Delete my account…
      </Button>
      <EraseAccountDialog
        open={open}
        onClose={() => setOpen(false)}
        name={name || "your"}
        email={email}
        warning="Your patients, their cases and PDFs, and your credit balance are erased for good."
        onErase={async (confirmation) => {
          await erase.mutateAsync({ confirmation });
          toast.success("Your account has been erased");
          logout();
          navigate("/login");
        }}
      />
    </div>
  );
}
