import {
  AlertDialog,
  AlertDialogTrigger,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogAction,
  AlertDialogCancel,
} from '../scope-src/alert-dialog';
import { Button } from '../scope-src/button';

export function Default() {
  return (
    <AlertDialog open>
      <AlertDialogTrigger asChild>
        <Button variant="destructive">Delete Case</Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete this case?</AlertDialogTitle>
          <AlertDialogDescription>
            This will permanently remove the patient&apos;s case record, including all session
            history and chakra scans. This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction>Delete Case</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
