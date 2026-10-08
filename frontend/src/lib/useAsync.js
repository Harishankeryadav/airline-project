import { useCallback, useEffect, useRef, useState } from "react";

/** Runs an async loader on mount (and whenever `deps` change). Late replies from superseded calls are ignored. */
export function useAsync(loader, deps = []) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const latest = useRef(0);

  const reload = useCallback(async () => {
    const id = ++latest.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const data = await loaderRef.current();
      if (id === latest.current) setState({ data, error: null, loading: false });
    } catch (e) {
      if (id === latest.current) setState({ data: null, error: e.message, loading: false });
    }
  }, []);

  useEffect(() => {
    reload();
    return () => {
      latest.current++;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { ...state, reload, setData: (data) => setState((s) => ({ ...s, data })) };
}
