from typing import List

class Solution:
    def minSwaps(self, nums: List[int]) -> int:
        def digit_sum(x):
            s = 0
            while x:
                s += x % 10
                x //= 10
            return s

        n = len(nums)
        # Target sorted order
        target = sorted(nums, key=lambda x: (digit_sum(x), x))
        # Map value to its target index
        pos = {v: i for i, v in enumerate(target)}

        # Count cycles in the permutation
        visited = [False] * n
        swaps = 0
        for i in range(n):
            if visited[i] or nums[i] == target[i]:
                visited[i] = True
                continue
            cycle = 0
            j = i
            while not visited[j]:
                visited[j] = True
                j = pos[nums[j]]
                cycle += 1
            swaps += cycle - 1

        return swaps