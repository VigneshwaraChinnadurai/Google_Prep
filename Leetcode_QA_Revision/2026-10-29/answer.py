class Solution:
    def palindromePath(self, n: int, edges: list[list[int]], s: str, queries: list[str]) -> list[bool]:
        adj = [[] for _ in range(n)]
        for u, v in edges:
            adj[u].append(v)
            adj[v].append(u)

        cur = [ord(ch) - 97 for ch in s]
        LOG = max(1, (n - 1).bit_length())
        parent = [0] * n
        depth = [0] * n
        tin = [0] * n
        base = [0] * n
        order = []
        seen = [False] * n
        stack = [0]
        seen[0] = True
        base[0] = 1 << cur[0]
        while stack:
            u = stack.pop()
            tin[u] = len(order)
            order.append(u)
            for w in adj[u]:
                if not seen[w]:
                    seen[w] = True
                    parent[w] = u
                    depth[w] = depth[u] + 1
                    base[w] = base[u] ^ (1 << cur[w])
                    stack.append(w)

        size = [1] * n
        for u in reversed(order):
            if u != 0:
                size[parent[u]] += size[u]

        up = [parent]
        for k in range(1, LOG):
            prev = up[-1]
            up.append([prev[prev[v]] for v in range(n)])

        def lca(a, b):
            if depth[a] < depth[b]:
                a, b = b, a
            diff = depth[a] - depth[b]
            for k in range(LOG):
                if diff >> k & 1:
                    a = up[k][a]
            if a == b:
                return a
            for k in range(LOG - 1, -1, -1):
                if up[k][a] != up[k][b]:
                    a = up[k][a]
                    b = up[k][b]
            return parent[a]

        # XOR Fenwick tree: range xor update, point query
        bit = [0] * (n + 2)

        def add(i, x):
            while i <= n + 1:
                bit[i] ^= x
                i += i & -i

        def pref(i):
            r = 0
            while i > 0:
                r ^= bit[i]
                i -= i & -i
            return r

        res = []
        for q in queries:
            parts = q.split()
            if parts[0] == "update":
                u = int(parts[1])
                c = ord(parts[2]) - 97
                if cur[u] != c:
                    x = (1 << cur[u]) ^ (1 << c)
                    add(tin[u] + 1, x)
                    add(tin[u] + size[u] + 1, x)
                    cur[u] = c
            else:
                u = int(parts[1])
                v = int(parts[2])
                l = lca(u, v)
                pu = base[u] ^ pref(tin[u] + 1)
                pv = base[v] ^ pref(tin[v] + 1)
                mask = pu ^ pv ^ (1 << cur[l])
                res.append(mask & (mask - 1) == 0)
        return res