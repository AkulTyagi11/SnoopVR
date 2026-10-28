"""
Python ctypes wrapper for libsnoopar_core C-ABI shared library.
Provides clean Pythonic access to the C++20 SnoopAR Bayesian localization engine.
"""

import ctypes
import os
import sys
from pathlib import Path
from typing import List, Tuple, Optional


class SnoopARVoxel(ctypes.Structure):
    _fields_ = [
        ("x", ctypes.c_float),
        ("y", ctypes.c_float),
        ("z", ctypes.c_float),
        ("probability", ctypes.c_float),
    ]


def _find_library() -> str:
    # On Windows Python 3.8+, dependent DLL directories must be explicitly added
    if sys.platform == "win32" and hasattr(os, "add_dll_directory"):
        mingw_bin = "C:/msys64/mingw64/bin"
        if os.path.exists(mingw_bin):
            try:
                os.add_dll_directory(mingw_bin)
            except Exception:
                pass
        build_dir = Path(__file__).parent.parent / "build"
        if build_dir.exists():
            try:
                os.add_dll_directory(str(build_dir))
            except Exception:
                pass

    search_paths = [
        Path(__file__).parent.parent / "build" / "libsnoopar_core.dll",
        Path(__file__).parent.parent / "build" / "libsnoopar_core.so",
        Path(__file__).parent.parent / "build" / "libsnoopar_core.dylib",
        Path(__file__).parent / "libsnoopar_core.dll",
    ]
    for p in search_paths:
        if p.exists():
            return str(p.resolve())
    raise FileNotFoundError(f"libsnoopar_core shared library not found in: {[str(p) for p in search_paths]}")


class SnoopAREngine:
    TRACKER_AIRTAG = 0
    TRACKER_SMARTTAG = 1
    TRACKER_TILE = 2
    TRACKER_GENERIC = 3

    STATE_COARSE_SEARCH = 0
    STATE_READY_FOR_CLOSE_SWEEP = 1
    STATE_CLOSE_SWEEP_ACTIVE = 2
    STATE_TARGET_LOCKED = 3

    HINT_CONTINUE_SWEEP = 0
    HINT_STEP_SIDEWAYS = 1
    HINT_CHANGE_HEIGHT = 2
    HINT_APPROACH_SURFACE = 3
    HINT_TARGET_LOCKED = 4

    def __init__(self, tracker_type: int = TRACKER_AIRTAG, lib_path: Optional[str] = None):
        if lib_path is None:
            lib_path = _find_library()
        self._lib = ctypes.CDLL(lib_path)
        self._setup_bindings()
        self._handle = self._lib.snoopar_create_engine(ctypes.c_int32(tracker_type))
        if not self._handle:
            raise RuntimeError("Failed to allocate SnoopAR C++ Engine")

    def _setup_bindings(self):
        self._lib.snoopar_create_engine.argtypes = [ctypes.c_int32]
        self._lib.snoopar_create_engine.restype = ctypes.c_void_p

        self._lib.snoopar_destroy_engine.argtypes = [ctypes.c_void_p]
        self._lib.snoopar_destroy_engine.restype = None

        self._lib.snoopar_reset_engine.argtypes = [ctypes.c_void_p]
        self._lib.snoopar_reset_engine.restype = None

        self._lib.snoopar_set_tracker_type.argtypes = [ctypes.c_void_p, ctypes.c_int32]
        self._lib.snoopar_set_tracker_type.restype = None

        self._lib.snoopar_add_observation.argtypes = [
            ctypes.c_void_p,
            ctypes.c_float, ctypes.c_float, ctypes.c_float,
            ctypes.c_float, ctypes.c_float, ctypes.c_float,
            ctypes.c_float,
            ctypes.c_uint64,
        ]
        self._lib.snoopar_add_observation.restype = None

        self._lib.snoopar_get_search_state.argtypes = [ctypes.c_void_p]
        self._lib.snoopar_get_search_state.restype = ctypes.c_int32

        self._lib.snoopar_get_guidance_hint.argtypes = [ctypes.c_void_p]
        self._lib.snoopar_get_guidance_hint.restype = ctypes.c_int32

        self._lib.snoopar_start_close_sweep.argtypes = [ctypes.c_void_p]
        self._lib.snoopar_start_close_sweep.restype = None

        self._lib.snoopar_get_observation_count.argtypes = [ctypes.c_void_p]
        self._lib.snoopar_get_observation_count.restype = ctypes.c_size_t

        self._lib.snoopar_get_estimate.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_float),
            ctypes.POINTER(ctypes.c_float),
            ctypes.POINTER(ctypes.c_float),
            ctypes.POINTER(ctypes.c_float),
        ]
        self._lib.snoopar_get_estimate.restype = ctypes.c_int32

        self._lib.snoopar_get_voxel_cloud.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(SnoopARVoxel),
            ctypes.c_size_t,
        ]
        self._lib.snoopar_get_voxel_cloud.restype = ctypes.c_size_t

    def __del__(self):
        if hasattr(self, "_handle") and self._handle:
            self._lib.snoopar_destroy_engine(self._handle)
            self._handle = None

    def reset(self):
        self._lib.snoopar_reset_engine(self._handle)

    def set_tracker_type(self, tracker_type: int):
        self._lib.snoopar_set_tracker_type(self._handle, ctypes.c_int32(tracker_type))

    def add_observation(
        self,
        obs_x: float, obs_y: float, obs_z: float,
        gaze_x: float, gaze_y: float, gaze_z: float,
        rssi: float,
        timestamp_ns: int = 0
    ):
        self._lib.snoopar_add_observation(
            self._handle,
            ctypes.c_float(obs_x), ctypes.c_float(obs_y), ctypes.c_float(obs_z),
            ctypes.c_float(gaze_x), ctypes.c_float(gaze_y), ctypes.c_float(gaze_z),
            ctypes.c_float(rssi),
            ctypes.c_uint64(timestamp_ns)
        )

    def start_close_sweep(self):
        self._lib.snoopar_start_close_sweep(self._handle)

    def get_search_state(self) -> int:
        return self._lib.snoopar_get_search_state(self._handle)

    def get_guidance_hint(self) -> int:
        return self._lib.snoopar_get_guidance_hint(self._handle)

    def get_observation_count(self) -> int:
        return self._lib.snoopar_get_observation_count(self._handle)

    def get_estimate(self) -> Optional[Tuple[Tuple[float, float, float], float]]:
        x = ctypes.c_float(0.0)
        y = ctypes.c_float(0.0)
        z = ctypes.c_float(0.0)
        rad = ctypes.c_float(0.0)
        ok = self._lib.snoopar_get_estimate(
            self._handle,
            ctypes.byref(x), ctypes.byref(y), ctypes.byref(z),
            ctypes.byref(rad)
        )
        if ok:
            return ((x.value, y.value, z.value), rad.value)
        return None

    def get_voxel_cloud(self, max_count: int = 100) -> List[Tuple[float, float, float, float]]:
        voxel_array = (SnoopARVoxel * max_count)()
        count = self._lib.snoopar_get_voxel_cloud(self._handle, voxel_array, ctypes.c_size_t(max_count))
        result = []
        for i in range(count):
            v = voxel_array[i]
            result.append((v.x, v.y, v.z, v.probability))
        return result
