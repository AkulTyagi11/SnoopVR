import SwiftUI
import ARKit
import SceneKit

public struct ContentView: View {
    @StateObject private var arController = ArKitController()
    @StateObject private var bleScanner = TrackerScanner()
    
    private let engine = SnoopArEngine(trackerType: .airTag)
    private let sceneRenderer = VoxelSceneRenderer()
    
    // UI State
    @State private var searchState: SearchState = .coarseSearch
    @State private var guidanceHint: GuidanceHint = .continueSweep
    @State private var observationCount: UInt64 = 0
    @State private var latestRssi: Float = -100.0
    @State private var targetName: String = "Scanning for trackers..."
    @State private var confidenceRadius: Float? = nil
    @State private var targetBearing: TargetBearing? = nil
    @State private var selectedAddress: String? = nil
    @State private var showTrackerSheet: Bool = false
    
    private let hapticGenerator = UIImpactFeedbackGenerator(style: .medium)
    
    public init() {}
    
    public var body: some View {
        ZStack {
            // 1. AR Camera Viewport
            ARSceneViewContainer(arController: arController, sceneRenderer: sceneRenderer)
                .edgesIgnoringSafeArea(.all)
            
            // 2. HUD Overlay
            VStack {
                // Top Stage & Guidance
                VStack(spacing: 8) {
                    // Stage Pill
                    HStack(spacing: 6) {
                        Image(systemName: searchState == .targetLocked ? "lock.fill" : "location.viewfinder")
                            .font(.system(size: 13, weight: .bold))
                        Text(searchState.description)
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                    .background(stageColor(for: searchState))
                    .cornerRadius(20)
                    
                    // Guidance Message Banner
                    HStack(spacing: 8) {
                        Image(systemName: "info.circle.fill")
                            .foregroundColor(Color.blue)
                        Text(guidanceHint.message)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.white)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity)
                    .background(Color.black.opacity(0.75))
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                    
                    // Directional Target Bearing Indicator
                    if let bearing = targetBearing, bearing.distanceM > 0.05 {
                        let isLeft = bearing.azimuthDeg < -25.0
                        let isRight = bearing.azimuthDeg > 25.0
                        let absAzimuth = Int(abs(bearing.azimuthDeg))
                        let distStr = String(format: "%.1fm", bearing.distanceM)
                        let navText = isLeft ? "← Turn Left \(absAzimuth)° (\(distStr))" :
                                     (isRight ? "Turn Right \(absAzimuth)° → (\(distStr))" : "Target Ahead (\(distStr))")
                        
                        HStack(spacing: 6) {
                            Image(systemName: "location.north.line.fill")
                                .font(.system(size: 11, weight: .bold))
                            Text(navText)
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background((isLeft || isRight) ? Color.cyan.opacity(0.85) : Color.green.opacity(0.85))
                        .cornerRadius(16)
                    }
                }
                .padding(.top, 12)
                
                Spacer()
                
                // Bottom Telemetry & Controls
                VStack(spacing: 12) {
                    VStack(spacing: 10) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(targetName)
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                    
                                    Button(action: { showTrackerSheet = true }) {
                                        Text("\(bleScanner.discoveredTrackers.count) found")
                                            .font(.system(size: 10, weight: .semibold))
                                            .foregroundColor(.blue)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color.blue.opacity(0.2))
                                            .cornerRadius(8)
                                    }
                                }
                                
                                Text("Samples: \(observationCount) | Rad: \(formattedRadius(confidenceRadius))")
                                    .font(.system(size: 12, design: .monospaced))
                                    .foregroundColor(.gray)
                            }
                            
                            Spacer()
                            
                            Text(latestRssi > -99.0 ? String(format: "%.0f dBm", latestRssi) : "--")
                                .font(.system(size: 20, weight: .bold, design: .monospaced))
                                .foregroundColor(rssiColor(for: latestRssi))
                        }
                        
                        Divider().background(Color.white.opacity(0.15))
                        
                        HStack(spacing: 10) {
                            Button(action: { showTrackerSheet = true }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "antenna.radiowaves.left.and.right")
                                    Text("Trackers")
                                }
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(Color.white.opacity(0.15))
                                .cornerRadius(8)
                            }
                            
