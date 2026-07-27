## 2026-07-27 - [Clear Search Field]
**Learning:** Add clear button to SearchFields for better UX. Users should be able to clear their search easily.
**Action:** Enhance the `SearchField` component in `src/design-system/components/Primitives.tsx` to include a trailing clear `IconButton` when the input string is not empty. Also, add the 'clear' SVG icon to `src/design-system/components/Icon.tsx` and the `common.clear` localization string.
## 2026-07-27 - [Clear Search Field]
**Learning:** Add a "clear" button to `SearchField` components to improve UX and allow users to quickly reset their search. When doing so, use safe truthiness checks (e.g., `!!value`) instead of relying on property access (`value.length > 0`) to avoid potential crashes with undefined values.
**Action:** Enhance the generic `SearchField` in `src/design-system/components/Primitives.tsx` to include an accessible clear button when a search string is active.
