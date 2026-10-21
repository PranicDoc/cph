import { lazy, Suspense, type ComponentType } from "react";
import { useUiVersion } from "./UiVersionContext";

/**
 * Picks the classic or the redesigned version of a page. Both take the same props and use the
 * same routes, hooks and API calls; only the markup differs. The new page is loaded lazily, so
 * people on the classic view never download it. A page with no new version simply isn't wrapped.
 */
export function versioned<P extends object>(
  Classic: ComponentType<P>,
  loadNew: () => Promise<{ default: ComponentType<P> }>
): ComponentType<P> {
  const NewView = lazy(loadNew);
  function Versioned(props: P) {
    const { isNewView } = useUiVersion();
    if (!isNewView) return <Classic {...props} />;
    return (
      <Suspense fallback={null}>
        <NewView {...(props as P & React.JSX.IntrinsicAttributes)} />
      </Suspense>
    );
  }
  return Versioned;
}
