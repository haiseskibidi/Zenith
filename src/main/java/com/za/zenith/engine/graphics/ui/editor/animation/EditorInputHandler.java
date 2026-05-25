package com.za.zenith.engine.graphics.ui.editor.animation;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.graphics.ui.Hotbar;
import com.za.zenith.engine.graphics.ui.UIRenderer;
import com.za.zenith.engine.graphics.model.ModelNode;
import com.za.zenith.engine.graphics.model.Viewmodel;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemRegistry;
import com.za.zenith.world.items.ItemStack;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class EditorInputHandler {
    
    private final Vector2f lastMousePos = new Vector2f();
    private final TransformController transformController = new TransformController();

    public TransformController.TransformMode getCurrentMode() { return transformController.getCurrentMode(); }
    public char getActiveAxis() { return transformController.getActiveAxis(); }
    public TransformController getTransformController() { return transformController; }

    public void handleMouseClick(float mx, float my, int button, AnimationEditorState state, List<ModelNode> allParts, AnimationEditorRenderer renderer) {
        int sw = GameLoop.getInstance().getWindow().getWidth(), sh = GameLoop.getInstance().getWindow().getHeight();
        
        if (transformController.getCurrentMode() != TransformController.TransformMode.NONE) {
            if (button == 0) { // Confirm
                confirmTransform(state, allParts);
                transformController.setCurrentMode(TransformController.TransformMode.NONE);
                transformController.setActiveAxis(' ');
                transformController.stopDragging();
            } else if (button == 1) { // Cancel
                transformController.cancelTransform(state);
            }
            return;
        }

        Item devItem = getDevItemAt(mx, my, sw, sh);
        if (devItem != null) { state.heldStack = new ItemStack(devItem); return; }

        if (my >= sh - 100) {
            state.isScrubbing = true;
            state.currentTime = Math.max(0, Math.min(1, (mx - 60) / (float)(sw - 120)));
            return;
        }

        if (mx <= 230 && my >= 120 && my <= sh - 100) {
            List<AnimationEditorState.FlatNode> flat = state.getFlatPartList(findRoot(allParts));
            int idx = (int)((my - 130) / 22);
            if (idx >= 0 && idx < flat.size()) {
                state.selectedPart = flat.get(idx).node();
                state.editingProperty = null;
            }
            return;
        }

        if (state.selectedPart != null && mx >= 240 && mx <= 460 && my >= 80 && my <= 460) {
            int px = 240, py = 80;
            int rowIdx = (int)((my - 140) / 22);
            // ... (existing property click logic) ...
            
            // Check IK Buttons
            int ikY = py + 140 + (7 * 22) + 15; // Rough estimate of IK button position
            if (mx >= px + 15 && mx <= px + 205) {
                if (my >= ikY && my <= ikY + 20) {
                    toggleIK(state, allParts);
                    return;
                }
                if (state.ikManager.isEnabled() && my >= ikY + 22 && my <= ikY + 42) {
                    state.ikManager.bakeToKeyframes(state);
                    return;
                }
            }
            state.editingProperty = null;
            return;
        }
        state.editingProperty = null;

        if (transformController.pickGizmo(mx, my, sw, sh, state)) return;
        if (transformController.pickPart3D(mx, my, sw, sh, state, allParts)) return;
    }

    private void applyStep(AnimationEditorState state, String prop, float step, List<ModelNode> allParts) {
        if (state.selectedPart == null) return;
        boolean isCam = state.selectedPart == state.cameraNode;

        float rad = (float)Math.toRadians(step);
        if (isCam) {
            switch (prop) {
                case "Cam X" -> state.selectedPart.animTranslation.x += step;
                case "Cam Y" -> state.selectedPart.animTranslation.y += step;
                case "FOV Off" -> state.selectedPart.animTranslation.z += step;
                case "Cam Tilt" -> state.selectedPart.animRotation.x += rad;
                case "Cam Roll" -> state.selectedPart.animRotation.z += rad;
            }
        } else {
            switch (prop) {
                case "Anim X" -> state.selectedPart.animTranslation.x += step;
                case "Anim Y" -> state.selectedPart.animTranslation.y += step;
                case "Anim Z" -> state.selectedPart.animTranslation.z += step;
                case "Pitch" -> state.selectedPart.animRotation.x += rad;
                case "Yaw" -> state.selectedPart.animRotation.y += rad;
                case "Roll" -> state.selectedPart.animRotation.z += rad;
            }
        }
    }

    private void confirmTransform(AnimationEditorState state, List<ModelNode> allParts) {
        if (state.selectedPart != null) {
            AnimationEditorState.EditorTrack track = state.tracks.computeIfAbsent(state.selectedPart.name, k -> new AnimationEditorState.EditorTrack());
            track.addKey(state.currentTime, state.selectedPart.animTranslation, state.selectedPart.animRotation);
        }
    }

    public void handleMouseMove(float mx, float my, AnimationEditorState state, List<ModelNode> allParts) {
        int sw = GameLoop.getInstance().getWindow().getWidth(), sh = GameLoop.getInstance().getWindow().getHeight();
        if (state.isScrubbing) {
            state.currentTime = Math.max(0, Math.min(1, (mx - 60) / (float)(sw - 120)));
        } else {
            transformController.handleDrag(mx, my, lastMousePos.x, lastMousePos.y, sw, sh, state, allParts);
        }
        lastMousePos.set(mx, my);
    }

    public boolean handleKeyPress(int key, AnimationEditorState state, List<ModelNode> allParts) {
        long window = GameLoop.getInstance().getWindow().getWindowHandle();
        boolean ctrl = glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
        boolean shift = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
        int sw = GameLoop.getInstance().getWindow().getWidth(), sh = GameLoop.getInstance().getWindow().getHeight();

        if (state.editingProperty != null) {
            if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
                try {
                    float val = Float.parseFloat(state.editingValue);
                    applyTypedValue(state, state.editingProperty, val, allParts);
                    confirmTransform(state, allParts);
                } catch (Exception ignored) {}
                state.editingProperty = null;
                return true;
            }
            if (key == GLFW_KEY_BACKSPACE && !state.editingValue.isEmpty()) {
                state.editingValue = state.editingValue.substring(0, state.editingValue.length() - 1);
                return true;
            }
            if (key == GLFW_KEY_ESCAPE) { state.editingProperty = null; return true; }
            if ((key >= GLFW_KEY_0 && key <= GLFW_KEY_9) || (key >= GLFW_KEY_KP_0 && key <= GLFW_KEY_KP_9) 
                || key == GLFW_KEY_PERIOD || key == GLFW_KEY_MINUS || key == GLFW_KEY_KP_DECIMAL || key == GLFW_KEY_KP_SUBTRACT) {
                char c = (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) ? (char)('0' + (key - GLFW_KEY_0)) :
                         (key >= GLFW_KEY_KP_0 && key <= GLFW_KEY_KP_9) ? (char)('0' + (key - GLFW_KEY_KP_0)) :
                         (key == GLFW_KEY_PERIOD || key == GLFW_KEY_KP_DECIMAL) ? '.' : '-';
                state.editingValue += c; return true;
            }
            return true;
        }

        if (key == GLFW_KEY_S && ctrl) { AnimationExporter.export(state, "src/main/resources/zenith/animations/editor_output.json"); return true; }
        if (key == GLFW_KEY_ESCAPE || key == GLFW_KEY_F8) {
            if (transformController.getCurrentMode() != TransformController.TransformMode.NONE) {
                transformController.cancelTransform(state);
                return true;
            }
            return false; // Delegate closing/toggle to GameLoop to prevent double-trigger flashing
        }
        if (key == GLFW_KEY_SPACE) { state.isPlaying = !state.isPlaying; return true; }
        if (key == GLFW_KEY_LEFT) { if (shift) jumpToKeyframe(state, -1); else state.currentTime = Math.max(0, state.currentTime - 0.01f); return true; }
        if (key == GLFW_KEY_RIGHT) { if (shift) jumpToKeyframe(state, 1); else state.currentTime = Math.min(1, state.currentTime + 0.01f); return true; }
        if (key == GLFW_KEY_O) { state.onionSkinning = !state.onionSkinning; return true; }
        if (key == GLFW_KEY_I) { toggleIK(state, allParts); return true; }
        if (key == GLFW_KEY_B && state.ikManager.isEnabled()) { state.ikManager.bakeToKeyframes(state); return true; }

        if (state.selectedPart != null) {
            if (shift) {
                AnimationEditorState.EditorTrack track = state.tracks.get(state.selectedPart.name);
                if (track != null) {
                    if (key == GLFW_KEY_1) { track.easing = AnimationEditorState.EasingType.LINEAR; return true; }
                    if (key == GLFW_KEY_2) { track.easing = AnimationEditorState.EasingType.SINE_IN_OUT; return true; }
                    if (key == GLFW_KEY_3) { track.easing = AnimationEditorState.EasingType.QUAD_IN_OUT; return true; }
                    if (key == GLFW_KEY_4) { track.easing = AnimationEditorState.EasingType.CUBIC_IN_OUT; return true; }
                }
            }
            if (key == GLFW_KEY_G) { transformController.setCurrentMode(TransformController.TransformMode.GRAB); transformController.prepareGrab(state, lastMousePos.x, lastMousePos.y, sw, sh); return true; }
            if (key == GLFW_KEY_R) { 
                transformController.setCurrentMode(TransformController.TransformMode.ROTATE); 
                transformController.setActiveAxis('S'); 
                transformController.prepareGrab(state, lastMousePos.x, lastMousePos.y, sw, sh);
                return true; 
            }
            if (key == GLFW_KEY_K) { confirmTransform(state, allParts); return true; }
            if (key == GLFW_KEY_DELETE) { 
                AnimationEditorState.EditorTrack track = state.tracks.get(state.selectedPart.name); 
                if (track != null) track.removeNear(state.currentTime); 
                return true; 
            }
            if (transformController.getCurrentMode() != TransformController.TransformMode.NONE) {
                if (key == GLFW_KEY_X) { transformController.setActiveAxis('X'); return true; }
                if (key == GLFW_KEY_Y) { transformController.setActiveAxis('Y'); return true; }
                if (key == GLFW_KEY_Z) { transformController.setActiveAxis('Z'); return true; }
            }
        }
        return false;
    }

    private void applyTypedValue(AnimationEditorState state, String prop, float val, List<ModelNode> allParts) {
        if (state.selectedPart == null) return;
        boolean isCam = state.selectedPart == state.cameraNode;

        float rad = (float)Math.toRadians(val);
        if (isCam) {
            switch (prop) {
                case "Cam X" -> state.selectedPart.animTranslation.x = val;
                case "Cam Y" -> state.selectedPart.animTranslation.y = val;
                case "FOV Off" -> state.selectedPart.animTranslation.z = val;
                case "Cam Tilt" -> state.selectedPart.animRotation.x = rad;
                case "Cam Roll" -> state.selectedPart.animRotation.z = rad;
            }
        } else {
            switch (prop) {
                case "Anim X" -> state.selectedPart.animTranslation.x = val;
                case "Anim Y" -> state.selectedPart.animTranslation.y = val;
                case "Anim Z" -> state.selectedPart.animTranslation.z = val;
                case "Pitch" -> state.selectedPart.animRotation.x = rad;
                case "Yaw" -> state.selectedPart.animRotation.y = rad;
                case "Roll" -> state.selectedPart.animRotation.z = rad;
            }
        }
    }

    public void handleMouseRelease(int button, AnimationEditorState state, List<ModelNode> allParts) {
        if (transformController.getCurrentMode() != TransformController.TransformMode.NONE) { 
            confirmTransform(state, allParts); 
            transformController.setCurrentMode(TransformController.TransformMode.NONE); 
            transformController.setActiveAxis(' '); 
            transformController.stopDragging();
        }
    }

    public boolean handleScroll(double yoffset) {
        UIRenderer ui = GameLoop.getInstance().getRenderer().getUIRenderer();
        if (ui.getDevScroller().isMouseOver(lastMousePos.x, lastMousePos.y)) { ui.getDevScroller().handleScroll(yoffset); return true; }
        return false;
    }

    private Item getDevItemAt(float mx, float my, int sw, int sh) {
        UIRenderer ui = GameLoop.getInstance().getRenderer().getUIRenderer();
        if (!ui.getDevScroller().isMouseOver(mx, my)) return null;
        int cols = 7, slotSize = (int)(18 * Hotbar.HOTBAR_SCALE), spacing = (int)(2 * Hotbar.HOTBAR_SCALE);
        int devX = sw - (cols * (slotSize + spacing)) - 25;
        List<Item> items = new ArrayList<>(ItemRegistry.getAllItems().values());
        float off = ui.getDevScroller().getOffset();
        for (int i = 0; i < items.size(); i++) {
            int x = devX + (i % cols) * (slotSize + spacing), y = 40 + (i / cols) * (slotSize + spacing) - (int)off;
            if (my >= 40 && my <= 40 + ui.getDevScroller().getHeight() && mx >= x && mx <= x + slotSize && my >= y && my <= y + slotSize) return items.get(i);
        }
        return null;
    }

    private ModelNode findRoot(List<ModelNode> allParts) {
        for (ModelNode n : allParts) if (n.name.equals("root")) return n;
        return allParts.isEmpty() ? null : allParts.get(0);
    }

    private void jumpToKeyframe(AnimationEditorState state, int direction) {
        float bestTime = direction > 0 ? 1.1f : -0.1f;
        boolean found = false;
        for (AnimationEditorState.EditorTrack track : state.tracks.values()) {
            for (AnimationEditorState.EditorKeyframe k : track.keyframes) {
                if (direction > 0 && k.time() > state.currentTime + 0.005f && k.time() < bestTime) {
                    bestTime = k.time(); found = true;
                } else if (direction < 0 && k.time() < state.currentTime - 0.005f && k.time() > bestTime) {
                    bestTime = k.time(); found = true;
                }
            }
        }
        if (found) state.currentTime = bestTime;
    }

    private void toggleIK(AnimationEditorState state, List<ModelNode> allParts) {
        if (state.ikManager.isEnabled()) {
            state.ikManager.setEnabled(false);
        } else {
            ModelNode root = findRoot(allParts);
            if (root != null) {
                state.ikManager.autoSetup(root);
                if (!state.ikManager.getChains().isEmpty()) {
                    state.ikManager.setEnabled(true);
                }
            }
        }
    }
}


