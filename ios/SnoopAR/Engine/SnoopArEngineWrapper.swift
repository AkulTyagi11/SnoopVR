import Foundation

public enum TrackerType: Int32 {
    case airTag = 0
    case smartTag = 1
    case tile = 2
    case generic = 3
}

public enum SearchState: Int32 {
    case coarseSearch = 0
    case readyForCloseSweep = 1
    case closeSweepActive = 2
    case targetLocked = 3
    
    public var description: String {
        switch self {
        case .coarseSearch: return "STAGE 1: COARSE SWEEP"
        case .readyForCloseSweep: return "READY FOR CLOSE SWEEP"
        case .closeSweepActive: return "STAGE 2: CLOSE SURFACE SWEEP"
        case .targetLocked: return "TARGET LOCKED (< 25cm)"
        }
    }
}

public enum GuidanceHint: Int32 {
    case continueSweep = 0
    case stepSideways = 1
    case changeHeight = 2
    case approachSurface = 3
    case targetLocked = 4
    
    public var message: String {
        switch self {
        case .continueSweep: return "Keep sweeping in an arc around the search area."
        case .stepSideways: return "Collinear sweep detected. Step 1 meter sideways."
        case .changeHeight: return "Scan higher or lower to constrain vertical height."
        case .approachSurface: return "Coarse quadrant identified! Move within 30-50 cm."
        case .targetLocked: return "Target locked! Inspect the indicated surface."
        }
    }
}

public struct VoxelPoint: Identifiable {
    public let id = UUID()
    public let x: Float
    public let y: Float
    public let z: Float
    public let probability: Float
}

public struct TargetEstimate {
    public let x: Float
    public let y: Float
    public let z: Float
    public let confidenceRadius: Float
}

public final class SnoopArEngine {
    private var handle: SnoopAREngineHandle?
    
    public init(trackerType: TrackerType = .airTag) {
        self.handle = snoopar_create_engine(trackerType.rawValue)
    }
    
    deinit {
        if let h = handle {
            snoopar_destroy_engine(h)
            handle = nil
        }
    }
    
    public func reset() {
        guard let h = handle else { return }
        snoopar_reset_engine(h)
    }
    
    public func setTrackerType(_ type: TrackerType) {
        guard let h = handle else { return }
        snoopar_set_tracker_type(h, type.rawValue)
    }
    
    public func addObservation(
        obsX: Float, obsY: Float, obsZ: Float,
        gazeX: Float, gazeY: Float, gazeZ: Float,
        rssi: Float, timestampNs: UInt64
    ) {
        guard let h = handle else { return }
        snoopar_add_observation(h, obsX, obsY, obsZ, gazeX, gazeY, gazeZ, rssi, timestampNs)
    }
    
    public func getSearchState() -> SearchState {
        guard let h = handle else { return .coarseSearch }
        let raw = snoopar_get_search_state(h)
        return SearchState(rawValue: raw) ?? .coarseSearch
    }
    
    public func getGuidanceHint() -> GuidanceHint {
        guard let h = handle else { return .continueSweep }
        let raw = snoopar_get_guidance_hint(h)
        return GuidanceHint(rawValue: raw) ?? .continueSweep
    }
    
    public func startCloseSweep() {
        guard let h = handle else { return }
        snoopar_start_close_sweep(h)
    }
    
    public func getObservationCount() -> UInt64 {
        guard let h = handle else { return 0 }
        return snoopar_get_observation_count(h)
    }
    
    public func getEstimate() -> TargetEstimate? {
        guard let h = handle else { return nil }
        var x: Float = 0
        var y: Float = 0
        var z: Float = 0
        var radius: Float = 0
        let ok = snoopar_get_estimate(h, &x, &y, &z, &radius)
        if ok == 0 { return nil }
        return TargetEstimate(x: x, y: y, z: z, confidenceRadius: radius)
    }
    
    public func getVoxelCloud(maxCount: Int = 80) -> [VoxelPoint] {
        guard let h = handle, maxCount > 0 else { return [] }
        var rawVoxels = [SnoopARVoxel](repeating: SnoopARVoxel(x: 0, y: 0, z: 0, probability: 0), count: maxCount)
        let count = snoopar_get_voxel_cloud(h, &rawVoxels, maxCount)
        guard count > 0 else { return [] }
        
        return (0..<count).map { i in
            VoxelPoint(
                x: rawVoxels[i].x,
                y: rawVoxels[i].y,
                z: rawVoxels[i].z,
                probability: rawVoxels[i].probability
            )
        }
    }
}
