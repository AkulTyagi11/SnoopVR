import Foundation
import ARKit
import Combine

public struct CameraPoseSample {
    public let timestampNs: UInt64
    public let x: Float
    public let y: Float
    public let z: Float
    public let gazeX: Float
    public let gazeY: Float
    public let gazeZ: Float
}

public final class ArKitController: NSObject, ObservableObject, ARSessionDelegate {
    public let session = ARSession()
    
    @Published public var trackingStateDescription: String = "Initializing..."
    @Published public var isTrackingNormal: Bool = false
    
    private var poseHistory: [CameraPoseSample] = []
    private let historyLock = NSLock()
    private let maxHistoryNanos: UInt64 = 2_000_000_000 // 2 seconds
    
    public var currentPose: CameraPoseSample? {
        historyLock.lock()
        defer { historyLock.unlock() }
        return poseHistory.last
    }
    
    public override init() {
        super.init()
        session.delegate = self
    }
    
    public func startSession() {
        guard ARWorldTrackingConfiguration.isSupported else {
            trackingStateDescription = "ARKit not supported on this device"
            return
        }
        
        let configuration = ARWorldTrackingConfiguration()
        configuration.worldAlignment = .gravity
        configuration.planeDetection = [.horizontal, .vertical]
        
        if ARWorldTrackingConfiguration.supportsSceneReconstruction(.mesh) {
            configuration.sceneReconstruction = .mesh
        }
        
        session.run(configuration, options: [.resetTracking, .removeExistingAnchors])
    }
    
    public func pauseSession() {
        session.pause()
        historyLock.lock()
        poseHistory.removeAll()
        historyLock.unlock()
    }
    
    // MARK: - ARSessionDelegate
    
    public func session(_ session: ARSession, didUpdate frame: ARFrame) {
        let camera = frame.camera
        
        switch camera.trackingState {
        case .normal:
            trackingStateDescription = "Tracking Normal"
            isTrackingNormal = true
        case .limited(let reason):
            isTrackingNormal = false
            switch reason {
            case .excessiveMotion:
                trackingStateDescription = "Limited: Slow down movement"
            case .insufficientFeatures:
                trackingStateDescription = "Limited: Low visual texture"
            case .initializing:
                trackingStateDescription = "Initializing AR..."
            case .relocalizing:
                trackingStateDescription = "Relocalizing..."
            @unknown default:
                trackingStateDescription = "Tracking Limited"
            }
        case .notAvailable:
            trackingStateDescription = "Tracking Unavailable"
            isTrackingNormal = false
        }
        
        guard camera.trackingState == .normal else { return }
        
        let transform = camera.transform
        let posX = transform.columns.3.x
        let posY = transform.columns.3.y
        let posZ = transform.columns.3.z
        
        // ARKit camera looks down its local -Z axis. Column 2 is local +Z.
        let gazeX = -transform.columns.2.x
        let gazeY = -transform.columns.2.y
        let gazeZ = -transform.columns.2.z
        
        // frame.timestamp is in seconds (monotonic system time base)
        let nowNs = UInt64(frame.timestamp * 1_000_000_000)
        
        let sample = CameraPoseSample(
            timestampNs: nowNs,
            x: posX, y: posY, z: posZ,
            gazeX: gazeX, gazeY: gazeY, gazeZ: gazeZ
        )
        
        historyLock.lock()
        poseHistory.append(sample)
        while let first = poseHistory.first, (nowNs - first.timestampNs) > maxHistoryNanos {
            poseHistory.removeFirst()
        }
        historyLock.unlock()
    }
    
    public func interpolatePoseAt(timestampNs: UInt64) -> CameraPoseSample? {
        historyLock.lock()
        let history = poseHistory
        historyLock.unlock()
        
        if history.isEmpty { return nil }
        if history.count == 1 { return history[0] }
        
        for i in 0..<(history.count - 1) {
            let p0 = history[i]
            let p1 = history[i + 1]
            if timestampNs >= p0.timestampNs && timestampNs <= p1.timestampNs {
                let dt = Float(p1.timestampNs - p0.timestampNs)
                if dt < 1e-4 { return p1 }
                let alpha = Float(timestampNs - p0.timestampNs) / dt
                return CameraPoseSample(
                    timestampNs: timestampNs,
                    x: p0.x + alpha * (p1.x - p0.x),
                    y: p0.y + alpha * (p1.y - p0.y),
                    z: p0.z + alpha * (p1.z - p0.z),
                    gazeX: p0.gazeX + alpha * (p1.gazeX - p0.gazeX),
                    gazeY: p0.gazeY + alpha * (p1.gazeY - p0.gazeY),
                    gazeZ: p0.gazeZ + alpha * (p1.gazeZ - p0.gazeZ)
                )
            }
        }
        return history.last
    }
}
