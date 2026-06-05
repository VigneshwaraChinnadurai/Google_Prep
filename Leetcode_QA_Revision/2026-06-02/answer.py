class Solution:
    def earliestFinishTime(self, landStartTime: List[int], landDuration: List[int], waterStartTime: List[int], waterDuration: List[int]) -> int:
        
        # --- Scenario 1: Land ride first, then water ride ---
        
        # To minimize the total time, we should finish the first ride as early as possible.
        # This makes the tourist available at the earliest moment for the second ride.
        # Find the earliest time any land ride can be finished.
        min_land_finish = min(s + d for s, d in zip(landStartTime, landDuration))
        
        # Given the tourist is free at min_land_finish, find the best water ride to take second.
        # The start time for a water ride is max(min_land_finish, water_start_time).
        # The total time is that start time plus the duration. We find the minimum over all water rides.
        time_land_first = min(max(min_land_finish, ws) + wd for ws, wd in zip(waterStartTime, waterDuration))
        
        # --- Scenario 2: Water ride first, then land ride ---
        
        # The logic is symmetric. Find the earliest any water ride can be finished.
        min_water_finish = min(s + d for s, d in zip(waterStartTime, waterDuration))
        
        # Find the best land ride to take second.
        time_water_first = min(max(min_water_finish, ls) + ld for ls, ld in zip(landStartTime, landDuration))
        
        # The overall earliest finish time is the minimum of the two scenarios.
        return min(time_land_first, time_water_first)