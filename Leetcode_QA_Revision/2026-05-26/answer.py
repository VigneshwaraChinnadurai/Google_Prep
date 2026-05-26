class Solution:
    def numberOfSpecialChars(self, word: str) -> int:
        # Use two bitmasks to track the presence of lowercase and uppercase letters.
        # Each bit from 0 to 25 corresponds to 'a'/'A' through 'z'/'Z'.
        lower_present = 0
        upper_present = 0

        for char in word:
            if 'a' <= char <= 'z':
                # Set the bit for the corresponding lowercase letter.
                # The bit position is determined by its distance from 'a'.
                # e.g., 'a' -> bit 0, 'b' -> bit 1, etc.
                lower_present |= 1 << (ord(char) - ord('a'))
            elif 'A' <= char <= 'Z':
                # Set the bit for the corresponding uppercase letter.
                # The bit position is determined by its distance from 'A'.
                upper_present |= 1 << (ord(char) - ord('A'))
        
        # A letter is special if its corresponding bit is set in both masks.
        # A bitwise AND operation gives us a mask representing only the special letters.
        special_chars_mask = lower_present & upper_present
        
        # The number of special characters is the number of set bits (population count)
        # in this final mask. bin(n).count('1') is a concise way to count set bits in Python.
        return bin(special_chars_mask).count('1')