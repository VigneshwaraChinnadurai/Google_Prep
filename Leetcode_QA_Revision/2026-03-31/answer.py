class Solution:
    def checkStrings(self, s1: str, s2: str) -> bool:
        # We use a 2D array for frequency counts.
        # counts[0] will store counts for even-indexed characters.
        # counts[1] will store counts for odd-indexed characters.
        # We increment for characters from s1 and decrement for characters from s2.
        # If the multisets for each parity are equal, the final counts for all 
        # characters will be zero.
        counts = [[0] * 26, [0] * 26]
        
        for i in range(len(s1)):
            parity = i % 2
            
            # Update counts based on s1's character
            counts[parity][ord(s1[i]) - ord('a')] += 1
            
            # Update counts based on s2's character
            counts[parity][ord(s2[i]) - ord('a')] -= 1
            
        # Check if all counts are zero for both parities.
        # A list of all zeros to compare against.
        zero_counts = [0] * 26
        
        return counts[0] == zero_counts and counts[1] == zero_counts