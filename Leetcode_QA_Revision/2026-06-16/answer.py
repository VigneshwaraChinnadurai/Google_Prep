class Solution:
    def processStr(self, s: str) -> str:
        """
        Processes a string with special operations by simulating the process.

        The method iterates through the input string `s` and builds a result
        string based on a set of rules. A list of characters is used to
        efficiently handle the modifications (append, pop, duplicate, reverse).

        - Lowercase letters are appended.
        - '*' removes the last character.
        - '#' duplicates the current result.
        - '%' reverses the current result.

        After processing all characters, the list is joined into the final string.
        """
        res_list = []
        for char in s:
            if 'a' <= char <= 'z':
                res_list.append(char)
            elif char == '*':
                if res_list:
                    res_list.pop()
            elif char == '#':
                res_list.extend(res_list)
            elif char == '%':
                res_list.reverse()
        
        return "".join(res_list)