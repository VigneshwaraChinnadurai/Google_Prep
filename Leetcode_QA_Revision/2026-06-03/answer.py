import math
from typing import List

class Solution:
    def earliestFinishTime(self, landStartTime: List[int], landDuration: List[int], waterStartTime: List[int], waterDuration: List[int]) -> int:
        
        def calculate_one_way_finish_time(start_times1: List[int], durations1: List[int], start_times2: List[int], durations2: List[int]) -> int:
            """
            Calculates the minimum finish time for the order: ride from group 1 -> ride from group 2.
            
            The total finish time for a pair of rides (ride1_i, ride2_j) is:
            max(finish_time_of_ride1, start_time_of_ride2) + duration_of_ride2
            
            To optimize, we iterate through each possible first ride and for each, find the best
            possible second ride.
            """
            n1 = len(start_times1)
            n2 = len(start_times2)

            # Create pairs for the first set of rides and sort by their natural finish time.
            # (finish_time, duration)
            rides1 = sorted([(start_times1[i] + durations1[i], durations1[i]) for i in range(n1)])

            # Create pairs for the second set of rides and sort by their start time.
            # (start_time, duration)
            rides2 = sorted([(start_times2[j], durations2[j]) for j in range(n2)])

            # Precompute suffix minimums of (start_time + duration) for the second set of rides.
            # This helps find the best second ride among those that start *after* the first one finishes.
            # The finish time in this case is simply start_time2 + duration2.
            suffix_min_finish2 = [0] * n2
            suffix_min_finish2[n2 - 1] = rides2[n2 - 1][0] + rides2[n2 - 1][1]
            for j in range(n2 - 2, -1, -1):
                suffix_min_finish2[j] = min(suffix_min_finish2[j + 1], rides2[j][0] + rides2[j][1])

            min_total_finish = math.inf
            min_duration2_prefix = math.inf
            
            # Use a two-pointer approach. 'i' for rides1, 'j' for rides2.
            j = 0
            for i in range(n1):
                finish_time1 = rides1[i][0]

                # For the current ride1, we partition rides2 into two groups:
                # 1. Those that start at or before finish_time1.
                # 2. Those that start after finish_time1.
                # The pointer 'j' finds this partition point.
                while j < n2 and rides2[j][0] <= finish_time1:
                    # For group 1, the best ride is the one with the minimum duration.
                    # We track this minimum as we advance 'j'.
                    min_duration2_prefix = min(min_duration2_prefix, rides2[j][1])
                    j += 1

                # Option A: Choose a second ride from group 1 (starts <= finish_time1).
                # The tourist finishes ride 1 at finish_time1 and can start ride 2 immediately.
                # Total finish time = finish_time1 + min_duration_of_ride2.
                if min_duration2_prefix != math.inf:
                    min_total_finish = min(min_total_finish, finish_time1 + min_duration2_prefix)

                # Option B: Choose a second ride from group 2 (starts > finish_time1).
                # The tourist finishes ride 1 at finish_time1, waits, and starts ride 2 at its start time.
                # The best choice is the one with the minimum (start_time + duration).
                # This is precomputed in our suffix_min_finish2 array at index 'j'.
                if j < n2:
                    min_total_finish = min(min_total_finish, suffix_min_finish2[j])
            
            return min_total_finish

        # The tourist can do Land -> Water or Water -> Land. We calculate the minimum for both scenarios.
        
        # Case 1: Land ride first, then Water ride.
        land_then_water = calculate_one_way_finish_time(landStartTime, landDuration, waterStartTime, waterDuration)

        # Case 2: Water ride first, then Land ride.
        water_then_land = calculate_one_way_finish_time(waterStartTime, waterDuration, landStartTime, landDuration)

        return min(land_then_water, water_then_land)