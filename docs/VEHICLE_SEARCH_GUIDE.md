# SnoopAR: Comprehensive Vehicle & Physical Search Guide

> **Tactical & Technical Manual for Locating Concealed Tracking Devices**  
> *Translating 2.4 GHz RF physics and AR volumetric spatial locks into physical discovery.*

---

## 1. Executive Overview

Bluetooth Low Energy (BLE) trackers (Apple AirTags, Samsung Galaxy SmartTags, Tile, and Google Find My Network tags) are engineered for consumer item tracking. However, illicit actors and vehicle thieves frequently repurpose them for unwanted covert tracking.

Standard countermeasures often fail:
1. **Disabled Speakers ("Silent Tags"):** Stalkers routinely drill out or sever the piezoelectric diaphragm inside AirTags. The official "Play Sound" alert emits zero audio.
2. **Owner-Only Precision Finding:** Apple's directional Ultra-Wideband (U1/U2) precision finding is cryptographically locked to the *registered owner*. Victims are completely locked out of directional UWB finding.
3. **Severe 2.4 GHz Multipath:** In car parks, driveways, and garages, 2.4 GHz radio waves ($\lambda \approx 12.3\text{ cm}$) reflect off concrete, tarmac, and car metal, producing standing wave nulls of 15–20 dB across just 6 cm of movement.

**SnoopAR** solves this by fusing millimeter-level Visual-Inertial Odometry (VIO) with multi-hypothesis Bayesian spatial grid estimation to locate the physical tag in 3D space.

---

## 2. RF Physics in Vehicles: Penetration vs Reflection

Understanding vehicle material transparency is critical when interpreting SnoopAR's AR heat clouds:

```
                  [ Vehicle Exterior Structure ]
       
         Plastic Bumper / Valence         Stamped Steel Quarter Panel
     +-------------------------------+   +===========================+
     |   High RF Transparency        |   |   RF Opaque & Reflective  |
     |   Attenuation: < 1.5 dB       |   |   Attenuation: > 35 dB    |
     |   (Radio waves easily escape) |   |   (Acts as Faraday cage)  |
     +-------------------------------+   +===========================+
                    |                                  |
                    v                                  v
       Signals radiate outward             Signals bounce through gaps
      cleanly; easy coarse lock.           (wheel arches, window seals).
```

### High Transparency (Common Exterior Hiding Zones):
* **ABS Plastic / Polypropylene:** Bumpers, diffusers, radiator grilles, side mirror caps, roof antenna sharks, and underbody aerodynamic covers attenuate 2.4 GHz by less than **$1.5\text{ dB}$**. Radio signals escape with minimal loss.
* **Rubber / Fiberglass:** Rubber grommets, splash guards, and fiberglass panels have near-zero attenuation.

### High Shielding / Reflection (Cavities & Faraday Zones):
* **Sheet Metal Bodywork:** Stamped steel panels reflect 2.4 GHz waves. If a tag is placed inside a completely enclosed steel box (e.g., inside an engine compartment or enclosed steel rocker panel), signals only escape through structural seams and holes.
* **Window Glass with Metallic Tint:** Ceramic or metallic sun-blocking tints can add 3–8 dB of attenuation.

---

## 3. Top 10 Concealment Locations on Vehicles

Based on law enforcement and digital safety forensics, these are the primary zones where magnetic or adhesive tracking devices are hidden:

```
                       [ TOP CONCEALMENT ZONES ]

           [10] Front Grille       [5] Wiper Cowl
                   \                   /
  +-----------------\-----------------/--------------------+
  |  [ENG]           v               v                     |
  |             +---------+     +---------+                |
  |   [3]       |  FRONT  |     | CABIN   |        [3]     |
  |  Wheel Well |  SEATS  |     | SEATS   |     Wheel Well |
  |   Liner     +---------+     +---------+        Liner   |
  |      \           |               |            /        |
  |       \       [6] OBD-II      [8] Floor      /         |
  |        \      [7] Fuse Box        Mats      /          |
  |                                            v           |
  |                                        +---------+     |
  |                                        | TRUNK   |     |
  |                                        +---------+     |
  |                                       /     |     \    |
  +--------------------------------------/------|------\---+
                                        /       |       \
                          [1] Rear Bumper   [2] Hitch   [9] Spare Tire
                                Lip          Receiver        Well
```

