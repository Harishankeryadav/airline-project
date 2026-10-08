import { useCallback, useState } from "react";

/**
 * Wraps an async action for a form or button: tracks loading, the error message and a success message.
 *   const save = useAction(async (values) => { ...await api...; return "Saved." });
 *   <form onSubmit={(e) => { e.preventDefault(); save.run(values); }}>
 * The action may return a string, which becomes the success message.
 */
export function useAction(action) {
  const [state, setState] = useState({ loading: false, error: null, success: null });

  const run = useCallback(
    async (...args) => {
      setState({ loading: true, error: null, success: null });
      try {
        const result = await action(...args);
        setState({ loading: false, error: null, success: typeof result === "string" ? result : null });
        return true;
      } catch (e) {
        setState({ loading: false, error: e.message, success: null });
        return false;
      }
    },
    [action]
  );

  return { ...state, run };
}
