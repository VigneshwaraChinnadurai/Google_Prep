import re

class Solution:
    def evaluate(self, s: str, knowledge: list[list[str]]) -> str:
        kmap = dict(knowledge)
        return re.sub(r'\(([^)]+)\)', lambda m: kmap.get(m.group(1), '?'), s)