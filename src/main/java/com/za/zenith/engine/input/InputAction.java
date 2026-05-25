package com.za.zenith.engine.input;

/**
 * Перечисление абстрактных логических действий игрока.
 * Связывает действия с ключами конфигурации в SettingsManager.
 */
public enum InputAction {
    // Движение
    MOVE_FORWARD("move_forward"),
    MOVE_BACK("move_back"),
    MOVE_LEFT("move_left"),
    MOVE_RIGHT("move_right"),
    JUMP("jump"),
    SPRINT("sprint"),
    SNEAK("sneak"),

    // Взаимодействие и Геймплей
    ATTACK_MINE("attack_mine"),
    INTERACT_PLACE("interact_place"),
    DROP("drop"),
    INVENTORY("inventory"),
    JOURNAL("journal"),
    SORT_INVENTORY("sort_inventory"),

    // Системные действия
    PAUSE("pause"),
    DEBUG_MENU("debug_menu"),
    EDITOR_TOGGLE("editor_toggle"),
    LIVE_INSPECTOR("live_inspector"),
    TOGGLE_FLY("toggle_fly"),
    TOGGLE_FXAA("toggle_fxaa"),
    TOGGLE_VERTICAL_MODE("toggle_vertical_mode"),

    // Слот хотбара
    SLOT_1("slot_1"),
    SLOT_2("slot_2"),
    SLOT_3("slot_3"),
    SLOT_4("slot_4"),
    SLOT_5("slot_5"),
    SLOT_6("slot_6"),
    SLOT_7("slot_7"),
    SLOT_8("slot_8"),
    SLOT_9("slot_9");

    private final String settingsKey;

    InputAction(String settingsKey) {
        this.settingsKey = settingsKey;
    }

    public String getSettingsKey() {
        return settingsKey;
    }
}
