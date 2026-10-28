# SnoopAR: Project Review

*Review date: October 28, 2026*

This review covers the README, all documents in `docs/`, and `simulation/simulate_snoopar.py`. The accuracy claims were tested by running the existing simulator and a set of additional Monte Carlo experiments (described in [Experiment results](#2-the-limit-is-missing-information-not-the-filter)).

## Summary

The idea is worth building, and the documentation is unusually thorough for a project with no app code yet. However, the central accuracy claim, a 25 cm "target lock" from a short walk, isn't supported by the project's own simulation. The experiments below suggest the physics doesn't allow it as currently designed.

## What's good

- **The problem is real.** Phones can warn you that a tracker is near, but none of them help you find one with its speaker removed. That's a real gap.
- **The core idea is sound.** Combining camera position tracking (ARKit/ARCore) with signal-strength readings turns a jumpy number into a localization problem you can solve. This is better than the usual "hot/cold" meter.
- **The docs cover the right topics:** fading from signal reflections, walking in a straight line, keeping the phone's position and the Bluetooth readings in sync, and when the camera loses tracking.
- **The privacy stance (on-device, no telemetry) fits the people who will use it.**

## Main problems

### 1. The project's own simulation fails, and its output hides that

Running `simulate_snoopar.py` gives an **87 cm error with a 3 m confidence radius**. The script still prints "⚠️ Convergence within acceptable bounds." It uses one fixed seed, Gaussian noise that exactly matches what the filter assumes, and no camera drift. That's the best possible case, and it still misses badly.

### 2. The limit is missing information, not the filter

Each setup below was run with 30 random seeds through the particle filter as specified. For comparison, the exact posterior was also computed on a 10 cm grid (10 seeds). The grid shows the best any algorithm could do with the same data.

| Setup | Median error | Within 25 cm |
|---|---|---|
| Repo setup (walking an arc 1–3 m away, 3.5 dB noise) | 126 cm | 0% of runs |
| Same data, exact grid posterior | 81–107 cm | 2% of probability mass |
| Same, but transmit power A and path-loss exponent n known | 25 cm | 50% of runs |
| Arc, realistic noise (deep fades + body blocking, about 7 dB) | 172 cm | 0% |
| Sweep along the bumper 30 cm away, at two heights, realistic noise | 39 cm | 20% |
| Same, counting each of the 3 advertising channels as a separate reading | 25 cm | 50% |

The last row is optimistic, because it assumes the three channels fade independently.

The "realistic noise" model combines Rayleigh fast fading (in dB) with a 30% chance per reading of 5–12 dB body blocking. Its total standard deviation is about 6.9 dB, while the filter still assumes 4 dB, as it would in the field.

The root cause is that the tracker's transmit power (A) and the path-loss exponent (n) are both unknown. With 1–3 m distances and 4 dB noise, the filter can trade those off against distance almost freely. A perfect filter can't fix that.

Height is also poorly pinned down when the phone stays at waist height. In the repo run, the estimated height was −0.28 m versus a true 0.35 m.

### 3. The feasibility doc overclaims

- It says "HIGHLY FEASIBLE," "centimeter-accurate," and "millimeter-precise trajectory."
- It says "simulations and literature indicate" 12 readings give under 40 cm, but cites nothing, and the repo's own simulation contradicts it.
- It models noise as Gaussian. Real fading is skewed, with deep 10–20 dB drops, and the user's body blocks signal depending on which way they face.
- Around a car, reflections off sheet metal dominate, so the simple log-distance model doesn't describe the signal well.

### 4. The final estimate is the wrong output

The design reduces the particles to one centroid plus an error ellipsoid. RSSI posteriors are often ring-shaped or have mirror-image solutions. The average of a ring lands in the empty middle, so the AR beacon would point at the wrong spot.

### 5. Factual errors in the tracker signatures

These would break the classifier:

- `0xFD6F` is the COVID Exposure Notification service, not Find My. Find My uses Apple manufacturer data (`0x004C`) with type `0x12`.
- `0x07` is AirPods proximity pairing, not offline finding.
- `0xFD5A` is Samsung SmartTag ([source](https://vigil.netslum.io/)), but the architecture doc lists it under Google.
- Google's Find My Device network is believed to use the Eddystone UUID `0xFEAA` with frame types `0x40`/`0x41`. This needs checking against Google's FMDN spec.
- An AirTag separated from its owner keeps its address for about 24 hours, not 15–30 minutes. The 15-minute rotation applies near the owner. This works in the project's favor.
- Android reports the tag's random address, not a hardware MAC.
- [AirTag 2nd generation](https://support.apple.com/en-ca/126203) is missing from the device list.

### 6. Scope problems

- **Smart glasses don't belong here.** They're worn by a person, not hidden.
- **Cellular GPS trackers are invisible to this app.** Many trackers used for car stalking are LTE/2G GPS units that send no Bluetooth at all. The README should say clearly that they're out of scope.
- **Existing tools aren't mentioned.** [AirGuard](https://arxiv.org/abs/2202.11813) (open source, Android and iOS) and Android's built-in unknown-tracker alerts already exist. Positioning against them, with silent and non-standard tags as the niche, would strengthen the pitch.

### 7. The architecture is ahead of the evidence

The plan calls for a Rust core, JNI and Swift bindings, lock-free buffers, "microsecond" sync, and two native apps, all before any real measurement exists. 10,000 particles × 35 updates is trivial even in Python. Microsecond sync doesn't matter when readings arrive every 1–2 seconds with millisecond jitter.

### 8. Smaller simulator issues

- Particle heights are initialized in ±1.5 m, while the algorithm spec says ±R (±3 m).
- A single fixed seed means one lucky or unlucky run decides the result. There are no medians or percentiles.
- The noise model matches the filter's assumption exactly, and there is no camera drift, packet loss, or irregular timing.

## Ideas for development

1. **Record real data first.** Write a throwaway Android logger that saves ARCore pose plus RSSI to CSV. Hide a tag at a measured spot and test the algorithm offline in Python. Treat this as the project's go/no-go test, and only build the full app once real data shows it works.
2. **Reframe the goal as coarse-to-fine search.** The app narrows the search to roughly half a metre to a metre, then guides a close sweep, where RSSI changes steeply and works well. In the experiments, close sweeps cut the error from 126 cm to 25–39 cm.
3. **Use the AR scene as a prior.** Trackers sit on or inside surfaces, not in mid-air. Limiting candidate locations to within about 10 cm of detected surfaces, or to the car's volume, removes most of the wrong answers.
4. **Replace the particle filter with a grid.** A enters the model linearly, so it can be integrated out in closed form. That leaves a 3D grid plus n. A grid is deterministic, handles multiple candidate spots naturally, and avoids particle collapse.
5. **Model the user's body.** The AR pose tells you which way the phone faces, so you know when the user's body is between the phone and a candidate location. Add an attenuation term for that, and use a heavy-tailed likelihood such as Student-t to absorb deep fades.
6. **Guide the user's next move.** Go beyond "step sideways": point the user to the spot that would most reduce uncertainty, including crouching low and holding the phone high.
7. **Get more readings.** On Android, use `CALLBACK_TYPE_ALL_MATCHES` to catch packets on all three advertising channels. Calibrate each phone's RSSI offset once using the user's own tag, and use known transmit powers per tracker type.
8. **Show the full distribution.** Display a heat cloud or the top few candidate spots instead of one orb. Declare a "lock" based on how much probability sits in a small region, not on eigenvalues.
9. **Make the simulator honest.** Run many seeds and report medians and percentiles. Add realistic fading, body blocking, camera drift and packet loss, and fail loudly when the target is missed.
10. **Use the DULT standard.** Trackers that follow the joint Apple/Google DULT (Detecting Unwanted Location Trackers) spec can be made to play a sound by a non-owner over Bluetooth, as AirGuard does. That finds most non-silent tags for free.
11. **Address safety and misuse.** For stalking survivors, removing a tracker can alert the abuser. Link to safety-planning resources and keep the evidence-report feature. The tool can also be misused: car and bike thieves already scan for owners' AirTags to remove them. Acknowledging that up front will help the project's credibility.

## Suggested roadmap changes

| Current | Suggested |
|---|---|
| Phase 1 starts with a Rust core engine | Phase 0: Android data logger + real sweeps with a tag at a known spot |
| Single-seed Python demo | Monte Carlo benchmark with realistic noise, reporting medians and percentiles |
| Particle filter + centroid/ellipsoid | Surface-constrained grid with A integrated out, rendered as a heat cloud |
| 25 cm "target lock" as the headline | Coarse zone (0.5–1 m), then a guided close sweep |
| Rust/FFI and two native apps in parallel | Kotlin app first; shared native core only if profiling shows a need |
