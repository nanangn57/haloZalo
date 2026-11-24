# Agent notes — haloZalo

Read this before changing client UI or opening a PR. CI will fail the PR if these are skipped.

## Always run before push / PR (web)

From repo root:

```bash
cd clients/web/ui && npm ci && npm run lint && npm run build
```

The GitHub job **Web lint and build** (`.github/workflows/ci.yml`) runs exactly that. A green local lint/build avoids the usual PR red X.

## Recurring CI failure: `react-hooks/set-state-in-effect`

**Error shape**

```
Error: Calling setState synchronously within an effect can trigger cascading renders
react-hooks/set-state-in-effect
```

**Do not** reset derived UI state like this:

```tsx
useEffect(() => {
  setImageFailed(false) // ❌ CI fails
}, [url])
```

**Do** key failure (or other derived flags) to the value itself so a prop change clears the flag without an effect:

```tsx
const [failedUrl, setFailedUrl] = useState<string | null>(null)
const imageFailed = Boolean(url) && failedUrl === url
// onError → setFailedUrl(url)
```

Same idea for “reset when X changes”: compare stored X to current X; do not `setState` inside `useEffect` just to clear a boolean.

## Other web lint gotchas

- `react-hooks/exhaustive-deps`: missing deps are often **warnings**, but **errors** fail the job (`eslint` exit 1). Fix errors; do not leave new errors for later.
- Prefer patterns that avoid effects for syncing props → state (see React “you might not need an effect”).

## Related modules

- Avatar fallback: `clients/web/ui/src/components/common/Avatar.tsx`
- Shared initials helper: `clients/core/avatar.ts`
