import { useState, useEffect } from 'react';
import { cn } from '@/lib/utils';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

interface CustomDOBPickerProps {
  value: string;
  onChange: (value: string) => void;
  onBlur: () => void;
  className?: string;
  disabled: boolean;
}

export function CustomDOBPicker({ value, onChange, onBlur, className, disabled }: CustomDOBPickerProps) {
  const [year, setYear] = useState('');
  const [month, setMonth] = useState('');
  const [day, setDay] = useState('');

  useEffect(() => {
    if (!value) {
      setYear('');
      setMonth('');
      setDay('');
    } else {
      const [y, m, d] = value.split('-');
      setYear(y || '');
      setMonth(m || '');
      setDay(d || '');
    }
  }, [value]);

  const updateParent = (y: string, m: string, d: string) => {
    if (y && m && d) {
      const maxDays = new Date(parseInt(y), parseInt(m), 0).getDate();
      const validDay = Math.min(parseInt(d), maxDays).toString().padStart(2, '0');
      onChange(`${y}-${m}-${validDay}`);
    } else {
      onChange('');
    }
  };

  const handleYearChange = (y: string) => {
    setYear(y);
    updateParent(y, month, day);
  };

  const handleMonthChange = (m: string) => {
    setMonth(m);
    updateParent(year, m, day);
  };

  const handleDayChange = (d: string) => {
    setDay(d);
    updateParent(year, month, d);
  };

  const currentYear = new Date().getFullYear();
  // Generate a list of years (e.g., from current year back to 120 years ago)
  const years = Array.from({ length: 120 }, (_, i) => currentYear - i);
  const months = [
    { v: '01', l: 'Jan' }, { v: '02', l: 'Feb' }, { v: '03', l: 'Mar' },
    { v: '04', l: 'Apr' }, { v: '05', l: 'May' }, { v: '06', l: 'Jun' },
    { v: '07', l: 'Jul' }, { v: '08', l: 'Aug' }, { v: '09', l: 'Sep' },
    { v: '10', l: 'Oct' }, { v: '11', l: 'Nov' }, { v: '12', l: 'Dec' }
  ];

  const maxDaysInMonth = month && year ? new Date(parseInt(year), parseInt(month), 0).getDate() : 31;
  const days = Array.from({ length: maxDaysInMonth }, (_, i) => (i + 1).toString().padStart(2, '0'));

  return (
    <div className={cn('flex gap-2', className)}>
      <Select value={month} onValueChange={handleMonthChange} disabled={disabled}>
        <SelectTrigger className="flex-1" onBlur={onBlur}>
          <SelectValue placeholder="Month" />
        </SelectTrigger>
        <SelectContent>
          {months.map((m) => (
            <SelectItem key={m.v} value={m.v}>
              {m.l}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select value={day} onValueChange={handleDayChange} disabled={disabled}>
        <SelectTrigger className="flex-1" onBlur={onBlur}>
          <SelectValue placeholder="Day" />
        </SelectTrigger>
        <SelectContent>
          {days.map((d) => (
            <SelectItem key={d} value={d}>
              {d}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select value={year} onValueChange={handleYearChange} disabled={disabled}>
        <SelectTrigger className="flex-1" onBlur={onBlur}>
          <SelectValue placeholder="Year" />
        </SelectTrigger>
        <SelectContent>
          {years.map((y) => (
            <SelectItem key={y} value={String(y)}>
              {y}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
