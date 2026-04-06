package xyz.openatbp.extension.game;

public class StatModifier {
    private String statName;
    private double modifier;
    private ModifierType type;
    private ModifierIntent intent;
    private int durationMs;
    private long startTime;

    public StatModifier(
            String statName,
            double modifier,
            ModifierType type,
            ModifierIntent intent,
            int durationMs) {
        this.statName = statName;
        this.intent = intent;
        this.type = type;
        this.durationMs = durationMs;
        this.startTime = System.currentTimeMillis();

        if (intent == ModifierIntent.BUFF) this.modifier = 1 + modifier;
        else this.modifier = 1 - modifier;
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
