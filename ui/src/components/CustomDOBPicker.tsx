import React, { useState, useEffect } from 'react';

interface CustomDOBPickerProps {
  value: string;
  onChange: (value: string) => void;
  onBlur: () => void;
  className?: string;
}

export function CustomDOBPicker({ value, onChange, onBlur, className }: CustomDOBPickerProps) {
  const [year, setYear] = useState('');
  const [month, setMonth] = useState('');
  const [day, setDay] = useState('');

  // Sync from external value (e.g. initial load or form reset)
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

  const handleYearChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const y = e.target.value;
    setYear(y);
    updateParent(y, month, day);
  };

  const handleMonthChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const m = e.target.value;
    setMonth(m);
    updateParent(year, m, day);
  };

  const handleDayChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const d = e.target.value;
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
    <div className={`flex gap-2 ${className || ''}`}>
      <select 
        value={month} 
        onChange={handleMonthChange} 
        onBlur={onBlur} 
        className="flex-1 p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white"
      >
        <option value="" disabled>Month</option>
        {months.map(m => <option key={m.v} value={m.v}>{m.l}</option>)}
      </select>
      <select 
        value={day} 
        onChange={handleDayChange} 
        onBlur={onBlur} 
        className="flex-1 p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white"
      >
        <option value="" disabled>Day</option>
        {days.map(d => <option key={d} value={d}>{d}</option>)}
      </select>
      <select 
        value={year} 
        onChange={handleYearChange} 
        onBlur={onBlur} 
        className="flex-1 p-2.5 border-2 border-border rounded-lg text-sm focus:border-primary outline-none bg-white"
      >
        <option value="" disabled>Year</option>
        {years.map(y => <option key={y} value={y}>{y}</option>)}
      </select>
    </div>
  );
}
