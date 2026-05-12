class Solution:
    def minimumEffort(self, tasks: list[list[int]]) -> int:
        """
        Finds the minimum initial energy to finish all tasks.

        The core idea is to determine the optimal order to perform the tasks.
        This is a greedy problem. The best order is determined by sorting the tasks.
        Let's consider the quantity `minimum - actual` for each task. This represents
        the "energy buffer" of a task. A task with a larger buffer is "less demanding"
        in the sense that after completing it, the energy drop `actual` is small
        relative to the `minimum` energy required to start it.

        By performing tasks with a larger `minimum - actual` difference first, we
        ensure that we tackle the most "energy-saving" tasks when our overall energy
        is highest. This leaves more energy for later tasks which might have a
        tighter constraint (a smaller `minimum - actual` difference). This can be
        proven optimal using an exchange argument.

        So, the optimal sequence is to perform tasks sorted by `minimum - actual`
        in descending order.

        To calculate the minimum initial energy for this optimal order, we can
        simulate the process in reverse. Let the optimal sequence of tasks be
        t_1, t_2, ..., t_n.
        - To perform the last task, t_n, we need at least `minimum_n` energy right before it.
        - To perform t_{n-1} and then t_n, we must have enough energy to start t_{n-1}
          (at least `minimum_{n-1}`) and, after completing it, have enough energy
          left for the rest of the sequence (which starts with t_n).
        - This leads to a recurrence: if `R_i` is the energy required just before
          starting task t_i, then `R_i = max(minimum_i, R_{i+1} + actual_i)`,
          with `R_{n+1} = 0`. The final answer is `R_1`.

        We can implement this by sorting tasks by `minimum - actual` in ascending
        order, which gives us the optimal sequence in reverse (t_n, t_{n-1}, ..., t_1).
        Then, we can iterate through this sorted list and apply the recurrence.
        """
        
        # Sort tasks by (minimum - actual) ascending. This gives the optimal
        # execution order in reverse.
        tasks.sort(key=lambda x: x[1] - x[0])
        
        # needed_energy will track the required energy for the suffix of tasks
        # we have considered. We start with the last task in the optimal sequence.
        needed_energy = 0
        for actual, minimum in tasks:
            # Let needed_energy be the energy required for the tasks that come *after*
            # the current one in the optimal sequence.
            # To do the current task and all subsequent ones, we must have enough
            # energy before this task. This amount must be at least 'minimum' to start,
            # and after spending 'actual', we must have 'needed_energy' left.
            # So, we need max(minimum, needed_energy + actual).
            needed_energy = max(minimum, needed_energy + actual)
            
        return needed_energy