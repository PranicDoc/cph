# PranicDoc UI conventions

This is a **partial first sync**: only `Table`, `Button`, `Dialog`, and `AlertDialog` (and their sub-parts) are shipped so far, out of a larger shadcn/radix component set in the app. Don't assume components beyond what's in this project's manifest — more will land on future re-syncs.

## Setup

No provider/wrapper is required. Every component is a plain function reading only props and CSS custom properties — no ThemeProvider, no context, no i18n setup. Just import and render.

`asChild` (Button, DialogTrigger, AlertDialogTrigger, DialogClose) merges the component's classes/behavior onto a single child element via Radix `Slot` instead of rendering its own wrapper tag — use it when the trigger needs to itself be a `<Button>` or link, not a plain element.

## Styling idiom

Tailwind utility classes generated from this DS's own CSS custom properties (defined in `styles.css`, from a Tailwind v4 `@theme` block) — always use these names, never invent new ones or hardcode hex colors:

| Purpose | Classes |
|---|---|
| Primary action | `bg-primary text-primary-foreground` (hover: `hover:bg-primary-dark`) |
| Secondary / neutral surface | `bg-secondary text-secondary-foreground` |
| Muted text/background | `bg-muted text-muted-foreground` |
| Destructive action | `bg-destructive text-white` (soft: `bg-destructive/10 text-destructive`) |
| Card surface | `bg-card text-card-foreground` |
| Borders / rings | `border-border`, `ring-ring/50`, `focus-visible:ring-3` |
| Corner radius | `rounded-lg` / `rounded-[min(var(--radius-md),8px)]` (base `--radius: 0.5rem`) |
| Chakra accents (domain-specific) | `--color-chakra-crown`, `-ajna`, `-throat`, `-heart`, `-solar`, `-navel`, `-basic` (use via `style={{ color: 'var(--color-chakra-heart)' }}`, no utility class shipped for these) |

Compose classes with `cn(...)` (clsx + tailwind-merge) when combining a component's own variant classes with caller-supplied `className` — every component here accepts `className` for exactly that.

## Where the truth lives

Read `styles.css` (imports the compiled `_ds_bundle.css`, which carries every component's real classes and the `@theme` tokens above) before styling anything. Per-component `.prompt.md` files carry the same info as this file's family table plus that component's specific prop contract from its `.d.ts`.

## Example

```tsx
<Dialog>
  <DialogTrigger asChild>
    <Button variant="outline">Edit Case Notes</Button>
  </DialogTrigger>
  <DialogContent>
    <DialogHeader>
      <DialogTitle>Edit Case Notes</DialogTitle>
      <DialogDescription>Update the healer's notes for this session.</DialogDescription>
    </DialogHeader>
    <DialogFooter>
      <DialogClose asChild>
        <Button variant="outline">Cancel</Button>
      </DialogClose>
      <Button>Save Changes</Button>
    </DialogFooter>
  </DialogContent>
</Dialog>
```

Button variants: `default | outline | secondary | ghost | destructive | destructive-soft | link`. Sizes: `default | xs | sm | lg | icon | icon-xs | icon-sm | icon-lg`.
