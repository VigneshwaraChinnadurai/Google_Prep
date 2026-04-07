from typing import List

class Robot:

    def __init__(self, width: int, height: int):
        self.w = width
        self.h = height
        # The robot moves along the perimeter. The total length of this path is
        # (width-1) East, (height-1) North, (width-1) West, (height-1) South.
        self.perimeter = 2 * (width - 1) + 2 * (height - 1)
        
        # self.pos_linear represents the total number of steps taken along the perimeter,
        # modulo the perimeter length. This simplifies calculating the final position
        # without simulating each step, which would be too slow given the constraints.
        self.pos_linear = 0
        
        # A flag to handle the initial state. Before any move, the robot is at (0,0)
        # facing East. After the first move, its state is determined by pos_linear.
        # This is crucial to distinguish the start state from landing on (0,0) after
        # completing a cycle, where the direction would be "South".
        self.moved_once = False

    def step(self, num: int) -> None:
        # Update the linear position. The modulo operation keeps the value
        # within the bounds of the perimeter length, effectively handling cycles.
        self.pos_linear = (self.pos_linear + num) % self.perimeter
        self.moved_once = True

    def getPos(self) -> List[int]:
        # Handle the initial state before any move.
        if not self.moved_once:
            return [0, 0]

        p = self.pos_linear
        w_1 = self.w - 1
        h_1 = self.h - 1

        # The perimeter path is divided into four segments.
        # We determine which segment the robot is on based on its linear position `p`.
        
        # Corner points in terms of linear distance from start
        c1 = w_1       # End of bottom edge: (w-1, 0)
        c2 = c1 + h_1  # End of right edge: (w-1, h-1)
        c3 = c2 + w_1  # End of top edge: (0, h-1)

        if p == 0:
            # Completed a full cycle, back at (0,0)
            return [0, 0]
        elif p <= c1:
            # On the bottom edge, moving East.
            return [p, 0]
        elif p <= c2:
            # On the right edge, moving North.
            return [w_1, p - c1]
        elif p <= c3:
            # On the top edge, moving West.
            return [w_1 - (p - c2), h_1]
        else: # p > c3
            # On the left edge, moving South.
            return [0, h_1 - (p - c3)]

    def getDir(self) -> str:
        # Handle the initial state before any move.
        if not self.moved_once:
            return "East"

        p = self.pos_linear
        w_1 = self.w - 1
        h_1 = self.h - 1
        
        # Corner points
        c1 = w_1
        c2 = c1 + h_1
        c3 = c2 + w_1

        if p == 0:
            # Completed a full cycle, arrived at (0,0) from (0,1), so facing South.
            return "South"
        elif p <= c1:
            # On the bottom edge.
            return "East"
        elif p <= c2:
            # On the right edge.
            return "North"
        elif p <= c3:
            # On the top edge.
            return "West"
        else: # p > c3
            # On the left edge.
            return "South"