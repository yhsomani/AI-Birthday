1. **Optimize `EventsList` Recomposition**
   - In `app/src/main/java/com/example/ui/screens/events/EventsScreen.kt`, the `EventsList` composable currently recomputes the grouping of events on every recomposition. It also instantiates a new `Calendar` object for every single event in the list during the `groupBy` operation.
   - I will wrap the `groupBy` logic in a `remember(events)` block to memoize the calculation.
   - I will also extract the `Calendar.getInstance()` call outside the `groupBy` lambda to reuse a single instance, drastically reducing object allocations.
   - I will use `items` extension function instead of `forEach` calling `item` for better `LazyColumn` performance.
2. **Pre commit steps**
   - Ensure proper testing, verification, review, and reflection are done by calling `pre_commit_instructions`.
3. **Submit the changes**
   - Commit and submit the code with a descriptive PR "⚡ Bolt: Memoize event grouping and reduce Calendar allocations".
