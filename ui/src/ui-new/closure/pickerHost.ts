import { createContext } from "react";

/** Where the colour picker opens on desktop: the chakra panel (the drawer sits inside it). */
export const PickerHost = createContext<HTMLElement | null>(null);
