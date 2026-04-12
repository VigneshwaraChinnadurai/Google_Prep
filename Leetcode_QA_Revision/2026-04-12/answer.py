class Solution:
    def minimumDistance(self, word: str) -> int:
        memo = {}
        n = len(word)

        def distance(idx1, idx2):
            """Calculates Manhattan distance between two character indices.
            -1 represents a free finger, so distance is 0."""
            if idx1 == -1:
                return 0
            x1, y1 = idx1 // 6, idx1 % 6
            x2, y2 = idx2 // 6, idx2 % 6
            return abs(x1 - x2) + abs(y1 - y2)

        def solve(i, other_finger_idx):
            """
            Calculates min cost to type word[i:] given one finger is on word[i-1]
            and the other is at other_finger_idx.
            """
            if i == n:
                return 0
            
            state = (i, other_finger_idx)
            if state in memo:
                return memo[state]

            prev_char_idx = ord(word[i-1]) - ord('A')
            target_char_idx = ord(word[i]) - ord('A')

            # Option 1: Move the finger that was at word[i-1] to word[i].
            # The other finger at other_finger_idx stays.
            # For the next step (i+1), one finger is at word[i], other is at other_finger_idx.
            cost1 = distance(prev_char_idx, target_char_idx) + solve(i + 1, other_finger_idx)

            # Option 2: Move the "other finger" from other_finger_idx to word[i].
            # The finger at word[i-1] stays.
            # For the next step (i+1), one finger is at word[i], other is at word[i-1].
            cost2 = distance(other_finger_idx, target_char_idx) + solve(i + 1, prev_char_idx)
            
            memo[state] = min(cost1, cost2)
            return memo[state]

        # Initial call:
        # Typing word[0] is free. We then need to type word[1:].
        # At this point, one finger is at word[0], the other is free (-1).
        # This corresponds to the state solve(1, -1).
        return solve(1, -1)