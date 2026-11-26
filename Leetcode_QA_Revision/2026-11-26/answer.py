class Solution:
    def canAliceWin(self, n: int) -> bool:
        take = 10
        alice = False
        while take > 0 and n >= take:
            n -= take
            take -= 1
            alice = not alice
        return alice