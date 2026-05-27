"""
Memory Tracker Utility
======================
Monitors GPU and system RAM usage during AirLLM inference.
Useful for understanding resource consumption patterns.
"""

import time
import psutil
from dataclasses import dataclass, field
from typing import List

try:
    import torch
    HAS_CUDA = torch.cuda.is_available()
except ImportError:
    HAS_CUDA = False


@dataclass
class MemorySnapshot:
    """A single memory measurement."""
    timestamp: float
    label: str
    ram_used_gb: float
    ram_total_gb: float
    gpu_used_gb: float = 0.0
    gpu_total_gb: float = 0.0


class MemoryTracker:
    """Track memory usage throughout the inference pipeline."""

    def __init__(self):
        self.snapshots: List[MemorySnapshot] = []
        self._start_time = time.time()

    def snapshot(self, label: str = "") -> MemorySnapshot:
        """Take a memory snapshot with an optional label."""
        ram = psutil.virtual_memory()
        ram_used = ram.used / (1024 ** 3)
        ram_total = ram.total / (1024 ** 3)

        gpu_used = 0.0
        gpu_total = 0.0

        if HAS_CUDA:
            gpu_used = torch.cuda.memory_allocated() / (1024 ** 3)
            gpu_total = torch.cuda.get_device_properties(0).total_mem / (1024 ** 3)

        snap = MemorySnapshot(
            timestamp=time.time() - self._start_time,
            label=label,
            ram_used_gb=ram_used,
            ram_total_gb=ram_total,
            gpu_used_gb=gpu_used,
            gpu_total_gb=gpu_total,
        )
        self.snapshots.append(snap)
        return snap

    def report(self) -> str:
        """Generate a formatted memory usage report."""
        lines = [
            "=" * 60,
            "MEMORY USAGE REPORT",
            "=" * 60,
            f"{'Time (s)':<10} {'Label':<25} {'RAM (GB)':<15} {'GPU (GB)':<15}",
            "-" * 60,
        ]
        for s in self.snapshots:
            ram_str = f"{s.ram_used_gb:.2f}/{s.ram_total_gb:.2f}"
            gpu_str = f"{s.gpu_used_gb:.2f}/{s.gpu_total_gb:.2f}" if s.gpu_total_gb > 0 else "N/A"
            lines.append(f"{s.timestamp:<10.2f} {s.label:<25} {ram_str:<15} {gpu_str:<15}")

        lines.append("=" * 60)

        if len(self.snapshots) >= 2:
            peak_ram = max(s.ram_used_gb for s in self.snapshots)
            peak_gpu = max(s.gpu_used_gb for s in self.snapshots)
            lines.append(f"Peak RAM Usage: {peak_ram:.2f} GB")
            if HAS_CUDA:
                lines.append(f"Peak GPU Usage: {peak_gpu:.2f} GB")
            total_time = self.snapshots[-1].timestamp - self.snapshots[0].timestamp
            lines.append(f"Total Time: {total_time:.2f} seconds")

        return "\n".join(lines)

    def print_report(self):
        """Print the memory usage report to console."""
        print(self.report())
