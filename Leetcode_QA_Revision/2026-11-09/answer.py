class Solution:
    def busiestServers(self, k: int, arrival: list[int], load: list[int]) -> list[int]:
        import heapq
        free = list(range(k))  # min-heap of free server ids, stored as virtual ids >= current i
        heapq.heapify(free)
        busy = []  # (end_time, virtual_id)
        count = [0] * k
        for i, (t, l) in enumerate(zip(arrival, load)):
            # release servers that are done, re-queue them at the next virtual slot congruent to their id
            while busy and busy[0][0] <= t:
                _, vid = heapq.heappop(busy)
                # smallest virtual id >= i with the same residue mod k
                nvid = i + ((vid - i) % k)
                heapq.heappush(free, nvid)
            if not free:
                continue
            # free holds virtual ids; ids below i are stale, so shift them forward lazily
            vid = heapq.heappop(free)
            sid = vid % k
            count[sid] += 1
            heapq.heappush(busy, (t + l, vid))
        mx = max(count)
        return [i for i in range(k) if count[i] == mx]