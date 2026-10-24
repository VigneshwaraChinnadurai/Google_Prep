class Solution:
    def countDaysTogether(self, arriveAlice: str, leaveAlice: str, arriveBob: str, leaveBob: str) -> int:
        days_in_month = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
        prefix = [0] * 13
        for i in range(12):
            prefix[i + 1] = prefix[i] + days_in_month[i]

        def to_day(date: str) -> int:
            month = int(date[:2])
            day = int(date[3:])
            return prefix[month - 1] + day

        start = max(to_day(arriveAlice), to_day(arriveBob))
        end = min(to_day(leaveAlice), to_day(leaveBob))
        return max(0, end - start + 1)