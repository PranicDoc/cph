import { useState, useEffect, useRef } from 'react';
import { DayPicker } from 'react-day-picker';
import { Calendar as CalendarIcon } from 'lucide-react';
import { cn } from '@/lib/utils';

interface CustomDOBPickerProps {
  value: string;
  onChange: (value: string) => void;
  onBlur: () => void;
  className?: string;
  disabled: boolean;
}

function parseDateValue(value: string): Date | undefined {
  if (!value) return undefined;
  const [y, m, d] = value.split('-').map(Number);
  if (!y || !m || !d) return undefined;
  return new Date(y, m - 1, d);
}

function formatDateValue(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

// Display/typing format is DD/MM/YYYY.
function dateToDisplay(date: Date): string {
  const d = String(date.getDate()).padStart(2, '0');
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const y = date.getFullYear();
  return `${d}/${m}/${y}`;
}

function isRealCalendarDate(day: number, month: number, year: number): boolean {
  if (month < 1 || month > 12) return false;
  const date = new Date(year, month - 1, day);
  return (
    date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day
  );
}

// Auto-inserts '/' as digits are typed: 01022000 -> 01/02/2000
function autoFormat(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 8);
  return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)]
    .filter(Boolean)
    .join('/');
}

export function CustomDOBPicker({ value, onChange, onBlur, className, disabled }: CustomDOBPickerProps) {
  const [showPicker, setShowPicker] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const selected = parseDateValue(value);

  const [text, setText] = useState(() => (selected ? dateToDisplay(selected) : ''));

  // Keep the typed text in sync when the value changes from outside typing
  // (calendar selection, form reset, editing an existing patient).
  useEffect(() => {
    const parsed = parseDateValue(value);
    setText(parsed ? dateToDisplay(parsed) : '');
  }, [value]);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setShowPicker(false);
        onBlur();
      }
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setShowPicker(false);
        onBlur();
      }
    }
    if (showPicker) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleEscape);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [showPicker, onBlur]);

  const today = new Date();
  // Patients can be up to 120 years old; the dropdown caption lets you jump
  // straight to a year instead of paging month-by-month.
  const oldestDob = new Date(today.getFullYear() - 120, today.getMonth(), today.getDate());

  const handleSelect = (date: Date | undefined) => {
    if (!date) return;
    onChange(formatDateValue(date));
    setShowPicker(false);
    onBlur();
  };

  const handleTextChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const formatted = autoFormat(e.target.value);
    setText(formatted);

    const [dd, mm, yyyy] = formatted.split('/');
    if (dd?.length === 2 && mm?.length === 2 && yyyy?.length === 4) {
      const day = Number(dd);
      const month = Number(mm);
      const year = Number(yyyy);
      const candidate = new Date(year, month - 1, day);
      if (
        isRealCalendarDate(day, month, year) &&
        candidate <= today &&
        candidate >= oldestDob
      ) {
        onChange(formatDateValue(candidate));
        return;
      }
    }
    // Incomplete or out-of-range — don't commit a half-typed/invalid date.
    if (value) onChange('');
  };

  return (
    <div className={cn('relative', className)} ref={containerRef}>
      <div
        className={cn(
          'flex items-center gap-2 rounded-lg border-2 border-border bg-white pr-2.5 text-sm transition-colors focus-within:border-primary',
          disabled && 'pointer-events-none cursor-not-allowed opacity-50'
        )}
      >
        <input
          type="text"
          inputMode="numeric"
          autoComplete="off"
          value={text}
          disabled={disabled}
          placeholder="DD/MM/YYYY"
          onChange={handleTextChange}
          onBlur={onBlur}
          className="w-full min-w-0 bg-transparent p-2.5 outline-none placeholder:text-muted-foreground"
        />
        <button
          type="button"
          disabled={disabled}
          aria-label="Open calendar"
          onClick={() => setShowPicker((prev) => !prev)}
          className="shrink-0 text-text-light transition-colors hover:text-primary"
        >
          <CalendarIcon className="w-4 h-4" />
        </button>
      </div>

      {showPicker && (
        <div className="absolute top-full left-0 mt-2 z-50 bg-card rounded-2xl shadow-xl border border-border p-4 min-w-max text-sm">
          <DayPicker
            mode="single"
            captionLayout="dropdown"
            selected={selected}
            onSelect={handleSelect}
            defaultMonth={selected ?? today}
            startMonth={oldestDob}
            endMonth={today}
            disabled={{ after: today }}
          />
        </div>
      )}
    </div>
  );
}
