package com.za.zenith.engine.core;

import com.za.zenith.engine.graphics.Camera;
import com.za.zenith.engine.graphics.Renderer;
import com.za.zenith.engine.graphics.ui.Hotbar;
import com.za.zenith.engine.graphics.ui.editor.animation.AnimationEditorScreen;
import com.za.zenith.engine.graphics.ui.DevInspectorScreen;
import com.za.zenith.engine.input.InputManager;
import com.za.zenith.engine.input.InputAction;
import com.za.zenith.entities.Player;
import com.za.zenith.network.GameClient;
import com.za.zenith.network.GameServer;
import com.za.zenith.world.World;
import com.za.zenith.world.physics.RaycastResult;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class GameLoop {
    private static GameLoop instance;
    private static final int TARGET_FPS = 300;
    private static final int TARGET_UPS = 170;
    
    private Window window;
    private Timer timer;
    private Camera camera;
    private InputManager inputManager;
    private Renderer renderer;
    private World world;
    private Player player;
    private Hotbar hotbar;
    private boolean running;
    private boolean paused = false;
    private boolean inventoryOpen = false;
    private float screenToggleCooldown = 0.0f;
    
    private GameMode gameMode;
    private GameServer localServer;
    private GameClient networkClient;
    private String playerName;
    private String serverAddress;
    
    private int fpsCounter = 0;
    private float currentFps = 0;
    private float fpsTimer = 0;
    private RaycastResult highlightedBlock;
    private com.za.zenith.world.recipes.NappingSession currentNappingSession = null;
    
    public GameLoop() {
        instance = this;
    }
    
    public static GameLoop getInstance() {
        return instance;
    }
    
    public Player getPlayer() { return player; }
    public World getWorld() { return world; }
    public Camera getCamera() { return camera; }
    public Timer getTimer() { return timer; }
    public InputManager getInputManager() { return inputManager; }
    public Renderer getRenderer() { return renderer; }
    public com.za.zenith.world.physics.RaycastResult getHighlightedBlock() { return highlightedBlock; }
    public void setInventoryOpen(boolean open) { this.inventoryOpen = open; }

    public void runSingleplayer() { runWithMode(GameMode.SINGLEPLAYER, "Player", null); }
    public void runAsHost(String name) { runWithMode(GameMode.MULTIPLAYER_HOST, name, null); }
    public void runAsClient(String name, String address) { runWithMode(GameMode.MULTIPLAYER_CLIENT, name, address); }
    
    private void runWithMode(GameMode mode, String name, String address) {
        this.gameMode = mode;
        this.playerName = name;
        this.serverAddress = address;
        init();
        loop();
        cleanup();
    }
    
    private void init() {
        SettingsManager.getInstance().load();
        com.za.zenith.world.DataLoader.loadAll();
        window = new Window("Protocol: Grounding", 1280, 720, SettingsManager.getInstance().isVsync());
        window.init();
        timer = new Timer();
        camera = new Camera(new Vector3f(8, 65, 8));
        camera.updateAspectRatio(window.getAspectRatio());
        inputManager = new InputManager();
        renderer = new Renderer();
        inputManager.init(window);
        renderer.init(window.getWidth(), window.getHeight());
        
        long seed = 12345;
        if (gameMode == GameMode.MULTIPLAYER_HOST) {
            localServer = new GameServer(seed);
            localServer.start();
        }
        world = new World(seed);
        world.addUnloadListener(renderer::onChunkUnload);
        player = new Player(new Vector3f(8, 150, 8));
        world.setPlayer(player);
        player.getInventory().setStackInSlot(0, new com.za.zenith.world.items.ItemStack(com.za.zenith.world.items.Items.ADMIN_HAMMER));
        hotbar = new Hotbar(player);
        renderer.setHotbar(hotbar);
        
        if (gameMode != GameMode.SINGLEPLAYER) {
            String ip = (gameMode == GameMode.MULTIPLAYER_HOST) ? "localhost" : serverAddress;
            networkClient = new GameClient(world, player, camera, playerName);
            networkClient.connect(ip);
        }
        running = true;
    }
    
    private void loop() {
        float interval = 1f / TARGET_UPS;
        float accumulator = 0f;
        
        while (running && !window.shouldClose()) {
            timer.updateDelta();
            float elapsedTime = timer.getDeltaF();
            accumulator += elapsedTime;
            
            while (accumulator >= interval) {
                update(interval); // Fixed Physics Update
                accumulator -= interval;
            }
            
            // Calculate interpolation alpha [0..1]
            float alpha = accumulator / interval;
            
            input(elapsedTime);
            render(alpha, elapsedTime); // Render with interpolation and deltaTime
            
            sync(elapsedTime);
        }
    }
    
    private void input(float deltaTime) {
        screenToggleCooldown = Math.max(0, screenToggleCooldown - deltaTime);
        com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
        
        // F8 - Editor Toggle
        if (inputManager.wasJustPressed(InputAction.EDITOR_TOGGLE) && !paused && screenToggleCooldown <= 0 && !inputManager.isKeyHandled(SettingsManager.getInstance().getKeyCode("editor_toggle"))) {
            if (active instanceof AnimationEditorScreen) {
                toggleAnimationEditor();
                screenToggleCooldown = 0.25f;
            } else if (active == null) {
                toggleAnimationEditor();
                screenToggleCooldown = 0.25f;
            }
        }

        // F9 - Live Inspector
        if (inputManager.wasJustPressed(InputAction.LIVE_INSPECTOR) && SettingsManager.getInstance().isDevMode() && screenToggleCooldown <= 0) {
            if (active instanceof DevInspectorScreen) {
                com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().closeScreen();
                inputManager.enableMouseCapture(window);
                screenToggleCooldown = 0.25f;
            } else if (active == null) {
                inputManager.disableMouseCapture(window);
                com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().openScreen(new com.za.zenith.engine.graphics.ui.DevInspectorScreen(), window.getWidth(), window.getHeight());
                screenToggleCooldown = 0.25f;
            }
        }

        // J - Journal Toggle
        if (inputManager.wasJustPressed(InputAction.JOURNAL) && !paused && screenToggleCooldown <= 0 && !inputManager.isKeyHandled(SettingsManager.getInstance().getKeyCode("journal"))) {
            if (active instanceof com.za.zenith.engine.graphics.ui.JournalScreen) {
                toggleJournal();
                screenToggleCooldown = 0.25f;
            } else if (active == null) {
                toggleJournal();
                screenToggleCooldown = 0.25f;
            }
        }

        // E - Inventory Toggle
        if (inputManager.wasJustPressed(InputAction.INVENTORY) && !paused && screenToggleCooldown <= 0 && !inputManager.isKeyHandled(SettingsManager.getInstance().getKeyCode("inventory"))) {
            if (active instanceof com.za.zenith.engine.graphics.ui.InventoryScreen) {
                toggleInventory();
                screenToggleCooldown = 0.25f;
            } else if (active == null) {
                toggleInventory();
                screenToggleCooldown = 0.25f;
            }
        }

        // ESC - Pause Toggle
        if (inputManager.wasJustPressed(InputAction.PAUSE) && screenToggleCooldown <= 0 && !inputManager.isKeyHandled(SettingsManager.getInstance().getKeyCode("pause"))) {
            if (active instanceof com.za.zenith.engine.graphics.ui.PauseScreen) {
                togglePause();
                screenToggleCooldown = 0.25f;
            } else if (active instanceof com.za.zenith.engine.graphics.ui.InventoryScreen || 
                       active instanceof com.za.zenith.engine.graphics.ui.JournalScreen || 
                       active instanceof AnimationEditorScreen || 
                       active instanceof DevInspectorScreen) {
                // Закрываем активный экран при нажатии ESC
                if (active instanceof com.za.zenith.engine.graphics.ui.InventoryScreen) {
                    toggleInventory();
                } else if (active instanceof com.za.zenith.engine.graphics.ui.JournalScreen) {
                    toggleJournal();
                } else if (active instanceof AnimationEditorScreen) {
                    toggleAnimationEditor();
                } else if (active instanceof DevInspectorScreen) {
                    com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().closeScreen();
                    inputManager.enableMouseCapture(window);
                }
                screenToggleCooldown = 0.25f;
            } else if (active == null) {
                if (currentNappingSession != null) {
                    closeNappingWithWaste();
                    screenToggleCooldown = 0.25f;
                } else {
                    togglePause();
                    screenToggleCooldown = 0.25f;
                }
            }
        }

        highlightedBlock = inputManager.input(window, camera, player, deltaTime, renderer, world, networkClient);
    }

    public void toggleJournal() {
        if (currentNappingSession != null) return;
        inventoryOpen = !inventoryOpen;
        if (inventoryOpen) {
            inputManager.disableMouseCapture(window);
            com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().openScreen(
                new com.za.zenith.engine.graphics.ui.JournalScreen(), window.getWidth(), window.getHeight());
        } else {
            inputManager.enableMouseCapture(window);
            com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().closeScreen();
        }
    }

    public void toggleAnimationEditor() {
        if (currentNappingSession != null) return;
        
        if (!inventoryOpen) { // Opening
            inventoryOpen = true;
            inputManager.disableMouseCapture(window);
            com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().openScreen(
                new AnimationEditorScreen(), window.getWidth(), window.getHeight());
        } else { // Closing
            inventoryOpen = false; 
            paused = false;
            // Explicitly enable capture BEFORE closing screen to ensure state
            inputManager.enableMouseCapture(window);
            com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().closeScreen();
        }
    }
    
    public void toggleInventory() {
        if (currentNappingSession != null) return;
        inventoryOpen = !inventoryOpen;
        if (inventoryOpen) inputManager.disableMouseCapture(window);
        else {
            inputManager.enableMouseCapture(window);
            com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().closeScreen();
            com.za.zenith.world.items.ItemStack held = inputManager.getHeldStack();
            if (held != null) {
                player.getInventory().addItem(held);
                inputManager.clearHeldStack();
            }
        }
    }

    public void togglePause() {
        paused = !paused;
        if (paused) {
            inputManager.disableMouseCapture(window);
            com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().openScreen(
                new com.za.zenith.engine.graphics.ui.PauseScreen(), window.getWidth(), window.getHeight());
        } else {
            com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().closeScreen();
            if (!inventoryOpen && currentNappingSession == null) inputManager.enableMouseCapture(window);
        }
    }

    public void startNapping(com.za.zenith.world.items.Item item) {
        currentNappingSession = new com.za.zenith.world.recipes.NappingSession(item);
        inputManager.disableMouseCapture(window);
    }

    public void closeNapping() {
        currentNappingSession = null;
        if (!inventoryOpen && !paused) inputManager.enableMouseCapture(window);
    }

    public void closeNappingWithWaste() {
        if (currentNappingSession != null) {
            com.za.zenith.world.items.ItemStack current = player.getInventory().getSelectedItemStack();
            if (current != null) {
                com.za.zenith.world.items.ItemStack newStack = current.getCount() > 1 
                    ? new com.za.zenith.world.items.ItemStack(current.getItem(), current.getCount() - 1) : null;
                player.getInventory().setStackInSlot(player.getInventory().getSelectedSlot(), newStack);
            }
        }
        closeNapping();
    }

    public boolean isNappingOpen() { return currentNappingSession != null; }
    public com.za.zenith.world.recipes.NappingSession getNappingSession() { return currentNappingSession; }

    private void update(float interval) {
        if (paused) return;

        com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
        if (active != null && active.isScene()) return;
        
        world.update(interval); // Fixed Physics
        com.za.zenith.world.particles.ParticleManager.getInstance().update(interval, world);
        
        // Fixed Camera Position (Physics Based)
        camera.setPosition(player.getPosition().x, player.getPosition().y + player.getEyeHeight(), player.getPosition().z);

        if (networkClient != null && networkClient.isConnected()) {
            networkClient.sendPlayerPosition();
        }
    }
    
    private void render(float alpha, float deltaTime) {
        fpsTimer += deltaTime;
        fpsCounter++;
        if (fpsTimer >= 1.0f) {
            currentFps = fpsCounter;
            fpsCounter = 0;
            fpsTimer = 0;
        }

        com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
        if (active != null && active.isScene()) {
            active.render(renderer.getUIRenderer(), window.getWidth(), window.getHeight(), renderer.getAtlas());
            renderer.renderDebug(currentFps, window.getWidth(), window.getHeight());
            window.update();
            return;
        }

        // High-Frequency Animation Update (Right before render)
        if (!paused) {
            player.updateAnimations(deltaTime, world);

            // Sync animated offsets to camera (High-frequency)


            camera.setPitchOffset(player.getCameraPitchOffset());
            camera.setRollOffset(player.getCameraRollOffset());
            camera.setFovOffset(player.getFovOffset());
            camera.setOffsets(player.getCameraOffsetX(), player.getCameraOffsetY(), player.getCameraOffsetZ());
        }

        renderer.render(window, camera, world, highlightedBlock, networkClient, alpha, deltaTime, inputManager);
        
        // Napping is a special modal state, kept outside the pipeline for now
        if (currentNappingSession != null) com.za.zenith.engine.graphics.ui.NappingGUI.render(renderer.getUIRenderer(), window.getWidth(), window.getHeight(), currentNappingSession);
        
        // Render Active Screen (like Pause Menu) or Inventory
        if (inventoryOpen) {
            renderer.getUIRenderer().renderInventory(window.getWidth(), window.getHeight(), renderer.getAtlas());
        } else if (active != null && !active.isScene()) {
            renderer.getUIRenderer().renderPauseMenu(window.getWidth(), window.getHeight(), renderer.getAtlas());
        }

        renderer.renderDebug(currentFps, window.getWidth(), window.getHeight());        
        window.update();
    }
    
    private void sync(float elapsedTime) {
        float loopSlot = 1f / TARGET_FPS;
        if (elapsedTime < loopSlot) {
            double endTime = timer.getDelta() + loopSlot;
        }
    }
    
    private void cleanup() {
        SettingsManager.getInstance().save();
        if (networkClient != null) networkClient.disconnect();
        if (localServer != null) localServer.stop();
        if (world != null) world.cleanup();
        if (renderer != null) renderer.cleanup();
        if (window != null) window.cleanup();
    }

    public boolean isInventoryOpen() { return inventoryOpen; }
    public boolean isPaused() { return paused; }
    public Window getWindow() { return window; }
    public float getCurrentFps() { return currentFps; }
}


