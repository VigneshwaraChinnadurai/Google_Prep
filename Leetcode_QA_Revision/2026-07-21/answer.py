from itertools import groupby

class Solution:
  def maxActiveSectionsAfterTrade(self, s: str) -> int:
    """
    Maximizes the number of active sections ('1's) in a binary string by performing at most one trade.

    A trade involves:
    1. Converting a '1'-block surrounded by '0's to all '0's.
    2. Converting a '0'-block surrounded by '1's to all '1's.

    The "surrounded" condition is evaluated on an augmented string t = '1' + s + '1'.

    The core idea is to find the trade that yields the maximum net gain in the number of '1's.
    The net gain from a trade is `L0 - L1`, where `L1` is the length of the '1'-block removed
    and `L0` is the length of the '0'-block filled.

    To maximize this gain, for each possible '1'-block we can remove, we must find the best
    '0'-block to fill.

    Algorithm:
    1. Calculate the initial number of '1's in `s`.
    2. Create the augmented string `t = '1' + s + '1'`. This simplifies boundary checks.
    3. Parse `t` into a sequence of alternating '1'-blocks and '0'-blocks and store their lengths.
       `t` will always start and end with a '1'-block.
    4. Handle base cases: If no trade is possible (e.g., no '0's, or no '1'-blocks surrounded by '0's),
       the answer is the initial count of '1's.
    5. Iterate through each "internal" '1'-block. These are the only ones that can be removed as they
       are guaranteed to be surrounded by '0's. Let the length of a chosen '1'-block be `L1`.
    6. For each chosen '1'-block, determine the best '0'-block to fill. After removing the '1'-block,
       two scenarios arise for the second step of the trade:
       a) The removed '1'-block (now '0's) merges with its two neighboring '0'-blocks, creating a
          new, larger '0'-block. We can choose to fill this merged block.
          The net gain is (length of left '0's + length of right '0's).
       b) We can choose to fill any of the original '0'-blocks elsewhere in the string. To maximize
          the gain, we would choose the largest one.
          The net gain is (length of largest '0'-block - `L1`).
    7. The best gain for the chosen '1'-block is the maximum of the gains from scenarios (a) and (b).
    8. Keep track of the maximum gain found across all possible '1'-blocks to remove.
    9. The final answer is the initial number of '1's plus this maximum gain. If no profitable
       trade exists, the max gain will be 0.

    Complexity:
    - Time: O(N), where N is the length of `s`. Parsing the string takes O(N). The loop runs over
      the number of blocks, which is at most N.
    - Space: O(K), where K is the number of blocks, which is at most N. This is for storing block lengths.
    """
    initial_ones = s.count('1')

    # Augment the string to handle boundary conditions easily.
    t = '1' + s + '1'

    # Parse the augmented string into blocks of consecutive characters.
    blocks = []
    for k, g in groupby(t):
        blocks.append((k, len(list(g))))

    # Separate the lengths of '1' blocks and '0' blocks.
    ones_lengths = [length for char, length in blocks if char == '1']
    zeros_lengths = [length for char, length in blocks if char == '0']

    # If there are no '0' blocks, a '1' block cannot be surrounded by '0's, so no trade is possible.
    if not zeros_lengths:
        return initial_ones

    # If there are fewer than three '1' blocks in 't', there are no internal '1' blocks
    # that can be removed. The first and last are boundaries.
    if len(ones_lengths) <= 2:
        return initial_ones

    # Find the maximum length of any '0' block. This is a candidate for filling.
    max_l0 = max(zeros_lengths)

    max_gain = 0
    
    # Iterate through all '1' blocks that can be removed. These are the "internal" blocks,
    # from index 1 to len-2 in the ones_lengths list.
    for i in range(1, len(ones_lengths) - 1):
        l1_to_remove = ones_lengths[i]
        
        # The neighboring '0' blocks are at indices i-1 and i in the zeros_lengths list.
        l0_left = zeros_lengths[i-1]
        l0_right = zeros_lengths[i]
        
        # --- Calculate potential gain from two strategies for the second step ---
        
        # Strategy 1: Fill the new '0' block created by merging the neighbors
        # with the removed '1' block.
        # Net gain = (l0_left + l1_to_remove + l0_right) - l1_to_remove
        gain_if_merge = l0_left + l0_right
        
        # Strategy 2: Fill the largest existing '0' block elsewhere.
        # Net gain = max_l0 - l1_to_remove
        gain_if_separate = max_l0 - l1_to_remove
        
        # The gain for this choice of '1' block is the max of the two strategies.
        current_gain = max(gain_if_merge, gain_if_separate)
        
        # Update the overall maximum possible gain.
        max_gain = max(max_gain, current_gain)
        
    # The final answer is the initial count of '1's plus the maximum possible gain.
    # If no trade is profitable, max_gain will remain 0.
    return initial_ones + max_gain