class Solution:
    def minOperations(self, nums: List[int]) -> int:
        n = len(nums)
        asc = all(nums[(i + 1) % n] == (nums[i] + 1) % n for i in range(n))
        desc = all(nums[(i + 1) % n] == (nums[i] - 1) % n for i in range(n))
        if not asc and not desc:
            return -1
        k = nums[0]
        best = float('inf')
        if asc:
            if k == 0:
                best = 0
            else:
                # rotate left (n-k) times, or reverse + (k rotations) + reverse
                best = min(n - k, k + 2)
        if desc:
            # rotate until n-1 is in front, then reverse
            best = min(best, (k + 1) % n + 1)
            # reverse first, then rotate left
            best = min(best, 1 + (n - k - 1) % n)
        return best