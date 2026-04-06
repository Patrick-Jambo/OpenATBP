package xyz.openatbp.extension.game;

import static xyz.openatbp.extension.game.champions.FlamePrincess.W_POLY_DURATION;

import java.util.*;

import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Base;
import xyz.openatbp.extension.game.actors.BaseTower;
import xyz.openatbp.extension.game.actors.Tower;
import xyz.openatbp.extension.game.champions.BubbleGum;

public class EffectManager {
    private final Actor actor;
    private final List<StatModifier> modifiers;
    private final List<ActorStateEffect> stateEffects;
    private final Map<ActorState, Boolean> states = new HashMap<>();

    private Map<String, Double> tempStats;

    private static final List<Class<? extends Actor>> ignoreList =
            List.of(Base.class, Tower.class, BaseTower.class, BubbleGum.Turret.class);

    public static final float KNOCKBACK_SPEED = 11;
    public static final int FEAR_MOVING_DISTANCE = 3;

    public EffectManager(Actor a) {
        this.actor = a;
        modifiers = new ArrayList<>();
        stateEffects = new ArrayList<>();
    }

    public Map<ActorState, Boolean> getStates() {
        return states;
    }

    private void handleStateEffect(ActorState state, double modifier, int durationMs) {
        actor.onStateChange(state, true);

        switch (state) {
            case SLOWED:
                StatModifier m =
                        new StatModifier(
                                "speed",
                                modifier,
                                ModifierType.MULTIPLICATIVE,
                                ModifierIntent.DEBUFF,
                                durationMs);
                modifiers.add(m);
                break;

            case ROOTED:
            case STUNNED:
                actor.stopMoving();
                break;

            case CHARMED:
                handleCharm(); // stub
                break;

            case FEARED:
                handleFear(); // stub
                break;

            case POLYMORPH:
                if (actor.hasCustomPolySwap()) actor.customSwapToPoly();
                else handleSwapToPoly();
                break;

            case CLEANSED:
                removeEffects();
                break;
        }
    }

    public void handleCharm() {}

    public void handleFear() {}

    public boolean hasState(ActorState state) {
        return states.getOrDefault(state, false);
    }

    private boolean canApplyStateOrEffect(ModifierIntent intent) {
        if (hasState(ActorState.IMMUNITY) && intent == ModifierIntent.DEBUFF) return false;
        return true;
    }

    public void setState(ActorState state, boolean enabled) {
        this.states.put(state, enabled);
        ExtensionCommands.updateActorState(
                actor.getParentExt(), actor.getRoom(), actor.getId(), state, enabled);
    }

    private ModifierIntent evaluateState(ActorState state) {
        // IF IT RETURNS BUFF IT MEANS STATE CAN BE ADDED IF AN ACTOR HAS IMMUNITY
        // SOME STATES ARE NOT REAL BUFFS BUT SHOULD STILL WORK ON AN IMMUNE ACTOR
        switch (state) {
            case INVINCIBLE:
            case INVISIBLE:
            case STEALTH:
            case IMMUNITY:
            case BRUSH:
            case REVEALED:
            case TRANSFORMED:
            case CLEANSED:
                return ModifierIntent.BUFF;
            default:
                return ModifierIntent.DEBUFF;
        }
    }

    public void addState(ActorState state, double percent, int durationMs) {
        if (isActorIgnored(actor)) return;

        ModifierIntent intent = evaluateState(state);
        if (canApplyStateOrEffect(intent)) {
            ActorStateEffect stateEffect = new ActorStateEffect(state, percent, durationMs);
            stateEffects.add(stateEffect);

            handleStateEffect(state, percent, durationMs);
            setState(state, true);
        }
    }

    public void addState(
            ActorState state,
            double percent,
            String bundle,
            int durationMs,
            String fxId,
            String emit) {
        if (isActorIgnored(actor)) return;

        ModifierIntent intent = evaluateState(state);

        if (canApplyStateOrEffect(intent)) {
            ActorStateEffect stateEffect = new ActorStateEffect(state, percent, durationMs);
            stateEffects.add(stateEffect);
            handleStateEffect(state, percent, durationMs);
            setState(state, true);

            ExtensionCommands.createActorFX(
                    actor.getParentExt(),
                    actor.getRoom(),
                    actor.getId(),
                    bundle,
                    durationMs,
                    fxId,
                    true,
                    emit,
                    false,
                    false,
                    actor.getTeam());
        }
    }

    public void handleSwapToPoly() {
        ExtensionCommands.swapActorAsset(
                actor.getParentExt(), actor.getRoom(), actor.getId(), "flambit");
        ExtensionCommands.createActorFX(
                actor.getParentExt(),
                actor.getRoom(),
                actor.getId(),
                "statusEffect_polymorph",
                1000,
                actor.getId() + "_statusEffect_polymorph",
                true,
                "",
                true,
                false,
                actor.getTeam());
        ExtensionCommands.createActorFX(
                actor.getParentExt(),
                actor.getRoom(),
                actor.getId(),
                "flambit_aoe",
                W_POLY_DURATION,
                actor.getId() + "_flambit_aoe",
                true,
                "",
                true,
                false,
                actor.getTeam());
        ExtensionCommands.createActorFX(
                actor.getParentExt(),
                actor.getRoom(),
                actor.getId(),
                "fx_target_ring_2",
                W_POLY_DURATION,
                actor.getId() + "_flambit_ring_",
                true,
                "",
                true,
                true,
                actor.getOppositeTeam());
    }

