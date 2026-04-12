package xyz.openatbp.extension.game.bots;

import static xyz.openatbp.extension.game.champions.Jake.*;
import static xyz.openatbp.extension.game.effects.EffectManager.KNOCKBACK_SPEED;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.RoomHandler;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.actors.Actor;
import xyz.openatbp.extension.game.actors.Bot;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.game.effects.ActorState;
import xyz.openatbp.extension.pathfinding.PathFinder;

public class JakeBot extends Bot {
    private Map<String, Long> lastPassiveTime = new HashMap<>();

    private float offsetDistance = 1;
    private boolean grabActive = false;
    private Point2D grabPoint;
    private float grabStatus = 0;
    private Point2D qDestination;
    private boolean blockAbilities = false;

    private boolean ultActivated = false;
    private long lastStomped = 0;
    private boolean stompSoundChange = false;
    private long ultStartTime = 0;

    public JakeBot(
            ATBPExtension parentExt,
            Room room,
            String avatar,
            String displayName,
            int team,
            BotMapConfig mapConfig) {
        super(parentExt, room, avatar, displayName, team, mapConfig);

        qCooldownMs = 12000;
        wCooldownMs = 14000;
        eCooldownMs = 60000;

        qGCooldownMs = 1000;
        wGCooldownMs = 500;
        eGCooldownMs = 5000;

        qCastDelayMS = 600;
        wCastDelayMS = 0;
        eCastDelayMS = 0;
        setHealth(800, 800);
    }

