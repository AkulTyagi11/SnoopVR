import Foundation
import ARKit
import SceneKit
import UIKit

public final class VoxelSceneRenderer: NSObject, ARSCNViewDelegate {
    public weak var sceneView: ARSCNView?
    
    private var breadcrumbParentNode = SCNNode()
    private var voxelCloudParentNode = SCNNode()
    private var targetLockNode: SCNNode?
    
    private var breadcrumbs: [SCNVector3] = []
    private static let maxBreadcrumbs = 250
    
    public override init() {
        super.init()
    }
    
    public func setup(sceneView: ARSCNView) {
        self.sceneView = sceneView
        sceneView.delegate = self
        sceneView.autoenablesDefaultLighting = true
        
        sceneView.scene.rootNode.addChildNode(breadcrumbParentNode)
        sceneView.scene.rootNode.addChildNode(voxelCloudParentNode)
    }
    
    public func addBreadcrumb(x: Float, y: Float, z: Float, rssi: Float) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            let pos = SCNVector3(x, y, z)
            let sphere = SCNSphere(radius: 0.018)
            
            // Normalize RSSI from [-85, -55] to [0.0, 1.0]
            let normRssi = max(0.0, min(1.0, (rssi - (-85.0)) / 30.0))
            let color = self.colorForNormalizedRssi(normRssi)
            
            let material = SCNMaterial()
            material.diffuse.contents = color
            material.emission.contents = color.withAlphaComponent(0.4)
            sphere.materials = [material]
            
            let node = SCNNode(geometry: sphere)
            node.position = pos
            
            self.breadcrumbParentNode.addChildNode(node)
            self.breadcrumbs.append(pos)
            
            if self.breadcrumbs.count > Self.maxBreadcrumbs {
                self.breadcrumbParentNode.childNodes.first?.removeFromParentNode()
                self.breadcrumbs.removeFirst()
            }
        }
    }
    
    public func updateVoxelCloud(voxels: [VoxelPoint]) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            self.voxelCloudParentNode.childNodes.forEach { $0.removeFromParentNode() }
            
            for v in voxels {
                let size = CGFloat(max(0.02, 0.08 * v.probability))
                let box = SCNBox(width: size, height: size, length: size, chamferRadius: size * 0.2)
                
                let material = SCNMaterial()
                // Thermal color: Golden Amber to Red
                let color = UIColor(
                    red: 1.0,
                    green: CGFloat(0.7 * (1.0 - v.probability)),
                    blue: 0.1,
                    alpha: CGFloat(0.35 * v.probability + 0.1)
                )
                material.diffuse.contents = color
                material.emission.contents = color
                material.isDoubleSided = true
                material.blendMode = .add
                box.materials = [material]
                
                let node = SCNNode(geometry: box)
                node.position = SCNVector3(v.x, v.y, v.z)
                self.voxelCloudParentNode.addChildNode(node)
            }
        }
    }
    
    public func updateTargetLock(estimate: TargetEstimate?) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            self.targetLockNode?.removeFromParentNode()
            self.targetLockNode = nil
            
            guard let est = estimate else { return }
            
            let radius = CGFloat(max(0.05, est.confidenceRadius))
            let sphere = SCNSphere(radius: radius)
            let material = SCNMaterial()
            material.diffuse.contents = UIColor(red: 0.2, green: 0.8, blue: 0.3, alpha: 0.25)
            material.emission.contents = UIColor(red: 0.2, green: 0.8, blue: 0.3, alpha: 0.2)
            material.fillMode = .lines
            sphere.materials = [material]
            
            let node = SCNNode(geometry: sphere)
            node.position = SCNVector3(est.x, est.y, est.z)
            self.sceneView?.scene.rootNode.addChildNode(node)
            self.targetLockNode = node
        }
    }
    
    public func clearAll() {
        DispatchQueue.main.async { [weak self] in
            self?.breadcrumbParentNode.childNodes.forEach { $0.removeFromParentNode() }
            self?.voxelCloudParentNode.childNodes.forEach { $0.removeFromParentNode() }
            self?.targetLockNode?.removeFromParentNode()
            self?.targetLockNode = nil
            self?.breadcrumbs.removeAll()
        }
    }
    
    private func colorForNormalizedRssi(_ norm: Float) -> UIColor {
        if norm < 0.5 {
            // Blue to Yellow
            let t = CGFloat(norm * 2.0)
            return UIColor(
                red: 0.2 + 0.8 * t,
                green: 0.5 + 0.4 * t,
                blue: 1.0 - 0.8 * t,
                alpha: 0.85
            )
        } else {
            // Yellow to Red
            let t = CGFloat((norm - 0.5) * 2.0)
            return UIColor(
                red: 1.0,
                green: 0.9 - 0.7 * t,
                blue: 0.2 - 0.1 * t,
                alpha: 0.85
            )
        }
    }
}