    public void handleSwapFromPoly() {
        String bundle = actor.getSkinAssetBundle();
        ExtensionCommands.swapActorAsset(
                actor.getParentExt(), actor.getRoom(), actor.getId(), bundle);
    }

    public boolean isActorIgnored(Actor a) {
        return ignoreList.contains(a.getClass());
    }

    public void addEffect(
            String stat, double percent, ModifierType type, ModifierIntent intent, int durationMs) {
        if (isActorIgnored(actor)) return;

        if (canApplyStateOrEffect(intent)) {
            StatModifier m = new StatModifier(stat, percent, type, intent, durationMs);
            modifiers.add(m);
        }
    }

    public void addEffect(
            String stat,
            double percent,
            ModifierType type,
            ModifierIntent intent,
            int durationMs,
            String bundle,
            String fxId,
            String emit) {
        if (isActorIgnored(actor)) return;

        if (canApplyStateOrEffect(intent)) {
            StatModifier m = new StatModifier(stat, percent, type, intent, durationMs);
            modifiers.add(m);

            ExtensionCommands.createActorFX(
                    actor.getParentExt(),
                    actor.getRoom(),
                    actor.getId(),
                    bundle,
                    durationMs,
                    fxId,
                    true,
                    emit,
                    false,
                    false,
                    actor.getTeam());
        }
    }

    public void removeAllStatEffects(String stat) {
        modifiers.removeIf(m -> m.getStatName().equals(stat));
    }

    public double getTempStat(String stat) {
        if (tempStats == null) return actor.getStats().get(stat);
        return tempStats.getOrDefault(stat, actor.getStats().get(stat));
    }

    public boolean hasTempStat(String stat) {
        if (tempStats == null) return false;
        return tempStats.containsKey(stat)
                && !Objects.equals(tempStats.get(stat), actor.getStats().get(stat));
    }

    public void removeEffects() {
        modifiers.clear();

        for (ActorState s : states.keySet()) {
            ExtensionCommands.updateActorState(
                    actor.getParentExt(), actor.getRoom(), actor.getId(), s, false);
        }

        stateEffects.clear();
    }

    public void removeState(ActorState state) {
        Iterator<ActorStateEffect> it = stateEffects.iterator();

        while (it.hasNext()) { // keep iterator cause it's safe and prevents
            // ConcurrentModificationException
            ActorStateEffect e = it.next();
            if (e.getState() == state) {
                it.remove();
            }
        }
    }

    public void cleanseDebuffs() {
        Set<ActorState> removedStates = new HashSet<>();

        Iterator<ActorStateEffect> it = stateEffects.iterator();
        while (it.hasNext()) {
            ActorStateEffect e = it.next();
            if (e.getIntent() == ModifierIntent.DEBUFF) {
                removedStates.add(e.getState());
                it.remove();
            }
        }

        for (ActorState s : removedStates) {
            setState(s, false);
        }

        Iterator<StatModifier> itM = modifiers.iterator();
        while (itM.hasNext()) {
            StatModifier m = itM.next();
            if (m.getIntent() == ModifierIntent.DEBUFF) itM.remove();
        }
    }

    public void handleEffectsUpdate() {
        if (isActorIgnored(actor)) return;

        // HANDLES EFFECTS
        modifiers.removeIf(StatModifier::isExpired);

        if (!modifiers.isEmpty()) {
            tempStats = new HashMap<>(actor.getStats()); // start with base stats

            Map<String, List<StatModifier>> modifiersByStat = new HashMap<>();
            for (StatModifier m : modifiers) {
                modifiersByStat.computeIfAbsent(m.getStatName(), k -> new ArrayList<>()).add(m);
            }

            for (Map.Entry<String, List<StatModifier>> entry : modifiersByStat.entrySet()) {
                String stat = entry.getKey();
                double base = actor.getStats().get(stat);

                double multiplicative =
                        entry.getValue().stream()
                                .filter(m -> m.getType() == ModifierType.MULTIPLICATIVE)
                                .reduce(1.0, (acc, m) -> acc * m.getModifier(), (a, b) -> a * b);

                double additive =
                        entry.getValue().stream()
                                .filter(m -> m.getType() == ModifierType.ADDITIVE)
                                .mapToDouble(StatModifier::getModifier)
                                .sum();

                tempStats.put(stat, (base * multiplicative) + additive);
            }
        }

        if (!stateEffects.isEmpty()) {
            Iterator<ActorStateEffect> it = stateEffects.iterator();

            // expired = state map is true and state count is 0
            Map<ActorState, Boolean> stateBools = new HashMap<>(); // true = expired
            Map<ActorState, Integer> stateCount = new HashMap<>();

            for (ActorState s : ActorState.values()) {
                stateBools.put(s, false);
                stateCount.put(s, 0);
            }

            while (it.hasNext()) {
                ActorStateEffect e = it.next();
                stateCount.put(e.getState(), stateCount.get(e.getState()) + 1);

                if (e.isExpired()) {
                    it.remove();
                    stateBools.put(e.getState(), true);
                    stateCount.put(e.getState(), stateCount.get(e.getState()) - 1);
                }
            }

            for (ActorState s : stateBools.keySet()) {
                if (stateBools.get(s) && stateCount.get(s) == 0) {
                    setState(s, false); // remove state if expired

                    if (s == ActorState.POLYMORPH && actor.hasCustomPolySwap()) {
                        actor.customSwapFromPoly();
                    } else if (s == ActorState.POLYMORPH) {
                        handleSwapFromPoly();
                    }
                }
            }
        }
    }
}
