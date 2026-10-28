import Foundation
import CoreBluetooth
import Combine

public enum TrackerFamily: String, CaseIterable, Identifiable {
    case appleFindMy = "Apple Find My"
    case samsungSmartTag = "Samsung SmartTag"
    case tile = "Tile Tracker"
    case googleFindMy = "Google Find My"
    case genericBeacon = "Generic Beacon"
    
    public var id: String { rawValue }
    
    public var trackerTypeId: Int32 {
        switch self {
        case .appleFindMy: return 0
        case .samsungSmartTag: return 1
        case .tile: return 2
        case .googleFindMy, .genericBeacon: return 3
        }
    }
}

public struct DiscoveredTracker: Identifiable, Equatable {
    public let id: String
    public let family: TrackerFamily
    public let lastRssi: Int
    public let packetCount: Int
    public let lastSeenTimestampNs: UInt64
    
    public static func == (lhs: DiscoveredTracker, rhs: DiscoveredTracker) -> Bool {
        return lhs.id == rhs.id &&
               lhs.lastRssi == rhs.lastRssi &&
               lhs.packetCount == rhs.packetCount
    }
}

public final class TrackerScanner: NSObject, ObservableObject, CBCentralManagerDelegate {
    private var centralManager: CBCentralManager?
    
    @Published public var isScanning: Bool = false
    @Published public var discoveredTrackers: [DiscoveredTracker] = []
    
    public var targetedAddress: String?
    public var onTargetObservationReceived: ((_ id: String, _ rssi: Float, _ timestampNs: UInt64) -> Void)?
    
    private var trackerMap: [String: DiscoveredTracker] = [:]
    private let mapLock = NSLock()
    
    // 16-bit Service UUID constants
    private static let uuidSamsungSmartTag = CBUUID(string: "FD5A")
    private static let uuidGoogleFmdn = CBUUID(string: "FEAA")
    private static let uuidTileFeed = CBUUID(string: "FEED")
    private static let uuidTileFeec = CBUUID(string: "FEEC")
    
    public override init() {
        super.init()
        self.centralManager = CBCentralManager(delegate: self, queue: DispatchQueue.global(qos: .userInitiated))
    }
    
    public func startScanning() {
        guard let central = centralManager, central.state == .poweredOn else { return }
        if isScanning { return }
        
        let options: [String: Any] = [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        central.scanForPeripherals(withServices: nil, options: options)
        DispatchQueue.main.async {
            self.isScanning = true
        }
    }
    
    public func stopScanning() {
        centralManager?.stopScan()
        DispatchQueue.main.async {
            self.isScanning = false
        }
    }
    
    // MARK: - CBCentralManagerDelegate
    
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            startScanning()
        } else {
            stopScanning()
        }
    }
    
    public func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let rssiInt = RSSI.intValue
        if rssiInt > 0 || rssiInt < -100 { return }
        
        guard let family = classify(advertisementData: advertisementData) else { return }
        let deviceId = peripheral.identifier.uuidString
        let nowNs = UInt64(ProcessInfo.processInfo.systemUptime * 1_000_000_000)
        
        mapLock.lock()
        let existing = trackerMap[deviceId]
        let updated = DiscoveredTracker(
            id: deviceId,
            family: family,
            lastRssi: rssiInt,
            packetCount: (existing?.packetCount ?? 0) + 1,
            lastSeenTimestampNs: nowNs
        )
        trackerMap[deviceId] = updated
        let currentList = Array(trackerMap.values)
        mapLock.unlock()
        
        DispatchQueue.main.async {
            self.discoveredTrackers = currentList
        }
        
        if targetedAddress == nil || targetedAddress == deviceId {
            onTargetObservationReceived?(deviceId, Float(rssiInt), nowNs)
        }
    }
    
    private func classify(advertisementData: [String: Any]) -> TrackerFamily? {
        // 1. Apple Find My (Company ID 0x004c, Type 0x12, payload >= 25 bytes)
        if let mfgData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data, mfgData.count >= 27 {
            // Little-endian 0x004c: byte[0]=0x4c, byte[1]=0x00
            if mfgData[0] == 0x4C && mfgData[1] == 0x00 && mfgData[2] == 0x12 {
                return .appleFindMy
            }
        }
        
        // 2. Samsung SmartThings Find (Service UUID FD5A)
        if let serviceData = advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data],
           serviceData[Self.uuidSamsungSmartTag] != nil {
            return .samsungSmartTag
        }
        if let serviceUUIDs = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID],
           serviceUUIDs.contains(Self.uuidSamsungSmartTag) {
            return .samsungSmartTag
        }
        
        // 3. Tile Tracker (FEED or FEEC)
        if let serviceUUIDs = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] {
            if serviceUUIDs.contains(Self.uuidTileFeed) || serviceUUIDs.contains(Self.uuidTileFeec) {
                return .tile
            }
        }
        
        // 4. Google Find My Device Network (FEAA with frame type 0x40/0x41)
        if let serviceData = advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data],
           let feaaData = serviceData[Self.uuidGoogleFmdn], feaaData.count > 0 {
            let frameType = feaaData[0]
            if frameType == 0x40 || frameType == 0x41 {
                return .googleFindMy
            }
        }
        
        return nil
    }
}