                            if searchState == .readyForCloseSweep || searchState == .coarseSearch {
                                Button(action: startCloseSweep) {
                                    Text("Begin Close Sweep")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 8)
                                        .background(Color.orange)
                                        .cornerRadius(8)
                                }
                            }
                            
                            Button(action: resetSearch) {
                                Image(systemName: "arrow.clockwise")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(8)
                                    .background(Color.white.opacity(0.15))
                                    .cornerRadius(8)
                            }
                        }
                    }
                    .padding(16)
                    .background(Color(red: 0.1, green: 0.1, blue: 0.1).opacity(0.88))
                    .cornerRadius(16)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
        }
        .sheet(isPresented: $showTrackerSheet) {
            SuspectTrackerSheet(
                trackers: bleScanner.discoveredTrackers,
                selectedAddress: selectedAddress,
                onSelect: selectTracker
            )
        }
        .onAppear {
            setupTrackingPipelines()
        }
        .onDisappear {
            arController.pauseSession()
            bleScanner.stopScanning()
        }
    }
    
    private func setupTrackingPipelines() {
        arController.startSession()
        bleScanner.startScanning()
        
        bleScanner.onTargetObservationReceived = { (id, rssi, timestampNs) in
            guard let pose = arController.interpolatePoseAt(timestampNs: timestampNs) else { return }
            
            DispatchQueue.global(qos: .userInitiated).async {
                engine.addObservation(
                    obsX: pose.x, obsY: pose.y, obsZ: pose.z,
                    gazeX: pose.gazeX, gazeY: pose.gazeY, gazeZ: pose.gazeZ,
                    rssi: rssi, timestampNs: timestampNs
                )
                
                sceneRenderer.addBreadcrumb(x: pose.x, y: pose.y, z: pose.z, rssi: rssi)
                
                let voxels = engine.getVoxelCloud(maxCount: 80)
                sceneRenderer.updateVoxelCloud(voxels: voxels)
                
                let estimate = engine.getEstimate()
                sceneRenderer.updateTargetLock(estimate: estimate)
                
                let bearing = engine.getBearingToTarget(
                    camX: pose.x, camY: pose.y, camZ: pose.z,
                    gazeX: pose.gazeX, gazeY: pose.gazeY, gazeZ: pose.gazeZ
                )
                
                let count = engine.getObservationCount()
                let state = engine.getSearchState()
                let hint = engine.getGuidanceHint()
                
                DispatchQueue.main.async {
                    self.observationCount = count
                    self.latestRssi = rssi
                    self.searchState = state
                    self.guidanceHint = hint
                    self.confidenceRadius = estimate?.confidenceRadius
                    self.targetBearing = bearing
                    
                    if state == .targetLocked {
                        hapticGenerator.impactOccurred()
                    }
                }
            }
        }
    }
    
    private func selectTracker(_ tracker: DiscoveredTracker) {
        selectedAddress = tracker.id
        targetName = "\(tracker.family.rawValue) (\(String(tracker.id.suffix(5))))"
        engine.setTrackerType(TrackerType(rawValue: tracker.family.trackerTypeId) ?? .airTag)
        bleScanner.targetedAddress = tracker.id
        resetSearch()
        showTrackerSheet = false
    }
    
    private func startCloseSweep() {
        engine.startCloseSweep()
        searchState = .closeSweepActive
        guidanceHint = .approachSurface
    }
    
    private func resetSearch() {
        engine.reset()
        sceneRenderer.clearAll()
        observationCount = 0
        searchState = .coarseSearch
        guidanceHint = .continueSweep
        confidenceRadius = nil
        targetBearing = nil
    }
    
    private func stageColor(for state: SearchState) -> Color {
        switch state {
        case .coarseSearch: return Color.blue.opacity(0.85)
        case .readyForCloseSweep: return Color.orange.opacity(0.85)
        case .closeSweepActive: return Color.red.opacity(0.85)
        case .targetLocked: return Color.green.opacity(0.85)
        }
    }
    
    private func rssiColor(for rssi: Float) -> Color {
        if rssi >= -65.0 { return .green }
        if rssi >= -75.0 { return .orange }
        return .red
    }
    
    private func formattedRadius(_ r: Float?) -> String {
        guard let radius = r else { return "--" }
        return String(format: "%.1f cm", radius * 100.0)
    }
}

public struct ARSceneViewContainer: UIViewRepresentable {
    public let arController: ArKitController
    public let sceneRenderer: VoxelSceneRenderer
    
    public func makeUIView(context: Context) -> ARSCNView {
        let scnView = ARSCNView(frame: .zero)
        scnView.session = arController.session
        sceneRenderer.setup(sceneView: scnView)
        return scnView
    }
    
    public func updateUIView(_ uiView: ARSCNView, context: Context) {}
}

public struct SuspectTrackerSheet: View {
    public let trackers: [DiscoveredTracker]
    public let selectedAddress: String?
    public let onSelect: (DiscoveredTracker) -> Void
    @Environment(\.presentationMode) var presentationMode
    
    public var body: some View {
        NavigationView {
            List(trackers) { tracker in
                let isSelected = tracker.id == selectedAddress
                Button(action: { onSelect(tracker) }) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tracker.family.rawValue)
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(isSelected ? .blue : .primary)
                            Text("\(tracker.id) | \(tracker.packetCount) pkts")
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundColor(.gray)
                        }
                        Spacer()
                        Text("\(tracker.lastRssi) dBm")
                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                            .foregroundColor(tracker.lastRssi >= -65 ? .green : (tracker.lastRssi >= -75 ? .orange : .red))
                        if isSelected {
                            Image(systemName: "checkmark")
                                .foregroundColor(.blue)
                        }
                    }
                }
            }
            .navigationTitle("Suspect Trackers (\(trackers.count))")
            .navigationBarItems(trailing: Button("Done") {
                presentationMode.wrappedValue.dismiss()
            })
        }
    }
}