    @Override
    public void update(int msRan) {
        super.update(msRan);

        if (grabActive && (hasInterrupingCC() || this.dead)) {
            blockAbilities = false;
            resetGrab();
            if (!hasMovementCC()) canMove = true;
            ExtensionCommands.playSound(
                    this.parentExt, this.room, this.id, "sfx_skill_interrupted", this.location);
            ExtensionCommands.actorAnimate(parentExt, room, id, "idle", 10, false);
        }
        if (grabActive) {
            if (grabStatus == 5) offsetDistance = 1.75f;

            AbilityShape qRect =
                    AbilityShape.createRectangle(grabPoint, qDestination, 1, offsetDistance);

            qRect.displayVertices(parentExt, room, id, team);

            RoomHandler handler = parentExt.getRoomHandler(room.getName());

            List<Actor> foes =
                    Champion.getEnemyActorsInRadius(handler, team, location, MAX_Q_RANGE);
            foes.removeIf(f -> f.getActorType() == ActorType.TOWER);
            foes.removeIf(f -> !(qRect.contains(f.getLocation(), f.getCollisionRadius())));
            foes.removeIf(f -> f.getMovementState() == MovementState.LEAPING);

            if (!foes.isEmpty()) {
                resetGrab();
                Actor victim = foes.get(0);
                JsonNode spellData = parentExt.getAttackData(getChampionName(avatar), "spell1");

                Point2D origLocation = this.location;

                float distance = (float) origLocation.distance(victim.getLocation());
                JsonNode qData = parentExt.getAttackData(avatar, "spell1");
                float dmgPerUnitOfDistance = (float) qData.get("damageRatio").asDouble();
                int baseDmg = qData.get("damage").asInt();

                float modifier = dmgPerUnitOfDistance * distance;
                double damage = baseDmg + (modifier * getPlayerStat("spellDamage"));

                victim.addToDamageQueue(this, damage, spellData, false);

                if (isNonStructureEnemy(victim)) {
                    victim.getEffectManager()
                            .addState(ActorState.STUNNED, id + "_jake_q_stun", 0d, Q_STUN_DURATION);
                }

                if (distance > 5) {
                    ExtensionCommands.playSound(
                            parentExt,
                            room,
                            victim.getId(),
                            "sfx_oildrum_dead",
                            victim.getLocation());

                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            victim.getId(),
                            "jake_stomp_fx",
                            500,
                            this.id + "_jake_q_fx",
                            false,
                            "",
                            false,
                            false,
                            team);
                }

                PathFinder pf = handler.getPathFinder();
                if (!pf.lineIntersectsObstacle(location, victim.getLocation())) {
                    float pullDist =
                            (float) location.distance(victim.getLocation()) - Q_VICTIM_SEPARATION;

                    victim.handlePull(location, pullDist);

                    int timeMS = (int) ((pullDist / KNOCKBACK_SPEED) * 1000);

                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            victim.getId(),
                            "jake_trail",
                            timeMS,
                            victim.getId() + "qTrail",
                            true,
                            "",
                            false,
                            false,
                            team);
                }

                unlockAbilitiesAfterDelay();
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell1c", 500, false);

            } else if (grabStatus < 8) {
                grabPoint = Champion.getAbilityLine(grabPoint, qDestination, 1).getP2();
                grabStatus++;
            } else {
                resetGrab();
                unlockAbilitiesAfterDelay();
                ExtensionCommands.actorAnimate(parentExt, room, id, "spell1c", 500, false);
            }
        }

        if (ultActivated && target != null && evaluateBotState() == BotState.FIGHTING) {
            Point2D targetLoc = target.getLocation();

            final float MAX_RADIUS = 0.5f;

            double angle = Math.random() * 2 * Math.PI;
            double radius = Math.random() * MAX_RADIUS;

            double offsetX = Math.cos(angle) * radius;
            double offsetY = Math.sin(angle) * radius;

            Point2D ultDest =
                    new Point2D.Double(targetLoc.getX() + offsetX, targetLoc.getY() + offsetY);

            if (canMove() && !isMoving) {
                startMoveTo(ultDest);
            }
        }

        if (ultActivated && System.currentTimeMillis() - ultStartTime >= E_DURATION) {
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
            ExtensionCommands.removeFx(parentExt, room, id + "_stomp");
            ultActivated = false;
        }

        if (ultActivated && !isStopped()) {
            if (System.currentTimeMillis() - lastStomped >= E_STOMP_CD) {
                lastStomped = System.currentTimeMillis();
                RoomHandler handler = parentExt.getRoomHandler(room.getName());
                for (Actor a : Champion.getActorsInRadius(handler, location, 2f)) {
                    if (a.getActorType() != ActorType.TOWER
                            && a.getTeam() != team
                            && a.isNotLeaping()) {
                        JsonNode spellData = parentExt.getAttackData(avatar, "spell3");
                        double damage = (double) (getSpellDamage(spellData)) / 2d;
                        a.addToDamageQueue(this, (int) damage, spellData, true);
                    }
                }

                String stompFX = SkinData.getJakeEStompFX(avatar);
                String stompSFX = SkinData.getJakeEStompSFX(avatar);
                String stompSFX1 = SkinData.getJakeEStomp1SFX(avatar);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        stompFX,
                        325,
                        id + "_stomp",
                        true,
                        "",
                        false,
                        false,
                        team);
                stompSoundChange = !stompSoundChange;
                if (stompSoundChange) {
                    ExtensionCommands.playSound(parentExt, room, id, stompSFX1, location);
                } else {
                    ExtensionCommands.playSound(parentExt, room, id, stompSFX, location);
                }
            }
        }
    }

    @Override
    public boolean canMove() {
        if (grabActive || blockAbilities) return false;
        return super.canMove();
    }

    private void resetGrab() {
        grabActive = false;
        grabStatus = 0;
        offsetDistance = 1;
    }

    private void unlockAbilitiesAfterDelay() {
        Runnable unlock = () -> blockAbilities = false;
        scheduleTask(unlock, Q_UNLOCK_SKILLS_DELAY);
    }

    @Override
    public void attack(Actor a) {
        super.attack(a);
        if (isNonStructureEnemy(a)) {
            if (!lastPassiveTime.isEmpty()) {
                if (lastPassiveTime.containsKey(a.getId())) {
                    if (System.currentTimeMillis() - lastPassiveTime.get(a.getId())
                            >= PASSIVE_PER_TARGET_CD) {
                        doPassive(a);
                    }
                } else {
                    doPassive(a);
                }
            } else {
                doPassive(a);
            }
        }
    }

    public void doPassive(Actor target) {
        Runnable passive =
                () -> {
                    lastPassiveTime.put(target.getId(), System.currentTimeMillis());
                    JsonNode attackData = this.parentExt.getAttackData("jake", "spell4");
                    target.addToDamageQueue(
                            this,
                            getPlayerStat("attackDamage") * PASSIVE_ATTACKDAMAGE_VALUE,
                            attackData,
                            false);

                    target.getEffectManager()
                            .addState(
                                    ActorState.ROOTED,
                                    id + "_jake_passive_root",
                                    0d,
                                    PASSIVE_ROOT_DURATION);

                    if (!this.avatar.contains("cake"))
                        ExtensionCommands.playSound(
                                this.parentExt,
                                this.room,
                                this.id,
                                "vo/vo_jake_passive_1",
                                this.location);
                };
        SmartFoxServer.getInstance()
                .getTaskScheduler()
                .schedule(passive, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
    }

    @Override
    public void handleFightingAbilities() {
        if (target != null) {
            if (target instanceof UserActor) {
                if (canUseQ()) useQ(target.getLocation());
            }

            if (canUseW()) useW(target.getLocation());
            if (canUseE()) useE(target.getLocation());
        }
    }

    @Override
    public void handleRetreatAbilities() {
        if (canUseW()) useW(location);
    }

    @Override
    public boolean canAttack() {
        if (ultActivated || grabActive || globalCooldown != 0) return false;
        return super.canAttack();
    }

    @Override
    public boolean canUseQ() {
        if (timeOk(1) && !ultActivated) {
            AbilityShape qRect =
                    AbilityShape.createRectangle(
                            location, target.getLocation(), MAX_Q_RANGE, 1.75f);
            RoomHandler rh = parentExt.getRoomHandler(room.getName());

            List<Actor> enemies = Champion.getEnemyActorsInRadius(rh, team, location, MAX_Q_RANGE);
            enemies.remove(target);

            for (Actor a : enemies) {
                if (qRect.contains(a.getLocation(), a.getCollisionRadius())) return false;
            }

            return true;
        }
        return false;
    }

    @Override
    public boolean canUseW() {
        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> enemiesNearby = Champion.getEnemyActorsInRadius(rh, team, location, W_RADIUS);
        if (enemiesNearby.stream().anyMatch(a -> a instanceof UserActor)
                && timeOk(2)
                && !ultActivated
                && !blockAbilities) return true;
        else return enemiesNearby.size() > 1 && timeOk(2) && !ultActivated && !blockAbilities;
    }

    @Override
    public boolean canUseE() {
        boolean distance = target.getLocation().distance(location) <= 2;
        boolean tPlayerMoveCC =
                target != null && target instanceof UserActor && target.hasMovementCC();
        boolean tSlowedPlayer =
                target != null
                        && target instanceof UserActor
                        && target.getEffectManager().hasState(ActorState.SLOWED);

        return (timeOk(3) && distance) && (tPlayerMoveCC || tSlowedPlayer);
    }

    @Override
    public void useQ(Point2D destination) {
        globalCooldown += qGCooldownMs;
        lastQUse = System.currentTimeMillis();
        blockAbilities = true;

        faceTarget(target);
        stopMoving(Q_RESTRAINT_TIME);

        ExtensionCommands.actorAnimate(parentExt, room, id, "spell1", 1500, false);

        Runnable activateHitbox =
                () -> {
                    grabPoint = location;
                    qDestination =
                            Champion.getAbilityLine(location, target.getLocation(), 9).getP2();
                    grabActive = true;
                };

        scheduleTask(activateHitbox, 500);
        String stretchSFX = SkinData.getJakeQSFX(avatar);
        String stretchVO = SkinData.getJakeQVO(avatar);
        ExtensionCommands.playSound(parentExt, room, id, stretchSFX, location);
        ExtensionCommands.playSound(parentExt, room, id, stretchVO, location);
    }

    @Override
    public void useW(Point2D destination) {
        globalCooldown += wGCooldownMs;
        lastWUse = System.currentTimeMillis();

        this.stopMoving(wGCooldownMs);
        String ballFX = SkinData.getJakeWFX(avatar);
        String dustUpFX = SkinData.getJakeWDustUpFX(avatar);

        ExtensionCommands.actorAnimate(parentExt, room, id, "spell2", 2000, false);

        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                ballFX,
                2000,
                id + "_ball",
                true,
                "targetNode",
                true,
                false,
                team);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                dustUpFX,
                1500,
                id + "_dust",
                false,
                "Bip001 Footsteps",
                false,
                false,
                team);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "fx_target_ring_3",
                850,
                id + "_jake_ring_3",
                true,
                "",
                true,
                true,
                team);
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        for (Actor a : Champion.getActorsInRadius(handler, location, W_RADIUS)) {
            if (isNonStructureEnemy(a)) {
                a.handleKnockback(location, W_KNOCKBACK_DIST);
            }

            if (a.getActorType() != ActorType.TOWER && a.isNotLeaping()) {
                JsonNode spellData = parentExt.getAttackData(avatar, "spell2");
                double dmg = getSpellDamage(spellData);
                a.addToDamageQueue(this, dmg, spellData, false);
            }
        }

        String ballVO = SkinData.getJakeWVO(avatar);
        String ballSFX = SkinData.getJakeWSFX(avatar);
        ExtensionCommands.playSound(parentExt, room, id, ballVO, location);
        ExtensionCommands.playSound(parentExt, room, id, ballSFX, location);
    }

    @Override
    public void useE(Point2D destination) {
        globalCooldown += eGCooldownMs;
        lastEUse = System.currentTimeMillis();

        this.stopMoving();
        String bigFX = SkinData.getJakeEFX(avatar);
        ExtensionCommands.swapActorAsset(this.parentExt, this.room, this.id, bigFX);
        ultActivated = true;
        ultStartTime = System.currentTimeMillis();
        effectManager.cleanseDebuffs();

        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "statusEffect_immunity",
                E_DURATION,
                id + "_ultImmunity",
                true,
                "displayBar",
                false,
                false,
                team);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "fx_target_ring_2",
                E_DURATION,
                id + "_jake_ring_2",
                true,
                "",
                true,
                true,
                team);

        effectManager.addState(ActorState.CLEANSED, id + "_jake_e_cleanse", 0d, E_DURATION);

        effectManager.addState(ActorState.IMMUNITY, id + "_jake_e_immunity", 0d, E_DURATION);

        String growVO = SkinData.getJakeEVO(avatar);
        String growSFX = SkinData.getJakeESFX(avatar);

        ExtensionCommands.playSound(parentExt, room, id, growSFX, location);
        ExtensionCommands.playSound(parentExt, room, id, growVO, location);
    }

    @Override
    public void levelUpStats() {
        switch (level) {
            case 1:
                setStat("attackDamage", 43);
                setStat("armor", 26);
                setStat("attackSpeed", 1450);
                setStat("spellDamage", 12);
                setStat("spellResist", 21);
                setStat("healthRegen", 3);
                setHealth(getHealth(), 800);
                break;

            case 2:
                setStat("attackDamage", 46);
                setStat("armor", 27);
                setStat("attackSpeed", 1400);
                setStat("spellDamage", 14);
                setStat("spellResist", 22);
                setStat("healthRegen", 4);
                setHealth(getHealth(), 850);
                break;

            case 3:
                setStat("attackDamage", 49);
                setStat("armor", 28);
                setStat("attackSpeed", 1350);
                setStat("spellDamage", 16);
                setStat("spellResist", 23);
                setStat("healthRegen", 5);
                setHealth(getHealth(), 900);
                break;

            case 4:
                setStat("attackDamage", 52);
                setStat("armor", 29);
                setStat("attackSpeed", 1300);
                setStat("spellDamage", 18);
                setStat("spellResist", 24);
                setStat("healthRegen", 6);
                setHealth(getHealth(), 950);
                break;

            case 5:
                setStat("attackDamage", 55);
                setStat("armor", 30);
                setStat("attackSpeed", 1250);
                setStat("spellDamage", 20);
                setStat("spellResist", 25);
                setStat("healthRegen", 7);
                setHealth(getHealth(), 1000);
                break;

            case 6:
                setStat("attackDamage", 58);
                setStat("armor", 31);
                setStat("attackSpeed", 1200);
                setStat("spellDamage", 22);
                setStat("spellResist", 26);
                setStat("healthRegen", 8);
                setHealth(getHealth(), 1050);
                break;

            case 7:
                setStat("attackDamage", 61);
                setStat("armor", 32);
                setStat("attackSpeed", 1150);
                setStat("spellDamage", 24);
                setStat("spellResist", 27);
                setStat("healthRegen", 9);
                setHealth(getHealth(), 1100);
                break;

            case 8:
                setStat("attackDamage", 64);
                setStat("armor", 33);
                setStat("attackSpeed", 1100);
                setStat("spellDamage", 26);
                setStat("spellResist", 28);
                setStat("healthRegen", 10);
                setHealth(getHealth(), 1150);
                break;

            case 9:
                setStat("attackDamage", 67);
                setStat("armor", 34);
                setStat("attackSpeed", 1050);
                setStat("spellDamage", 28);
                setStat("spellResist", 29);
                setStat("healthRegen", 11);
                setHealth(getHealth(), 1200);
                break;

            case 10:
                setStat("attackDamage", 70);
                setStat("armor", 35);
                setStat("attackSpeed", 1000);
                setStat("spellDamage", 30);
                setStat("spellResist", 30);
                setStat("healthRegen", 12);
                setHealth(getHealth(), 1250);
                break;
        }
    }
}
