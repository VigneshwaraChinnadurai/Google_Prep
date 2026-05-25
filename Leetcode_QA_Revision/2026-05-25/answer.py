class Solution:
    def canReach(self, s: str, minJump: int, maxJump: int) -> bool:
        n = len(s)
        if s[-1] == '1':
            return False

        # dp[i] is True if index i is reachable from index 0.
        dp = [False] * n
        dp[0] = True

        # 'count' tracks the number of reachable positions (where dp[j] is True)
        # in the current sliding window. The window for index 'i' is
        # [i - maxJump, i - minJump]. These are the indices 'j' from which
        # we could potentially jump to 'i'.
        count = 0

        for i in range(1, n):
            # Update the sliding window count.
            # When we move from i-1 to i, the window slides one step to the right.
            
            # The index (i - minJump) enters the window's right side.
            # We add its reachability status (True=1, False=0) to the count.
            if i >= minJump:
                count += dp[i - minJump]
            
            # The index (i - maxJump - 1) leaves the window's left side.
            # We subtract its reachability status from the count.
            if i > maxJump:
                count -= dp[i - maxJump - 1]

            # An index 'i' is reachable if s[i] is '0' and there is at least
            # one reachable position in the valid jump window (i.e., count > 0).
            if s[i] == '0' and count > 0:
                dp[i] = True
        
        return dp[n - 1]