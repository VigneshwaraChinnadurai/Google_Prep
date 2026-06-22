import collections

class Solution:
    def maxNumberOfBalloons(self, text: str) -> int:
        """
        Calculates the maximum number of "balloon" instances that can be formed
        from the characters in the input text.
        """
        
        # The word "balloon" requires the following character counts:
        # b: 1
        # a: 1
        # l: 2
        # o: 2
        # n: 1
        
        # First, count the frequency of each character in the input string 'text'.
        # collections.Counter is an efficient way to do this.
        text_counts = collections.Counter(text)
        
        # Determine how many instances of "balloon" can be formed based on the
        # available characters. This is limited by the character that runs out first.
        
        # For 'b', 'a', 'n', we need one of each per "balloon".
        # The number of available characters is the maximum number of balloons
        # we can form from that character's perspective.
        # If a character is missing, its count in text_counts will be 0.
        b_potential = text_counts['b']
        a_potential = text_counts['a']
        n_potential = text_counts['n']
        
        # For 'l' and 'o', we need two of each per "balloon".
        # So, the number of balloons we can form is the count of the character
        # divided by 2 (using integer division).
        l_potential = text_counts['l'] // 2
        o_potential = text_counts['o'] // 2
        
        # The overall maximum number of "balloon"s is the minimum of the
        # potential counts calculated above, as this represents the bottleneck.
        return min(b_potential, a_potential, l_potential, o_potential, n_potential)