| Priority | Location | Attachment Method | Search Technique |
| :---: | :--- | :--- | :--- |
| **1** | **Rear Bumper Inner Lip / Diffuser** | 3M tape or zip-tie inside the plastic valence lip | Sweep camera along the underside of the plastic bumper cover. |
| **2** | **Trailer Hitch Receiver Tube** | Neodymium magnet stuck inside 2-inch square tube | Inspect the interior cavity of the hitch receiver using phone flashlight. |
| **3** | **Wheel Well Plastic Liners** | Taped behind flexible wheel arch liner | Point camera into the gap between tire and plastic splash guard. |
| **4** | **Undercarriage Frame Rails** | Heavy-duty magnetic weatherproof box | Look for rectangular black magnetic boxes attached to steel crossmembers. |
| **5** | **Windshield Wiper Cowl Tray** | Placed in the plastic trough beneath wipers | Sweep camera over the black plastic grille at the base of the windshield. |
| **6** | **OBD-II Diagnostic Port** | Plugged directly into port under driver dash | Check OBD port for dongles with internal BLE beacons or SIM cards. |
| **7** | **Cabin / Trunk Fuse Panel** | Tucked into unused fuse slots or relay cavities | Open the fuse door (usually left of steering wheel or in trunk). |
| **8** | **Seat Seams & Carpet Liners** | Pushed deep into seat crevice or under carpet | Run hands along rear seat cushions and under heavy floor mats. |
| **9** | **Spare Tire Well** | Placed under spare tire or jack tool pouch | Lift rear trunk floor board and inspect around the rim of the spare wheel. |
| **10** | **Front Radiator Grille** | Zip-tied to plastic vertical/horizontal slats | Sweep camera across front bumper honeycomb openings. |

---

## 4. Operational Sweep Protocol with SnoopAR

Follow this structured protocol to maximize probability convergence and eliminate multipath nulls:

### Step 1: Perimeter Baseline Walk (Stage 1: Coarse Sweep)
1. Launch **SnoopAR**. Grant permissions and verify camera tracking initializes.
2. If multiple trackers appear in the **Trackers** drawer, tap the suspect tag.
3. Stand approximately **1.5 to 2.0 meters** away from the vehicle.
4. Walk a steady, circular perimeter arc around the entire vehicle holding your phone at chest level, keeping the phone camera angled toward the car.
5. **Listen to the Procedural Geiger Audio:**
   * When facing away or on the wrong side: slow, lazy clicking (1.2–1.5 second intervals).
   * As you walk past the suspect quadrant: clicking cadence dramatically accelerates into a rapid burst.
6. The HUD will indicate: **"CLOSE SWEEP READY"** and the confidence radius will drop below $0.8\text{ m}$.

### Step 2: Surface Near-Field Sweep (Stage 2: Close Sweep)
1. Tap **"Begin Close Sweep"** on the HUD.
2. Step close to the vehicle surface (within **30 to 50 cm**) in the suspect quadrant.
3. Slowly sweep the camera back and forth across the surface (e.g. moving along the bumper seam or wheel arch).
4. **Surface Snapping in Action:** ARCore automatically identifies the nearest physical plane (bumper surface or ground plane) and constrains the 3D voxel grid.
5. Watch the **3D Thermal Probability Cloud**:
   * A dense cluster of orange and red glowing voxels will condense directly onto the physical concealment zone.
   * The reticle will lock with a numeric readout: `Target Locked: < 15 cm`.

### Step 3: Physical Extraction & Evidence Preservation
Once the volumetric lock isolates the position:
1. Use a flashlight or hand inspection behind the plastic panel or lip.
2. **Do NOT touch the tag barehanded!** Tracking tags often retain latent fingerprints of the perpetrator, especially on the inner CR2032 battery contact surface.
3. Wear gloves, place the tag into an RF-blocking Faraday bag (or several layers of thick aluminum foil) to sever live tracking, and document serial numbers for law enforcement.

---

## 5. In-App Empirical Session Recording

To contribute empirical datasets or verify challenging RF environments:
1. Tap the **REC** pill in the top-right corner of the SnoopAR HUD.
2. Walk the sweep as normal. The HUD displays real-time sample counts: `REC (48)`.
3. When the physical tag is found, position the phone directly over the tag and tap **"Pin Tag"**. This marks ground-truth $(X, Y, Z)$ coordinates with millimeter accuracy.
4. Tap **Stop** (`■`). The trace is exported to your device's `Downloads/SnoopAR/` directory as `snoopar_session_YYYYMMDD_HHMMSS.json`.
5. Traces can be replayed offline on desktop using:
   ```bash
   python tools/android-data-logger/scripts/replay_session.py <path_to_session>
   ```
