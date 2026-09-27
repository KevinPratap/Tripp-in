# Deterministic Itinerary Engine Specification

The Deterministic Itinerary Engine (`ItineraryValidator`) enforces physical, temporal, and economic feasibility:

### Rules Enforced:
1. **Temporal Non-Overlap**:
   $$\forall i < j, \quad \text{startTime}_j \ge \text{endTime}_i$$
   No two activities in the same day may have overlapping schedules.

2. **Operating Hours Compliance**:
   Each activity interval $[\text{startTime}, \text{endTime}]$ must fall entirely within the place's official operating hours for that specific day of the week.

3. **Physical Transit Feasibility**:
   Between consecutive activities $A_i$ and $A_{i+1}$:
   $$\text{startTime}_{i+1} - \text{endTime}_i \ge \Delta t_{\text{transit}}(P_i, P_{i+1}, \text{mode})$$
   Calculated via Google Routes Distance Matrix or Haversine transit speed estimator.

4. **Pace & Fatigue Limits**:
   - **Relaxed**: $\le 5.5$ hours of scheduled activities / day.
   - **Moderate**: $\le 7.5$ hours of scheduled activities / day.
   - **Fast**: $\le 9.5$ hours of scheduled activities / day.

5. **Daily Bounds**:
   Standard activities must not start before 08:00 or end after 23:30 (unless nightlife/special events).

6. **Budget Adherence**:
   $$\sum \text{estimatedCost} \le \text{budgetTotal}$$
