package xyz.openatbp.extension.game.effects;

public class StatModifier {
    private final String effectId; // SHOULD ALWAYS BE [ACTOR ID]_[SPELL/SOURCE]
    private final String statName;
    private final double modifier;
    private final ModifierType type;
    private ModifierIntent intent;
    private final int durationMs;
    private final long startTime;

    public StatModifier(
            String effectId,
            String statName,
            double modifier,
            ModifierType type,
            ModifierIntent intent,
            int durationMs) {
        this.effectId = effectId;
        this.statName = statName;
        this.intent = intent;
        this.type = type;
        this.durationMs = durationMs;
        this.startTime = System.currentTimeMillis();

        if (intent == ModifierIntent.BUFF) this.modifier = 1 + modifier;
        else this.modifier = 1 - modifier;
    }

    public String getEffectId() {
        return this.effectId;
    }

    public String getStatName() {
        return this.statName;
    }

    public double getModifier() {
        return this.modifier;
    }

    public ModifierIntent getIntent() {
        return this.intent;
    }

    public ModifierType getType() {
        return this.type;
    }

    public int getDurationMs() {
        return this.durationMs;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - this.startTime >= this.durationMs;
    }
}
