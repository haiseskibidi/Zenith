# Zenith Animation 2.0: Quaternion & Modular Engine

## 1. Vision
Implement a professional-grade animation system similar to modern game engines (and Blender) that uses Quaternions for rotations. This solves the "propeller effect," gimbal lock, and interpolation artifacts associated with Euler angles.

## 2. Core Principles
- **Quaternions Everywhere**: Internal rotations stored and calculated as `org.joml.Quaternionf`.
- **Hybrid Support**: Support for both Legacy (V1, Euler) and Modern (V2, Quaternion) via a `"version": 2` flag in JSON.
- **Intuitive Coordinate System (V2)**: 
    - `+X`: Right
    - `+Y`: Up
    - `+Z`: Forward (into the screen)
- **Modular & Additive**: Animations should be able to layer (Locomotion + Pose + Recoil) without overwriting each other.
- **State Cross-fading**: Smooth 200ms-300ms transitions when switching items or animation states.

## 3. Architectural Changes

### 3.1 ModelNode.java (The Core)
- Replace `animRotation (Vector3f)` with `animRotation (Quaternionf)`.
- Replace `baseRotation (Vector3f)` with `baseRotation (Quaternionf)`.
- Update `updateHierarchy()`:
    - `globalRotation = parentRotation * baseRotation * animRotation` (Quaternion multiplication).
    - `globalMatrix = parentMatrix * translationMatrix * rotationMatrix`.

### 3.2 ViewmodelController.java (The Logic)
- **Additive evaluation**: All `evaluate` calls must use `+=` for positions and `quat.mul()` for rotations.
- **V2 Parsing**: If `version == 2`, read `[X, Y, Z]` as degrees, convert to a temporary quaternion, and multiply it into the node's state.
- **Slerp Support**: Implement Spherical Linear Interpolation for smooth, shortest-path bone transitions.

### 3.3 Player.java (The Orchestrator)
- **Transition Buffer**: Store the "Last Known State" of the entire viewmodel (all nodes' transforms) when an item/state change occurs.
- **Cross-fade Timer**: Interpolate between "Last Known State" and "Current Animation State" over a short duration.
- **Unified Physics**: Pass the finalized, lerped root matrix to the physical spring system.

## 4. Implementation Steps

### Phase 1: Foundation (Current Focus)
1.  **Refactor `ModelNode`**: Transition to Quaternions while keeping Euler methods for V1 compatibility.
2.  **Update `ViewmodelController`**: Implement modular additive logic and V2 coordinate mapping.
3.  **Refactor `ViewmodelMeshGenerator`**: Ensure geometry is generated correctly for the new `+Z = Forward` standard.

### Phase 2: Flow & Switching
1.  **Implement `AnimationState` Snapshotting**: Logic to capture the current frame of all bones.
2.  **Cross-fade Logic**: Modular blending system in `Player.java`.

### Phase 3: Content Migration
1.  **Rebuild `hands.json` (V2)**: Clean anatomy using intuitive coordinates.
2.  **Update Auto-Grip**: Generate Quaternion-based grips instead of Euler angles.

## 5. Editor Compatibility
- The system will expose `getRotationQuaternion()` and `setRotationQuaternion()` methods.
- The editor will be able to manipulate bones in world space and convert to local quaternions automatically, ensuring what you see in the editor is exactly what you get in-game.
