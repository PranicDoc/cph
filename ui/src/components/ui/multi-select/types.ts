// types.ts

import { ReactNode } from "react";

export interface MultiSelectProps<T> {
  options: T[];

  value: T[];

  onChange: (value: T[]) => void;

  getOptionKey: (option: T) => string;

  getOptionLabel: (option: T) => string;

  renderOption?: (option: T, selected: boolean) => ReactNode;

  renderBadge?: (option: T) => ReactNode;

  title?: string;

  placeholder?: string;

  searchPlaceholder?: string;

  emptyText?: string;

  disabled?: boolean;

  className?: string;

  maxBadges?: number;

  loading?: boolean;
}

export interface MultiSelectListProps<T> {
  options: T[];

  value: T[];

  onChange: (value: T[]) => void;

  getOptionKey: (option: T) => string;

  getOptionLabel: (option: T) => string;

  renderOption?: (option: T, selected: boolean) => ReactNode;

  searchPlaceholder?: string;

  emptyText?: string;

  disabled?: boolean
}

export interface MultiSelectTriggerProps<T> {
  value: T[];

  getOptionKey: (option: T) => string;

  getOptionLabel: (option: T) => string;

  renderBadge?: (option: T) => ReactNode;

  placeholder?: string;

  maxBadges?: number;

  className?: string;

  disabled?: boolean;

  loading?: boolean;
}
