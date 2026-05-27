class Solution:
    def numberOfSpecialChars(self, word: str) -> int:
        # Dictionary to store the last seen index of each lowercase character.
        last_lower_idx = {}
        
        # Dictionary to store the first seen index of each uppercase character.
        first_upper_idx = {}

        for i, char in enumerate(word):
            if char.islower():
                # By always updating, we ensure this stores the last seen index.
                last_lower_idx[char] = i
            else: # char.isupper()
                # We only care about the first occurrence, so we only store it once.
                if char not in first_upper_idx:
                    first_upper_idx[char] = i
        
        special_count = 0
        # Iterate through all 26 letters of the alphabet.
        for i in range(26):
            lower_char = chr(ord('a') + i)
            upper_char = lower_char.upper()

            # A character is a candidate for being special if both its lowercase
            # and uppercase forms appeared in the word.
            if lower_char in last_lower_idx and upper_char in first_upper_idx:
                # The core condition for a character to be special is that the last
                # occurrence of its lowercase form must appear before the first
                # occurrence of its uppercase form.
                if last_lower_idx[lower_char] < first_upper_idx[upper_char]:
                    special_count += 1
        
        return special_count