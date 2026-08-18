import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
  TableCaption,
  TableFooter,
} from '../scope-src/table';

export function Default() {
  return (
    <Table>
      <TableCaption>Recent healing sessions</TableCaption>
      <TableHeader>
        <TableRow>
          <TableHead>Patient</TableHead>
          <TableHead>Session Date</TableHead>
          <TableHead>Healer</TableHead>
          <TableHead className="text-right">Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow>
          <TableCell className="font-medium">Amara Rao</TableCell>
          <TableCell>2026-08-14</TableCell>
          <TableCell>Dr. Meera Nair</TableCell>
          <TableCell className="text-right">Completed</TableCell>
        </TableRow>
        <TableRow>
          <TableCell className="font-medium">Vikram Shah</TableCell>
          <TableCell>2026-08-16</TableCell>
          <TableCell>Dr. Meera Nair</TableCell>
          <TableCell className="text-right">Scheduled</TableCell>
        </TableRow>
        <TableRow>
          <TableCell className="font-medium">Leela Menon</TableCell>
          <TableCell>2026-08-17</TableCell>
          <TableCell>Dr. Arjun Iyer</TableCell>
          <TableCell className="text-right">In Progress</TableCell>
        </TableRow>
      </TableBody>
      <TableFooter>
        <TableRow>
          <TableCell colSpan={3}>Total sessions</TableCell>
          <TableCell className="text-right">3</TableCell>
        </TableRow>
      </TableFooter>
    </Table>
  );
}

export function Minimal() {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Chakra</TableHead>
          <TableHead>Energy Level</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow>
          <TableCell className="font-medium">Crown</TableCell>
          <TableCell>Balanced</TableCell>
        </TableRow>
        <TableRow>
          <TableCell className="font-medium">Heart</TableCell>
          <TableCell>Depleted</TableCell>
        </TableRow>
      </TableBody>
    </Table>
  );
}
