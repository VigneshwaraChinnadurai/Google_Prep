class Solution:
    def largestAltitude(self, gain: list[int]) -> int:
        """
        Calculates the highest altitude reached during a road trip.

        The biker starts at altitude 0. The `gain` array represents the net
        change in altitude between consecutive points. This problem can be solved
        by simulating the trip, calculating the altitude at each point, and
        keeping track of the maximum altitude encountered.
        """
        current_altitude = 0
        max_altitude = 0  # The trip starts at altitude 0, so this is the initial max

        for g in gain:
            # Update the current altitude with the gain from the current segment
            current_altitude += g
            # Update the maximum altitude if the current one is higher
            max_altitude = max(max_altitude, current_altitude)
        
        return max_altitude