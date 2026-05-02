class Solution:
    def rotatedDigits(self, n: int) -> int:
        count = 0
        for i in range(1, n + 1):
            s = str(i)
            # A number is "good" if:
            # 1. It is a valid number after rotation. This means it must not contain '3', '4', or '7'.
            # 2. The rotated number is different from the original. This means it must contain at least one of '2', '5', '6', or '9'.
            
            # First, check for invalid digits. If any are present, the number is not good.
            # Digits '3', '4', '7' become invalid after rotation.
            if '3' in s or '4' in s or '7' in s:
                continue
            
            # If the number is composed of only valid digits, we then check if it changes after rotation.
            # It changes if it contains at least one of '2', '5', '6', or '9'.
            # Digits '0', '1', '8' rotate to themselves. If a number only has these, it's not "good".
            if '2' in s or '5' in s or '6' in s or '9' in s:
                count += 1
                
        return count