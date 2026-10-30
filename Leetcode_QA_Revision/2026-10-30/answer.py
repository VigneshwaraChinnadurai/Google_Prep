class Solution:
    def shortestPath(self, n: int, edges: List[List[int]], labels: str, k: int) -> int:
        import heapq
        adj = [[] for _ in range(n)]
        for u, v, w in edges:
            adj[u].append((v, w))

        K = k + 1
        INF = float('inf')
        dist = [INF] * (n * K)
        start = 0 * K + 1
        dist[start] = 0
        heap = [(0, start)]
        while heap:
            d, state = heapq.heappop(heap)
            if d > dist[state]:
                continue
            u, r = divmod(state, K)
            if u == n - 1:
                return d
            lu = labels[u]
            for v, w in adj[u]:
                if labels[v] == lu:
                    nr = r + 1
                    if nr > k:
                        continue
                else:
                    nr = 1
                ns = v * K + nr
                nd = d + w
                if nd < dist[ns]:
                    dist[ns] = nd
                    heapq.heappush(heap, (nd, ns))
        return -1