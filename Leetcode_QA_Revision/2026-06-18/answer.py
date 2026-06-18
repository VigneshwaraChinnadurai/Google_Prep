class Solution:
    def angleClock(self, hour: int, minutes: int) -> float:
        """
        Calculates the smaller angle between the hour and minute hands of a clock.
        """
        
        # 1. Calculate the angle of the minute hand.
        # A full circle is 360 degrees. There are 60 minutes on a clock face.
        # Angle per minute = 360 / 60 = 6 degrees.
        # The angle is measured clockwise from the 12 o'clock position.
        minute_angle = minutes * 6.0
        
        # 2. Calculate the angle of the hour hand.
        # A full circle is 360 degrees. There are 12 hours on a clock face.
        # Angle per hour mark = 360 / 12 = 30 degrees.
        # The hour hand's position is also affected by the minutes.
        # In 60 minutes, the hour hand moves 30 degrees (from one hour mark to the next).
        # Angle per minute for the hour hand = 30 / 60 = 0.5 degrees.
        
        # We use `hour % 12` to handle the 12 o'clock case, treating it as 0.
        # For hour = 12, 12 % 12 = 0. For hours 1-11, hour % 12 = hour.
        hour_angle = (hour % 12) * 30.0 + minutes * 0.5
        
        # 3. Calculate the difference between the two angles.
        angle_diff = abs(hour_angle - minute_angle)
        
        # 4. The result must be the smaller angle.
        # The two angles formed by the hands are `angle_diff` and `360 - angle_diff`.
        # We return the minimum of these two.
        return min(angle_diff, 360 - angle_diff)