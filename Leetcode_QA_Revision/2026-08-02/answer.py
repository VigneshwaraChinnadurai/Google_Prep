from typing import List

class Solution:
    def stoneGame(self, piles: List[int]) -> bool:
        """
        This is a game theory problem. While it can be solved with dynamic programming,
        a simpler mathematical argument based on the game's constraints leads to a
        constant-time solution.

        The constraints are key:
        1. The number of piles (`n`) is even.
        2. The total number of stones is odd.
        3. Alice (the first player) and Bob play optimally.

        Let's analyze the structure of the game. The piles are at indices 0, 1, ..., n-1.
        Since `n` is even, there are `n/2` even-indexed piles and `n/2` odd-indexed piles.
        Let `S_even` be the sum of stones in even-indexed piles (piles[0], piles[2], ...).
        Let `S_odd` be the sum of stones in odd-indexed piles (piles[1], piles[3], ...).

        The total sum of stones is `S_even + S_odd`. Since this total is odd, it's
        impossible for `S_even` to be equal to `S_odd`. One must be strictly greater.

        Alice, as the first player, has a powerful choice. The initial piles are
        piles[0...n-1]. The ends are piles[0] (even index) and piles[n-1] (odd index,
        since `n` is even).

        Alice can decide at the start which "color" of piles (even-indexed or odd-indexed)
        she will collect.
        
        - If Alice wants to collect the even-indexed piles, she takes `piles[0]`.
          Bob is now faced with `piles[1...n-1]`. Both ends are at odd indices.
          Bob *must* take an odd-indexed pile. This leaves Alice with a choice
          between an even and an odd index on her next turn. She can again take the
          even one. This pattern continues, allowing Alice to claim all even-indexed piles.

        - Similarly, if Alice wants the odd-indexed piles, she starts with `piles[n-1]`.
          This forces Bob to always choose from even-indexed piles, allowing Alice to
          claim all odd-indexed piles.

        Since Alice plays optimally, she can calculate `S_even` and `S_odd` beforehand.
        - If `S_even > S_odd`, she will commit to the "take evens" strategy and win.
        - If `S_odd > S_even`, she will commit to the "take odds" strategy and win.

        Because `S_even` can never equal `S_odd`, a winning strategy is always
        available to Alice. Therefore, she always wins.
        """
        return True