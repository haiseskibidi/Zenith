package com.za.zenith.engine.graphics.ui;

import com.za.zenith.engine.graphics.DynamicTextureAtlas;

/**
 * Base interface for all GUI screens.
 */
public interface Screen {
    /**
     * Called when the screen is opened or resized.
     */
    void init(int screenWidth, int screenHeight);

    /**
     * Renders the screen.
     */
    void render(UIRenderer renderer, int screenWidth, int screenHeight, DynamicTextureAtlas atlas);

    /**
     * Handles mouse clicks.
     * @return true if the click was consumed.
     */
    default boolean handleMouseClick(float mx, float my, int button) {
        return false;
    }

    /**
     * Handles mouse releases.
     * @return true if the release was consumed.
     */
    default boolean handleMouseRelease(float mx, float my, int button) {
        return false;
    }

    /**
     * Handles mouse movement.
     */
    default void handleMouseMove(float mx, float my) {
    }

    /**
     * Handles key presses.
     * @return true if the key press was consumed.
     */
    default boolean handleKeyPress(int key) {
        return false;
    }

    /**
     * Handles character input (typing).
     * @return true if the input was consumed.
     */
    default boolean handleChar(int codepoint) {
        return false;
    }

    /**
     * Handles mouse scroll.
     * @return true if the scroll was consumed.
     */
    default boolean handleScroll(double yoffset) {
        return false;
    }

    /**
     * @return true if this screen replaces the entire game scene (e.g. Animation Editor).
     */
    default boolean isScene() {
        return false;
    }

    /**
     * @return true if the screen has an active input field focused (e.g. search bar).
     */
    default boolean isInputFocused() {
        return false;
    }
}


