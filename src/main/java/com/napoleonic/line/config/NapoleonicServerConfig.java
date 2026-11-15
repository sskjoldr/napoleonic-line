package com.napoleonic.line.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class NapoleonicServerConfig {
    public static final ForgeConfigSpec SPEC;

    // Reload durations
    public static final ForgeConfigSpec.DoubleValue PLAYER_RELOAD_SECONDS;
    public static final ForgeConfigSpec.DoubleValue RECRUIT_RELOAD_SECONDS;
    public static final ForgeConfigSpec.DoubleValue RECRUIT_RELOAD_JITTER_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MOVE_DURING_RELOAD;
    public static final ForgeConfigSpec.DoubleValue RELOAD_MOVE_SPEED_MULTIPLIER;

    // Musket damage & ballistics
    public static final ForgeConfigSpec.DoubleValue MUSKET_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_FALLOFF_START_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MIN_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue IGNORE_RESISTANCE_EFFECT;
    public static final ForgeConfigSpec.BooleanValue BULLETS_BREAK_BLOCKS;
    public static final ForgeConfigSpec.IntValue BULLET_MAX_LIFETIME_TICKS;

    // Shoulder Arms («На плечо»)
    public static final ForgeConfigSpec.BooleanValue SHOULDER_ARMS_MELEE_DEFENSE;

    // Volley («Залп по точке»)
    public static final ForgeConfigSpec.DoubleValue VOLLEY_MAX_RAYCAST_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue VOLLEY_EXTRA_WIDTH;
    public static final ForgeConfigSpec.DoubleValue VOLLEY_DEPTH;
    public static final ForgeConfigSpec.IntValue VOLLEY_MIN_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue VOLLEY_MAX_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue VOLLEY_MAX_CALCULATIONS_PER_TICK;

    // AI & Optimization
    public static final ForgeConfigSpec.IntValue TARGET_SEARCH_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue MAX_LOS_CHECKS_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue MAX_FIRING_RANGE;

    // Sound
    public static final ForgeConfigSpec.DoubleValue GUNSHOT_SOUND_VOLUME;
    public static final ForgeConfigSpec.DoubleValue GUNSHOT_PITCH_VARIATION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Napoleonic Line Infantry - Server Configuration").push("reload");
        PLAYER_RELOAD_SECONDS = builder
                .comment("Player musket reload time in seconds (default: 15.0s for ~3-4 shots/min).")
                .defineInRange("playerReloadSeconds", 15.0D, 1.0D, 120.0D);
        RECRUIT_RELOAD_SECONDS = builder
                .comment("Recruit musket reload time in seconds (default: 18.0s for ~3 shots/min).")
                .defineInRange("recruitReloadSeconds", 18.0D, 1.0D, 120.0D);
        RECRUIT_RELOAD_JITTER_SECONDS = builder
                .comment("Random +/- seconds added to each recruit's reload, so the rank does not finish together.")
                .defineInRange("recruitReloadJitterSeconds", 2.0D, 0.0D, 20.0D);
        MOVE_DURING_RELOAD = builder
                .comment("If false, recruits stand completely still while reloading. If true, they walk slowly.")
                .define("moveDuringReload", false);
        RELOAD_MOVE_SPEED_MULTIPLIER = builder
                .comment("Movement speed multiplier when moveDuringReload is true.")
                .defineInRange("reloadMoveSpeedMultiplier", 0.25D, 0.0D, 1.0D);
        builder.pop();

        builder.push("ballistics");
        MUSKET_DAMAGE = builder
                .comment("Base musket ball damage for both players and recruits (default: 35.0, bypasses armor and protection).")
                .defineInRange("musketDamage", 35.0D, 1.0D, 500.0D);
        DAMAGE_FALLOFF_START_DISTANCE = builder
                .comment("Distance in blocks after which musket ball damage begins to fall off (default: 60.0).")
                .defineInRange("damageFalloffStartDistance", 60.0D, 10.0D, 256.0D);
        DAMAGE_MIN_MULTIPLIER = builder
                .comment("Minimum damage multiplier at extreme range beyond falloff start.")
                .defineInRange("damageMinMultiplier", 0.35D, 0.05D, 1.0D);
        IGNORE_RESISTANCE_EFFECT = builder
                .comment("Whether musket balls ignore the Resistance potion effect (Totems of Undying still work normally).")
                .define("ignoreResistanceEffect", true);
        BULLETS_BREAK_BLOCKS = builder
                .comment("Whether musket bullets can break glass/blocks (default: false).")
                .define("bulletsBreakBlocks", false);
        BULLET_MAX_LIFETIME_TICKS = builder
                .comment("Maximum lifetime of a musket bullet in ticks (default: 60 ticks = 3 seconds).")
                .defineInRange("bulletMaxLifetimeTicks", 60, 10, 200);
        builder.pop();

        builder.push("shoulder_arms");
        SHOULDER_ARMS_MELEE_DEFENSE = builder
                .comment("If true, recruits in 'Shoulder Arms' mode will defend themselves in close melee if attacked. If false, they strictly hold formation and ignore enemies.")
                .define("shoulderArmsMeleeDefense", false);
        builder.pop();

        builder.push("volley");
        VOLLEY_MAX_RAYCAST_DISTANCE = builder
                .comment("Maximum raycast distance in blocks for aiming a Volley (default: 128.0).")
                .defineInRange("volleyMaxRaycastDistance", 128.0D, 16.0D, 256.0D);
        VOLLEY_EXTRA_WIDTH = builder
                .comment("Extra width added to the group's front width for the Volley ellipse (default: 4.0 blocks).")
                .defineInRange("volleyExtraWidth", 4.0D, 0.0D, 32.0D);
        VOLLEY_DEPTH = builder
                .comment("Depth of the Volley ellipse along the line of fire in blocks (default: 3.0 blocks).")
                .defineInRange("volleyDepth", 3.0D, 0.5D, 16.0D);
        VOLLEY_MIN_DELAY_TICKS = builder
                .comment("Minimum random delay in ticks before a recruit fires in a Volley (default: 0).")
                .defineInRange("volleyMinDelayTicks", 0, 0, 40);
        VOLLEY_MAX_DELAY_TICKS = builder
                .comment("Maximum random delay in ticks before a recruit fires in a Volley so the volley sounds ragged (default: 12). The line always ripples across at least 12 ticks.")
                .defineInRange("volleyMaxDelayTicks", 12, 0, 60);
        VOLLEY_MAX_CALCULATIONS_PER_TICK = builder
                .comment("Maximum number of recruit volley raycast/ballistic setups processed per server tick to prevent TPS spikes on 50+ recruit volleys.")
                .defineInRange("volleyMaxCalculationsPerTick", 15, 1, 100);
        builder.pop();

        builder.push("optimization");
        TARGET_SEARCH_INTERVAL_TICKS = builder
                .comment("Interval in ticks between target searches in Fire at Will mode (default: 15 ticks).")
                .defineInRange("targetSearchIntervalTicks", 15, 10, 60);
        MAX_LOS_CHECKS_PER_TICK = builder
                .comment("Maximum line-of-fire raycasts allowed per server tick across all line infantry recruits.")
                .defineInRange("maxLineOfSightChecksPerTick", 20, 2, 200);
        MAX_FIRING_RANGE = builder
                .comment("Maximum distance in blocks at which recruits will fire in Fire at Will / Volley.")
                .defineInRange("maxFiringRange", 128.0D, 16.0D, 256.0D);
        builder.pop();

        builder.push("sound");
        GUNSHOT_SOUND_VOLUME = builder
                .comment("Gunshot sound volume (controls audible distance: 5.5 = ~88 blocks range).")
                .defineInRange("gunshotSoundVolume", 5.5D, 0.5D, 16.0D);
        GUNSHOT_PITCH_VARIATION = builder
                .comment("Random pitch variation (+/-) for musket gunshots.")
                .defineInRange("gunshotPitchVariation", 0.08D, 0.0D, 0.4D);
        builder.pop();

        SPEC = builder.build();
    }

    private NapoleonicServerConfig() {
    }
}
