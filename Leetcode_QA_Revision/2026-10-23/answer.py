class Solution:
    def sortPermutation(self, nums: List[int]) -> int:
        ans = -1  # all bits set
        for i, v in enumerate(nums):
            if v != i:
                ans &= v
        return 0 if ans == -1 else ans