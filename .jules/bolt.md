## 2024-09-26 - Pre-normalizing data for tight loops
**Learning:** Redundant string allocations inside tight loops (like iterating over sensitive keys for every parameter logged) can cause significant overhead.
**Action:** Always pre-compute and normalize static data sets outside of hot paths to avoid recalculating the same values repeatedly.
