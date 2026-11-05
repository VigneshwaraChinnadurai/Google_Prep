class Solution:
    def findTheDistanceValue(self, arr1: list[int], arr2: list[int], d: int) -> int:
        from bisect import bisect_left
        b = sorted(arr2)
        m = len(b)
        count = 0
        for a in arr1:
            i = bisect_left(b, a)
            ok = True
            if i < m and b[i] - a <= d:
                ok = False
            if ok and i > 0 and a - b[i - 1] <= d:
                ok = False
            if ok:
                count += 1
        return count