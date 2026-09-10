import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
  DialogClose,
} from '../scope-src/dialog';
import { Button } from '../scope-src/button';

export function Default() {
  return (
    <Dialog open>
      <DialogTrigger asChild>
        <Button variant="outline">Edit Case Notes</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit Case Notes</DialogTitle>
          <DialogDescription>
            Update the healer&apos;s notes for this patient&apos;s session. Changes save automatically.
          </DialogDescription>
        </DialogHeader>
        <div style={{ padding: '8px 0' }}>
          <textarea
            style={{
              width: '100%',
              minHeight: 96,
              borderRadius: 8,
              border: '1px solid var(--color-border)',
              padding: 8,
              fontFamily: 'inherit',
              fontSize: 14,
            }}
            defaultValue="Patient reported reduced tension in the solar plexus chakra after the session. Recommend follow-up in 2 weeks."
          />
        </div>
        <DialogFooter>
          <DialogClose asChild>
            <Button variant="outline">Cancel</Button>
          </DialogClose>
          <Button>Save Changes</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
