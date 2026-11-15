package com.napoleonic.line.entity;

public enum LineInfantryMode {
    /**
     * «Прекратить огонь / Вольно» — return to normal Recruits behavior.
     */
    CEASE_FIRE(0, "cease_fire"),
    /**
     * «На плечо» — firearm in offhand vertically against left shoulder, hold fire, march in formation.
     */
    SHOULDER_ARMS(1, "shoulder_arms"),
    /**
     * «На караул / В руки» (Present Arms) — firearm in main hand held ready, hold fire, march in formation.
     */
    PRESENT_ARMS(2, "present_arms"),
    /**
     * «Залп по точке» — fire one coordinated volley at the designated ellipse zone, then reload and hold fire.
     */
    VOLLEY(3, "volley"),
    /**
     * «Свободный огонь» — independently acquire targets, fire, and reload.
     */
    FIRE_AT_WILL(4, "fire_at_will"),
    /**
     * «Перезарядка» — force reload all unloaded firearms, then hold fire ready for next order.
     */
    RELOAD(5, "reload");

    private final int id;
    private final String serializedName;

    LineInfantryMode(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int getId() {
        return id;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public static LineInfantryMode byId(int id) {
        return switch (id) {
            case 1 -> SHOULDER_ARMS;
            case 2 -> PRESENT_ARMS;
            case 3 -> VOLLEY;
            case 4 -> FIRE_AT_WILL;
            case 5 -> RELOAD;
            default -> CEASE_FIRE;
        };
    }
}

