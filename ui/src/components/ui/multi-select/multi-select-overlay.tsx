import { ReactNode } from "react";

import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
  DrawerTrigger,
} from "@/components/ui/drawer";

import { useIsMobile } from "@/hooks/use-mobile";

interface MultiSelectOverlayProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;

  trigger: ReactNode;

  children: ReactNode;

  title?: string;
}

export function MultiSelectOverlay({
  open,
  onOpenChange,
  trigger,
  children,
  title = "Select",
}: MultiSelectOverlayProps) {
  const isMobile = useIsMobile();

  if (isMobile) {
    return (
      <Drawer open={open} onOpenChange={onOpenChange}>
        <DrawerTrigger asChild>{trigger}</DrawerTrigger>

        <DrawerContent className="max-h-[80vh]">
          <DrawerHeader>
            <DrawerTitle>{title}</DrawerTitle>
          </DrawerHeader>

          <div className="overflow-auto px-4 pb-4">{children}</div>
        </DrawerContent>
      </Drawer>
    );
  }

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>{trigger}</PopoverTrigger>

      <PopoverContent
        align="start"
        className="
          w-[var(--radix-popover-trigger-width)]
          min-w-[280px]
          max-w-[380px]
          p-0
        "
      >
        {children}
      </PopoverContent>
    </Popover>
  );
}
