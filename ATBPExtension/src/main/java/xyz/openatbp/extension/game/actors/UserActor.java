package xyz.openatbp.extension.game.actors;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.SmartFoxServer;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import com.smartfoxserver.v2.util.TaskScheduler;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.Projectile;
import xyz.openatbp.extension.game.champions.Fionna;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;
import xyz.openatbp.extension.movement.MovementManager;

public class UserActor extends Actor {
    protected static final int DEMON_SWORD_AD_BUFF = 15;
    protected static final int DEMON_SWORD_SD_BUFF = 40;
    protected static final int NAIL_STACKS_PER_CHAMP = 7;
    protected static final int NAIL_STACKS_PER_NON_CHAMPS = 4;
    protected static final int LIGHTNING_SWORD_STACKS_PER_CHAMP = 7;
    protected static final int LIGHTNING_SWORD_STACKS_PER_NON_CHAMP = 4;
    protected static final double ROBE_CD_CHAMP_OR_JG_BOSS_KO = 1;
    protected static final double ROBE_CD_MINION_KO = 0.2;
    protected static final int DAMAGE_PER_NAIL_POINT = 40;
    protected static final int DAMAGE_PER_LIGHTNING_POINT = 55;
    protected static final int CDR_PER_ROBE_POINT = 10;
    protected static final int SAI_PROC_COOLDOWN = 3000;
    protected static final double GRASS_CRIT_INCREASE = 1.25d;
    protected static final double SPEED_BOOST_PER_ROBO_STACK = 0.05;
    protected static final double ROBO_SLOW_VALUE = 0.2;
    protected static final int ROBO_SLOW_DURATION = 2000;
    protected static final int ROBO_CD = 10000;
    protected static final int SIMON_GLASSES_RANGE = 5;

    public static final int BASIC_ATTACK_DELAY = 500;
    protected static final double DASH_SPEED = 20d;
    protected static final int HEALTH_PACK_REGEN = 15;
    protected static final float DC_AD_BUFF = 1.2f;
    protected static final float DC_ARMOR_BUFF = 1.2f;
    protected static final float DC_SPELL_RESIST_BUFF = 1.2f;
    protected static final float DC_SPEED_BUFF = 1.15f;
    protected static final float DC_PD_BUFF = 1.2f;
    public static final int HP_DURATION = 60000;
    public static final int NEW_AUTO_TARGET_CD = 2000;

    protected User player;
    protected boolean autoAttackEnabled = false;
    protected int xp = 0;
    private int deathTime = 10;
    private long timeKilled;
    protected Map<Actor, ISFSObject> aggressors = new HashMap<>();
    protected String backpack;
    private boolean futureCrystalActive = true;
    protected int magicNailStacks = 0;
    protected int lightningSwordStacks = 0;
    protected double robeStacks = 0;
    protected Map<String, Double> endGameStats = new HashMap<>();
    protected int killingSpree = 0;
    protected int multiKill = 0;
    protected long lastKilled = System.currentTimeMillis();
    protected int dcBuff = 0;
    protected boolean[] canCast = {true, true, true};
    protected Map<String, ScheduledFuture<?>> iconHandlers = new HashMap<>();
    protected int idleTime = 0;
    protected boolean changeTowerAggro = false;

    // Set debugging options via config.properties next to the extension jar
    protected static boolean movementDebug;
    private static boolean invincibleDebug;
    private static boolean abilityDebug;
    private static boolean speedDebug;
    private static boolean damageDebug;

    protected double hits = 0;
    protected boolean hpPickup = false;
    protected long healthPackPickUpTime = 0;
    protected boolean hasKeeothBuff = false;
    protected boolean hasGooBuff = false;
    protected long keeothBuffStartTime = 0;
    protected long gooBuffStartTime = 0;
    protected List<UserActor> killedPlayers = new ArrayList<>();
    protected long lastAutoTargetTime = 0;
    protected long stealthEmbargo = -1;
    private boolean moonVfxActivated = false;
    protected double glassesBuff = 0;
    protected long spellShieldCooldown = -1;
    protected boolean spellShieldActive = false;
    protected long iFrame = -1;
    protected String numbChuckVictim;
    protected boolean numbSlow = false;
    protected int roboStacks = 0;
    protected List<Monster.BuffType> activeMonsterBuffs = new ArrayList<>();
    protected Actor lichHandTarget;
    protected long lastLichHandStack = 0L;
    protected int lichHandStacks = 0;
    protected long lastAuto = -1;
    protected long lastSpell = -1;
    protected int fightKingStacks = 0;
    protected int cosmicStacks = 0;
    private boolean flameCloakEffectActivated = false;
    protected boolean hasGlassesPoint = false;
    protected Long lastSaiProcTime = 0L;
    protected Long lastZeldronBuff = 0L;
    protected Long lastRoboEffect = 0L;
    protected HashMap<UserActor, Integer> simonGlassesBuffProviders = new HashMap<>(2);

    protected Point2D queuedMovementDestination = null;

    // TODO: Add all stats into UserActor object instead of User Variables
    public UserActor(User u, ATBPExtension parentExt) {
        this.parentExt = parentExt;
        id = String.valueOf(u.getId());
        team = u.getVariable("player").getSFSObjectValue().getInt("team");
        player = u;
        avatar = u.getVariable("player").getSFSObjectValue().getUtfString("avatar");
        displayName = u.getVariable("player").getSFSObjectValue().getUtfString("name");
        ISFSObject playerLoc = player.getVariable("location").getSFSObjectValue();
        float x = playerLoc.getSFSObject("p1").getFloat("x");
        float z = playerLoc.getSFSObject("p1").getFloat("z");
        location = new Point2D.Float(x, z);
        movementLine = new Line2D.Float(location, location);
        stats = initializeStats();
        attackCooldown = stats.get("attackSpeed");
        currentHealth = stats.get("health");
        maxHealth = currentHealth;
        room = u.getLastJoinedRoom();
        actorType = ActorType.PLAYER;
        backpack = u.getVariable("player").getSFSObjectValue().getUtfString("backpack");
        xpWorth = 25;

        for (String k : stats.keySet()) {
            if (k.contains("PerLevel")) {
                String stat = k.replace("PerLevel", "");
                double levelStat = stats.get(k);
                if (k.equalsIgnoreCase("healthPerLevel")) {
                    setHealth(
                            (int) ((getMaxHealth() + levelStat) * getPHealth()),
                            (int) (getMaxHealth() + levelStat));
                } else if (k.contains("attackSpeed")) {
                    increaseStat(stat, (levelStat * -1));
                } else {
                    increaseStat(stat, levelStat);
                }
            }
        }

        Properties props = parentExt.getConfigProperties();
        invincibleDebug = Boolean.parseBoolean(props.getProperty("invincibleDebug", "false"));
        abilityDebug = Boolean.parseBoolean(props.getProperty("abilityDebug", "false"));
        movementDebug = Boolean.parseBoolean(props.getProperty("movementDebug", "false"));

        speedDebug = Boolean.parseBoolean(props.getProperty("speedDebug", "false"));
        damageDebug = Boolean.parseBoolean(props.getProperty("damageDebug", "false"));

        if (movementDebug) {
            ExtensionCommands.createActor(
                    parentExt, room, id + "_movementDebug", "creep1", location, 0f, 2);
        }

        if (speedDebug) setStat("speed", 20);
        if (damageDebug) setStat("attackDamage", 1000);
    }

    public boolean canPerformNewMove() {
        return canMove && !isDashingOrLeaping && !hasMovementCC();
    }

    @Override
    public void setStat(String stat, double value) {
        super.setStat(stat, value);
        if (!stat.toLowerCase().contains("sp") && !stat.equalsIgnoreCase("speed"))
            updateStatMenu(stat);
    }

    public void setAutoAttackEnabled(boolean enabled) {
        autoAttackEnabled = enabled;
    }

    public void setHasKeeothBuff(boolean hasBuff) {
        hasKeeothBuff = hasBuff;
    }

    public void setHasGooBuff(boolean hasBuff) {
        hasGooBuff = hasBuff;
    }

    public void setKeeothBuffStartTime(long keeothBuffStartTime) {
        this.keeothBuffStartTime = keeothBuffStartTime;
    }

    public void setGooBuffStartTime(long gooBuffStartTime) {
        this.gooBuffStartTime = gooBuffStartTime;
    }

    public void setCanCast(boolean q, boolean w, boolean e) {
        canCast[0] = q;
        canCast[1] = w;
        canCast[2] = e;
    }

    public void setLichHandTarget(Actor target) {
        lichHandTarget = target;
    }

    public Actor getLichHandTarget() {
        return lichHandTarget;
    }

    public void setLichHandStacks(int stacks) {
        lichHandStacks = stacks;
    }

    public int getLichHandStacks() {
        return lichHandStacks;
    }

    public void setLastLichHandStack(Long time) {
        lastLichHandStack = time;
    }

    public Long getLastLichHandStack() {
        return lastLichHandStack;
    }

    public void setLastSaiProcTime(Long time) {
        lastSaiProcTime = time;
    }

    public Long getLastSaiProcTime() {
        return lastSaiProcTime;
    }

    public void setLastZedronBuff(Long time) {
        lastZeldronBuff = time;
    }

    public Long getLastZeldronBuff() {
        return lastZeldronBuff;
    }

    public int getXp() {
        return xp;
    }

    public Map<String, Double> getStats() {
        return stats;
    }

    public boolean[] getCanCast() {
        return canCast;
    }

    public int getMultiKill() {
        return multiKill;
    }

    public int getKillingSpree() {
        return killingSpree;
    }

    public long getLastKilled() {
        return lastKilled;
    }

    public void setLastKilled(Long time) {
        lastKilled = time;
    }

    public List<UserActor> getKilledPlayers() {
        return killedPlayers;
    }

    public void setPath(Line2D path) {
        movementLine = path;
        timeTraveled = 0f;
    }

    public User getUser() {
        return player;
    }

    public boolean getIsDashing() {
        return isDashingOrLeaping;
    }

    public boolean getIsAutoAttacking() {
        return isAutoAttacking;
    }

    public void queueMovement(Point2D destination) {
        this.queuedMovementDestination = destination;
        Console.debugLog("Movement to: " + destination + " queued");
    }

    public void processQueueMovement() {
        if (this.queuedMovementDestination != null) {
            Point2D dest = this.queuedMovementDestination;
            this.queuedMovementDestination = null;

            MovementManager.handleMovementRequest(parentExt, this, dest);
        }
    }

    public void move(ISFSObject params, Point2D destination) {
        Point2D orig = new Point2D.Float(params.getFloat("orig_x"), params.getFloat("orig_z"));
        location = orig;
        movementLine = new Line2D.Float(orig, destination);
        timeTraveled = 0f;
        ExtensionCommands.moveActor(
                parentExt,
                room,
                id,
                location,
                destination,
                (float) getPlayerStat("speed"),
                params.getBool("orient"));
    }

    public void addHit(boolean dotDamage) {
        if (!dotDamage) hits++;
        else hits += 0.2d;
    }

    protected JsonNode getSpellData(int spell) {
        JsonNode actorDef = parentExt.getDefinition(avatar);
        return actorDef.get("MonoBehaviours").get("ActorData").get("spell" + spell);
    }

    public void preventStealth() {
        Console.debugLog("Prevent stealth");
        addState(ActorState.REVEALED, 0d, 3000);
        setState(ActorState.INVISIBLE, false);
        stealthEmbargo = System.currentTimeMillis() + 3000;
        if (roboStacks > 0) roboStacks = 0;
    }

    public void resetFightKingStacks() {
        Console.debugLog("Reset fight king stack");
        fightKingStacks = 0;
        ExtensionCommands.removeStatusIcon(parentExt, player, "fight_king_icon");
    }

    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        try {
            if (invincibleDebug) return false;
            if (dead) return true;
            if (a.getActorType() == ActorType.PLAYER) checkTowerAggro((UserActor) a);
            if (a.getActorType() == ActorType.COMPANION) {
                checkTowerAggroCompanion(a);
            }

            if (hpPickup) {
                removeHealthPackEffect();
            }
            if (a.getActorType() == ActorType.TOWER) {
                ExtensionCommands.playSound(
                        parentExt, room, id, "sfx_turret_shot_hits_you", location);
            }

            if (roboStacks == 3) {
                resetRoboStacks();
                if (isNeitherStructureNorAlly(a)) {
                    lastRoboEffect = System.currentTimeMillis();
                    addState(ActorState.SLOWED, ROBO_SLOW_VALUE, ROBO_SLOW_DURATION);
                }
            }

            AttackType type = getAttackType(attackData);
            preventStealth();
            double moonChance = ChampionData.getCustomJunkStat(this, "junk_3_battle_moon");
            if (moonChance > 0) {
                if (Math.random() < moonChance) {
                    Console.debugLog("Moon blocked damage! Chance: " + moonChance);
                    String moonS = "sfx_junk_battle_moon";

                    ExtensionCommands.playSound(parentExt, room, id, moonS, location);
                    return false;
                }
            }
            int newDamage = damage;
            if (a.getActorType() == ActorType.PLAYER) {
                UserActor ua = (UserActor) a;
                addDamageGameStat(ua, newDamage, type);
                if (type == AttackType.SPELL
                        && ChampionData.getJunkLevel(ua, "junk_1_fight_king_sword") > 0) {
                    newDamage += 15 * fightKingStacks;
                    ua.resetFightKingStacks();
                }
                double cubeEffect = ChampionData.getCustomJunkStat(ua, "junk_4_antimagic_cube");
                if (!effectHandlers.containsKey("spellDamage")
                        && type == AttackType.SPELL
                        && cubeEffect != -1) {
                    addEffect("spellDamage", cubeEffect, 5000);
                    // TODO: Add icon for this effect
                    // TODO: Bug, seems to not apply consistently. Especially with dot / constant
                    // abilities.
                }
                if (ChampionData.getJunkLevel(ua, "junk_2_peppermint_tank") > 0
                        && type == AttackType.SPELL) {
                    if (ua.getLocation().distance(location) < 2d) {
                        String item = "junk_2_peppermint_tank";
                        double junkStat = ChampionData.getCustomJunkStat(ua, item);
                        newDamage += (int) (newDamage * junkStat);
                    }
                }
                // handleElectrodeGun(ua, a, damage, attackData);

                if (maxHealth > ua.getMaxHealth()
                        && ChampionData.getJunkLevel(ua, "junk_3_globs_helmet") > 0) {
                    String item = "junk_3_globs_helmet";
                    double junkStat = ChampionData.getCustomJunkStat(ua, item);
                    newDamage += (int) (newDamage * junkStat);
                }

                if (type == AttackType.SPELL
                        && (spellShieldActive || System.currentTimeMillis() < iFrame)) {
                    if (spellShieldActive) triggerSpellShield();
                    return false;
                }
            }
            newDamage = getMitigatedDamage(newDamage, type, a);
            handleDamageTakenStat(type, newDamage);
            ExtensionCommands.damageActor(parentExt, room, id, newDamage);
            processHitData(a, attackData, newDamage);
            if (hasTempStat("healthRegen")) {
                effectHandlers.get("healthRegen").endAllEffects();
            }
            changeHealth(newDamage * -1);
            if (currentHealth > 0) return false;
            else {
                if (getClass() == Fionna.class) {
                    Fionna f = (Fionna) this;
                    if (f.ultActivated()) {
                        setHealth(1, (int) maxHealth);
                        return false;
                    }
                }
                if (futureCrystalActive
                        && ChampionData.getJunkLevel(this, "junk_4_future_crystal") > 0) {
                    if (Math.random()
                            < ChampionData.getCustomJunkStat(this, "junk_4_future_crystal")) {
                        futureCrystalActive = false;
                        int targetHealth = (int) Math.round(maxHealth * 0.3d);
                        changeHealth(targetHealth - getHealth());
                        return false;
                    }
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void triggerSpellShield() {
        spellShieldActive = false;
        spellShieldCooldown = System.currentTimeMillis() + 90000;
        ExtensionCommands.removeFx(parentExt, room, id + "_spellShield");
        ExtensionCommands.removeStatusIcon(parentExt, getUser(), "junk_4_grob_gob_glob_grod_name");
        iFrame = System.currentTimeMillis() + 500;
    }

    public double getAttackCooldown() {
        return attackCooldown;
    }

    public double handleGrassSwordProc(double damage) { // TODO: Add indicator or something
        return damage * GRASS_CRIT_INCREASE;
    }

    @Override
    public void attack(Actor a) {
        if (attackCooldown == 0) {
            applyStopMovingDuringAttack();
            preventStealth();
            setLastAuto();
            double critChance = getPlayerStat("criticalChance") / 100d;
            double random = Math.random();
            boolean crit = random < critChance;
            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    crit,
                    true);
            attackCooldown = getPlayerStat("attackSpeed");
            if (attackCooldown < BASIC_ATTACK_DELAY) attackCooldown = BASIC_ATTACK_DELAY;
            double damage = getPlayerStat("attackDamage");
            if (crit) {
                damage *= getPlayerStat("criticalDamage");
                damage = handleGrassSwordProc(damage);
            }
            Champion.DelayedAttack delayedAttack =
                    new Champion.DelayedAttack(parentExt, this, a, (int) damage, "basicAttack");
            try {
                String projectileFx =
                        parentExt
                                .getActorData(getAvatar())
                                .get("scriptData")
                                .get("projectileAsset")
                                .asText();
                if (projectileFx != null
                        && !projectileFx.isEmpty()
                        && !parentExt
                                .getActorData(avatar)
                                .get("attackType")
                                .asText()
                                .equalsIgnoreCase("MELEE")) {
                    parentExt
                            .getTaskScheduler()
                            .schedule(
                                    new RangedAttack(a, delayedAttack, projectileFx),
                                    BASIC_ATTACK_DELAY,
                                    TimeUnit.MILLISECONDS);
                } else {
                    parentExt
                            .getTaskScheduler()
                            .schedule(delayedAttack, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
                }

            } catch (NullPointerException e) {
                // e.printStackTrace();
                parentExt
                        .getTaskScheduler()
                        .schedule(delayedAttack, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void applyStopMovingDuringAttack() {
        if (parentExt.getActorData(getAvatar()).has("attackType")) {
            preventStealth();
            clearMovement();
            isAutoAttacking = true;

            Runnable resetFlagAndProcessQueue =
                    () -> {
                        isAutoAttacking = false;
                        processQueueMovement();
                    };

            scheduleTask(resetFlagAndProcessQueue, BASIC_ATTACK_DELAY);
        }
    }

    public void checkTowerAggro(UserActor ua) {
        if (isInTowerRadius(ua, false)) ua.changeTowerAggro = true;
    }

    public void checkTowerAggroCompanion(Actor a) {
        if (isInTowerRadius(a, false)) a.towerAggroCompanion = true;
    }

    public boolean isInTowerRadius(Actor a, boolean ownTower) {
        HashMap<String, Point2D> towers;
        List<Point2D> towerLocations = new ArrayList<>();
        HashMap<String, Point2D> baseTowers;
        String roomGroup = room.getGroupId();
        if (room.getGroupId().equalsIgnoreCase("practice")) {
            if (ownTower) {
                if (a.getTeam() == 1) {
                    towers = MapData.getPTowerActorData(1);
                    baseTowers = MapData.getBaseTowerData(1, roomGroup);
                } else {
                    towers = MapData.getPTowerActorData(0);
                    baseTowers = MapData.getBaseTowerData(0, roomGroup);
                }
            } else {
                if (a.getTeam() == 1) {
                    towers = MapData.getPTowerActorData(0);
                    baseTowers = MapData.getBaseTowerData(0, roomGroup);
                } else {
                    towers = MapData.getPTowerActorData(1);
                    baseTowers = MapData.getBaseTowerData(1, roomGroup);
                }
            }
        } else {
            if (ownTower) {
                if (a.getTeam() == 1) {
                    towers = MapData.getMainMapTowerData(1);
                    baseTowers = MapData.getBaseTowerData(1, roomGroup);
                } else {
                    towers = MapData.getMainMapTowerData(0);
                    baseTowers = MapData.getBaseTowerData(0, roomGroup);
                }
            } else {
                if (a.getTeam() == 1) {
                    towers = MapData.getMainMapTowerData(0);
                    baseTowers = MapData.getBaseTowerData(0, roomGroup);
                } else {
                    towers = MapData.getMainMapTowerData(1);
                    baseTowers = MapData.getBaseTowerData(1, roomGroup);
                }
            }
        }
        for (String key : baseTowers.keySet()) {
            towerLocations.add(baseTowers.get(key));
        }
        for (String key : towers.keySet()) {
            towerLocations.add(towers.get(key));
        }
        for (Point2D location : towerLocations) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            if (Champion.getActorsInRadius(handler, location, 6f).contains(a)) {
                return true;
            }
        }
        return false;
    }

    public Point2D dash(Point2D dest, boolean noClip, double dashSpeed) {
        /*isDashingOrLeaping = true;
        Point2D dashPoint = MovementManager.getDashPoint(this, new Line2D.Float(location, dest));
        if (dashPoint == null) dashPoint = location;
        if (movementDebug)
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "gnome_a",
                    id + "_test" + Math.random(),
                    5000,
                    (float) dashPoint.getX(),
                    (float) dashPoint.getY(),
                    false,
                    0,
                    0f);
        double time = dashPoint.distance(location) / dashSpeed;
        int timeMs = (int) (time * 1000d);
        stopMoving(timeMs);
        Runnable setIsDashing = () -> isDashingOrLeaping = false;
        parentExt.getTaskScheduler().schedule(setIsDashing, timeMs, TimeUnit.MILLISECONDS);
        ExtensionCommands.moveActor(
                parentExt, room, id, location, dashPoint, (float) dashSpeed, true);
        setLocation(dashPoint);
        target = null;
        return dashPoint;*/
        return new Point2D.Float(0, 0);
    }

    public void dash(Point2D dest, double dashSpeed) {
        isDashingOrLeaping = true;
        if (movementDebug)
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "gnome_a",
                    id + "_test" + Math.random(),
                    5000,
                    (float) dest.getX(),
                    (float) dest.getY(),
                    false,
                    0,
                    0f);
        double time = dest.distance(location) / dashSpeed;
        int timeMs = (int) (time * 1000d);
        stopMoving(timeMs);
        Runnable setIsDashing = () -> isDashingOrLeaping = false;
        parentExt.getTaskScheduler().schedule(setIsDashing, timeMs, TimeUnit.MILLISECONDS);
        ExtensionCommands.moveActor(parentExt, room, id, location, dest, (float) dashSpeed, true);
        setLocation(dest);
        target = null;
    }

    protected boolean handleAttack(Actor a) {
        if (attackCooldown == 0) {
            double critChance = getPlayerStat("criticalChance") / 100d;
            double random = Math.random();
            boolean crit = random < critChance;
            boolean critAnimation = crit;
            String[] skinsWithNoCritAnimation = {
                "princessbubblegum_skin_hoth", "princessbubblegum_skin_warrior"
            };
            for (String skin : skinsWithNoCritAnimation) {
                if (avatar.equals(skin)) {
                    critAnimation = false;
                    break;
                }
            }
            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    critAnimation,
                    true);
            attackCooldown = getPlayerStat("attackSpeed");
            preventStealth();
            setLastAuto();
            if (attackCooldown < BASIC_ATTACK_DELAY) attackCooldown = BASIC_ATTACK_DELAY;
            return crit;
        }
        return false;
    }

    public void autoAttack(Actor a) {
        attack(a);
    }

    public void reduceAttackCooldown() {
        attackCooldown -= 100;
        if (attackCooldown < 0) attackCooldown = 0;
    }

    protected boolean isNeitherStructureNorAlly(Actor a) {
        return a.getTeam() != team
                && a.getActorType() != ActorType.TOWER
                && a.getActorType() != ActorType.BASE;
    }

    protected boolean isNeitherTowerNorAlly(Actor a) {
        return a.getActorType() != ActorType.TOWER && a.getTeam() != team;
    }

    @Deprecated
    public void updateXPWorth(
            String event) { // Deprecating for now instead of removal in case we want to revisit
        // this mechanic
        switch (event) {
            case "kill":
                xpWorth += 5;
                break;
            case "death":
                if (xpWorth > 25) xpWorth = 25;
                else xpWorth -= 5;
                break;
            case "assist":
                xpWorth += 2;
                break;
        }
        if (xpWorth < 10) xpWorth = 10;
        else if (xpWorth > 50) xpWorth = 50;
    }

    @Override
    public void handleFear(Point2D source, int duration) {
        if (spellShieldActive || System.currentTimeMillis() < iFrame) {
            if (spellShieldActive) triggerSpellShield();
            return;
        }
        super.handleFear(source, duration);
    }

    @Override
    public void handlePull(Point2D source, double pullDistance) {
        if (spellShieldActive || System.currentTimeMillis() < iFrame) {
            if (spellShieldActive) triggerSpellShield();
            return;
        }
        super.handlePull(source, pullDistance);
    }

    @Override
    public void knockback(Point2D source, float distance) {
        if (spellShieldActive || System.currentTimeMillis() < iFrame) {
            if (spellShieldActive) triggerSpellShield();
            return;
        }
        super.knockback(source, distance);
    }

    @Override
    public void die(Actor a) {
        Console.debugLog(id + " has died! " + dead);
        try {
            if (dead) return;
            dead = true;
            // updateXPWorth("death");
            timeKilled = System.currentTimeMillis();
            canMove = false;
            if (!getState(ActorState.AIRBORNE)) stopMoving();
            if (hasKeeothBuff) disableKeeothBuff();
            if (hasGooBuff) disableGooBuff();

            if (a.getActorType() != ActorType.PLAYER) {
                ExtensionCommands.playSound(
                        parentExt, getUser(), "global", "announcer/you_are_defeated");
            }

            if (getState(ActorState.POLYMORPH)) {
                boolean swapAsset =
                        !getChampionName(getAvatar()).equalsIgnoreCase("marceline")
                                || !getState(ActorState.TRANSFORMED);
                if (swapAsset) {
                    ExtensionCommands.swapActorAsset(
                            parentExt, room, getId(), getSkinAssetBundle());
                }
                ExtensionCommands.removeFx(parentExt, room, id + "_statusEffect_polymorph");
                ExtensionCommands.removeFx(parentExt, room, id + "_flambit_aoe");
                ExtensionCommands.removeFx(parentExt, room, id + "_flambit_ring_");
                setState(ActorState.POLYMORPH, false);
            }
            setHealth(0, (int) maxHealth);
            target = null;
            killingSpree = 0;
            Actor realKiller = a;
            if (a.getActorType() != ActorType.PLAYER) {
                long lastAttacked = -1;
                UserActor lastAttacker = null;
                for (int i = 0; i < aggressors.size(); i++) {
                    Actor attacker = (Actor) aggressors.keySet().toArray()[i];
                    if (attacker.getActorType() == ActorType.PLAYER) {
                        long attacked = aggressors.get(attacker).getLong("lastAttacked");
                        if (lastAttacked == -1 || lastAttacked < attacked) {
                            lastAttacked = attacked;
                            lastAttacker = (UserActor) attacker;
                        }
                    }
                }
                if (lastAttacker != null) realKiller = lastAttacker;
            }
            ExtensionCommands.knockOutActor(
                    parentExt, room, String.valueOf(player.getId()), realKiller.getId(), deathTime);
            if (magicNailStacks > 0) {
                magicNailStacks /= 2;
                updateStatMenu("attackDamage");
            }
            if (lightningSwordStacks > 0) {
                lightningSwordStacks /= 2;
                updateStatMenu("spellDamage");
            }
            if (robeStacks > 0) {
                robeStacks /= 2;
                updateStatMenu("coolDownReduction");
            }
            try {
                ExtensionCommands.handleDeathRecap(
                        parentExt,
                        player,
                        id,
                        realKiller.getId(),
                        (HashMap<Actor, ISFSObject>) aggressors);
                increaseStat("deaths", 1);
                if (realKiller.getActorType() == ActorType.PLAYER) {
                    UserActor ua = (UserActor) realKiller;
                    ua.increaseStat("kills", 1);
                    parentExt.getRoomHandler(room.getName()).addScore(ua, ua.getTeam(), 25);
                }
                for (Actor actor : aggressors.keySet()) {
                    if (actor.getActorType() == ActorType.PLAYER
                            && !actor.getId().equalsIgnoreCase(realKiller.getId())) {
                        UserActor ua = (UserActor) actor;
                        // ua.updateXPWorth("assist");
                        ua.addXP(getXPWorth());
                        if (ChampionData.getJunkLevel(ua, "junk_5_ghost_pouch") > 0) {
                            ua.useGhostPouch();
                        }
                        ua.increaseStat("assists", 1);
                    }
                }
                // Set<String> buffKeys = activeBuffs.keySet();
            } catch (Exception e) {
                e.printStackTrace();
            }
            double timeDead = deathTime * 1000; // needs to be converted to ms for the client
            addGameStat("timeDead", timeDead);
            parentExt
                    .getTaskScheduler()
                    .schedule(new Champion.RespawnCharacter(this), deathTime, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /*
       Acceptable Keys:
       availableSpellPoints: Integer
       sp_category1
       sp_category2
       sp_category3
       sp_category4
       sp_category5
       kills
       deaths
       assists
       attackDamage
       attackSpeed
       armor
       speed
       spellResist
       spellDamage
       criticalChance
       criticalDamage*
       lifeSteal
       armorPenetration
       coolDownReduction
       spellVamp
       spellPenetration
       attackRange
       healthRegen
    */

    private void disableKeeothBuff() {
        hasKeeothBuff = false;
        ExtensionCommands.removeStatusIcon(parentExt, player, "keeoth_buff");
        ExtensionCommands.removeFx(parentExt, room, getId() + "_" + "jungle_buff_keeoth");
        String[] stats = {"lifeSteal", "spellVamp", "criticalChance"};
        updateStatMenu(stats);
    }

    private void disableGooBuff() {
        hasGooBuff = false;
        ExtensionCommands.removeStatusIcon(parentExt, player, "goomonster_buff");
        ExtensionCommands.removeFx(parentExt, room, getId() + "_" + "jungle_buff_goo");
        updateStatMenu("speed");
    }

    public void updateStat(String key, double value) {
        stats.put(key, value);
        ExtensionCommands.updateActorData(parentExt, room, id, key, getPlayerStat(key));
    }

    public void increaseStat(String key, double num) {
        // Console.debugLog("Increasing " + key + " by " + num);
        stats.put(key, stats.get(key) + num);
        ExtensionCommands.updateActorData(parentExt, room, id, key, getPlayerStat(key));
    }

    protected boolean
            canRegenHealth() { // TODO: Does not account for health pots. Not sure if this should be
        // added for balance reasons.
        // regen works while in combat
        return ((currentHealth < maxHealth || getPlayerStat("healthRegen") < 0)
                && ChampionData.getJunkLevel(this, "junk_1_ax_bass") < 1);
    }

    public void scheduleTask(Runnable task, int delay) {
        parentExt.getTaskScheduler().schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void update(int msRan) {
        handleDamageQueue();
        handleActiveEffects();

        if (dead) {
            if (currentHealth > 0 && System.currentTimeMillis() > timeKilled + (deathTime * 1500L))
                respawn();
            else return;
        }

        if (isMoving) {
            float SERVER_TICK_SECONDS = 0.1f;
            MovementManager.updateHitbox(this, SERVER_TICK_SECONDS);
        }

        if (movementDebug) {
            String id = this.id + "_movementDebug";
            ExtensionCommands.moveActor(parentExt, room, id, location, location, 5f, false);
        }

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        long currentTime = System.currentTimeMillis();

        handleSimonGlasses(rh);
        handleBattleMoon();
        handleGrobDevice(currentTime);
        handleFlameCloak();

        if (hits > 0) {
            hits -= 0.1d;
        } else if (hits < 0) hits = 0;

        if (location.distance(movementLine.getP2()) <= 0.01f) {
            idleTime += 100;
        }

        handleBrush(rh, currentTime);

        if (attackCooldown > 0) reduceAttackCooldown();

        if (target != null && (invisOrInBrush(target) || target.getHealth() <= 0)) {
            target = null;
        }

        if (target != null && target.getHealth() > 0) {

            if (withinRange(target) && canAttack()) {
                autoAttack(target);

            } else if (!withinRange(target) && canMove() && !isAutoAttacking) {
                MovementManager.handleMovementRequest(parentExt, this, target.getLocation());
            }
        }

        handleAutoTarget(rh, currentTime);

        if (msRan % 1000 == 0) {

            handleFlameCloakDamage(rh);
            handleRoboSuit(currentTime);
            handleFightKingDecay(currentTime);
            handleCosmicGauntletDecay(currentTime);

            if (canRegenHealth()) {
                regenHealth();
            }

            handleHealthPackEffectRemoval(currentTime);

            int newDeath = 10 + ((msRan / 1000) / 60);
            if (newDeath != deathTime) deathTime = newDeath;
            List<Actor> actorsToRemove = new ArrayList<>(aggressors.size());
            for (Actor a : aggressors.keySet()) {
                ISFSObject damageData = aggressors.get(a);
                if (currentTime > damageData.getLong("lastAttacked") + 5000) {
                    actorsToRemove.add(a);
                }
            }

            for (Actor a : actorsToRemove) {
                aggressors.remove(a);
            }

            handleUpdateMultiKill(currentTime);

            if (hasTempStat("healthRegen")) {
                if (currentHealth == maxHealth) {
                    effectHandlers.get("healthRegen").endAllEffects();
                }
            }
        }
        if (changeTowerAggro && !isInTowerRadius(this, false)) changeTowerAggro = false;

        if (hasKeeothBuff && currentTime - keeothBuffStartTime >= 90000) {
            disableKeeothBuff();
        }
        if (hasGooBuff && currentTime - gooBuffStartTime >= 90000) {
            disableGooBuff();
        }
        if (getState(ActorState.CHARMED) && charmer != null) {
            moveTowardsCharmer(charmer);
        }
    }

    private void handleSimonGlasses(RoomHandler rh) {
        String itemName = "junk_2_simon_petrikovs_glasses";

        for (UserActor ua : rh.getPlayers()) {
            boolean contains = simonGlassesBuffProviders.containsKey(ua);
            boolean isInRange =
                    ua.getLocation() != null
                            && location.distance(ua.getLocation()) <= SIMON_GLASSES_RANGE;

            boolean leveledGlasses = ChampionData.getJunkLevel(ua, itemName) > 0;

            if (ua.getTeam() == team
                    && leveledGlasses
                    && isInRange
                    && !contains
                    && !ua.equals(this)) {
                int pdValue = (int) ChampionData.getCustomJunkStat(ua, itemName);
                simonGlassesBuffProviders.put(ua, pdValue);
                glassesBuff += pdValue;

                String iconName = ua + "simon_glasses_buff";
                String desc = "Your Power Damage is increased by " + pdValue;

                ExtensionCommands.addStatusIcon(parentExt, getUser(), iconName, desc, itemName, 0f);
                updateStatMenu("spellDamage");
            }
        }

        Iterator<Map.Entry<UserActor, Integer>> iterator =
                simonGlassesBuffProviders.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UserActor, Integer> entry = iterator.next();
            UserActor userActor = entry.getKey();
            int pdValue = entry.getValue();
            int junkLevel = ChampionData.getJunkLevel(userActor, itemName);

            boolean isLocNull = userActor.getLocation() == null;

            if (isLocNull
                    || userActor.getLocation().distance(location) > SIMON_GLASSES_RANGE
                    || junkLevel < 1) {
                String iconName = userActor + "simon_glasses_buff";
                glassesBuff -= pdValue;

                updateStatMenu("spellDamage");

                ExtensionCommands.removeStatusIcon(parentExt, getUser(), iconName);
                iterator.remove();
            }
        }
    }

    private void handleBattleMoon() {
        if (!moonVfxActivated
                && hasBackpackItem("junk_3_battle_moon")
                && ChampionData.getJunkLevel(this, "junk_3_battle_moon") > 0) {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "fx_junk_battle_moon",
                    1000 * 60 * 15,
                    id + "_battlemoon",
                    true,
                    "Bip01 Head",
                    false,
                    false,
                    team);
            moonVfxActivated = true;
        } else if (moonVfxActivated && ChampionData.getJunkLevel(this, "junk_3_battle_moon") < 1) {
            moonVfxActivated = false;
            ExtensionCommands.removeFx(parentExt, room, id + "_battlemoon");
        }
    }

    private void handleGrobDevice(long currentTime) {
        if (!spellShieldActive
                && ChampionData.getJunkLevel(this, "junk_4_grob_gob_glob_grod") > 0
                && currentTime > spellShieldCooldown) {
            spellShieldActive = true;
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "spell_shield",
                    1000 * 60 * 15,
                    id + "_spellShield",
                    true,
                    "Bip001 Pelvis",
                    true,
                    false,
                    team);
            ExtensionCommands.addStatusIcon(
                    parentExt,
                    getUser(),
                    "junk_4_grob_gob_glob_grod_name",
                    "junk_4_grob_gob_glob_grod_mod3",
                    "junk_4_grob_gob_glob_grod",
                    0f);
        }
    }

    private void handleFlameCloak() {
        if (!flameCloakEffectActivated
                && ChampionData.getJunkLevel(this, "junk_4_flame_cloak") > 0) {
            ExtensionCommands.createActorFX(
                    parentExt,
                    room,
                    id,
                    "fx_target_ring_1.5",
                    1000 * 60 * 15,
                    id + "_flameCloak",
                    true,
                    "",
                    true,
                    true,
                    team);
            flameCloakEffectActivated = true;
        } else if (flameCloakEffectActivated
                && ChampionData.getJunkLevel(this, "junk_4_flame_cloak") == 0) {
            ExtensionCommands.removeFx(parentExt, room, id + "_flameCloak");
            flameCloakEffectActivated = false;
        }
    }

    private void handleFlameCloakDamage(RoomHandler rh) {
        if (ChampionData.getJunkLevel(this, "junk_4_flame_cloak") > 0) {
            for (Actor a : Champion.getActorsInRadius(rh, location, 1.5f)) {
                if (a.getTeam() != team && isNeitherStructureNorAlly(a)) {
                    a.addToDamageQueue(
                            this, maxHealth * 0.035d, ChampionData.getFlameCloakAttackData(), true);
                }
            }
        }
    }

    private void handleRoboSuit(long currentTime) {
        if (ChampionData.getJunkLevel(this, "junk_3_robo_suit") > 0) {
            boolean ready = currentTime - lastRoboEffect >= ROBO_CD;

            if (roboStacks < 3 && ready) {
                roboStacks++;
                updateStatMenu("speed");

                if (roboStacks == 3)
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            "statusEffect_speed",
                            1000 * 60 * 5,
                            id + "_roboSpeed",
                            true,
                            "",
                            true,
                            false,
                            team);
            }
        }
    }

    private void handleFightKingDecay(long currentTime) {
        double fightKingDecay = ChampionData.getCustomJunkStat(this, "junk_1_fight_king_sword");

        if (fightKingDecay != -1) {
            boolean effectEnd = currentTime - lastAuto >= fightKingDecay;

            if (fightKingStacks > 0 && effectEnd) {
                resetFightKingStacks();
            }
        }
    }

    private void handleCosmicGauntletDecay(long currentTime) {
        String item = "junk_2_cosmic_gauntlet";

        double cosmicGauntletDecay = ChampionData.getCustomJunkStat(this, item);

        if (cosmicGauntletDecay != -1 && currentTime - lastSpell >= cosmicGauntletDecay) {
            resetCosmicStacks();
        }
    }

    private void handleBrush(RoomHandler rh, long currentTime) {
        boolean insideBrush = false;
        boolean isPracticeMap = rh.isPracticeMap();
        ArrayList<Path2D> brushPaths = parentExt.getBrushPaths(isPracticeMap);

        for (Path2D brush : brushPaths) {
            if (brush.contains(location)) {
                insideBrush = true;
                break;
            }
        }
        if (insideBrush) {
            if (!states.get(ActorState.BRUSH)) {
                Console.debugLog("BRUSH STATE ENABLED");

                ExtensionCommands.changeBrush(
                        parentExt, room, id, parentExt.getBrushNum(location, brushPaths));
                setState(ActorState.BRUSH, true);
                if (stealthEmbargo <= currentTime) setState(ActorState.REVEALED, false);
            } else if (stealthEmbargo != -1 && stealthEmbargo <= currentTime) {
                setState(ActorState.REVEALED, false);
                stealthEmbargo = -1;
            }
        } else {
            if (states.get(ActorState.BRUSH)) {
                Console.debugLog("BRUSH STATE DISABLED");

                setState(ActorState.BRUSH, false);
                if (!getState(ActorState.INVISIBLE)) setState(ActorState.REVEALED, true);
                ExtensionCommands.changeBrush(parentExt, room, id, -1);
            } else if (!states.get(ActorState.REVEALED)
                    && !states.get(ActorState.INVISIBLE)
                    && !states.get(ActorState.STEALTH)) {
                setState(ActorState.REVEALED, true);
            }
        }
    }

    private void regenHealth() {
        double healthRegen = getPlayerStat("healthRegen");
        if (currentHealth + healthRegen <= 0) healthRegen = (currentHealth - 1) * -1;
        changeHealth((int) healthRegen);
    }

    private void handleHealthPackEffectRemoval(long currentTime) {
        if (hpPickup && currentTime - healthPackPickUpTime >= HP_DURATION) {
            hpPickup = false;
            updateStatMenu("healthRegen");
        }

        if (hpPickup && getHealth() == maxHealth) {
            removeHealthPackEffect();
        }
    }

    private void removeHealthPackEffect() {
        ExtensionCommands.removeFx(parentExt, room, id + "healthPackFX");
        hpPickup = false;
        updateStatMenu("healthRegen");
    }

    private void handleUpdateMultiKill(long currentTime) {
        if (currentTime - lastKilled >= 10000) {
            if (multiKill != 0) {
                if (hasGameStat("largestMulti")) {
                    double largestMulti = getGameStat("largestMulti");
                    if (multiKill > largestMulti) setGameStat("largestMulti", multiKill);
                } else {
                    setGameStat("largestMulti", multiKill);
                }
                multiKill = 0;
            }
        }
    }

    private void handleAutoTarget(RoomHandler rh, long currentTime) {
        boolean timeElapsed = currentTime - lastAutoTargetTime >= NEW_AUTO_TARGET_CD;

        if (autoAttackEnabled && timeElapsed && idleTime > 500) {
            Actor closestTarget = null;
            double closestDistance = 1000;

            int aggroRange = parentExt.getActorStats(avatar).get("aggroRange").asInt();

            for (Actor a : Champion.getActorsInRadius(rh, location, aggroRange)) {

                if (a.getTeam() != team && a.getLocation().distance(location) < closestDistance) {
                    closestDistance = a.getLocation().distance(location);
                    closestTarget = a;
                }
            }
            idleTime = 0;
            target = closestTarget;
            lastAutoTargetTime = currentTime;
        }
    }

    public void resetIdleTime() {
        idleTime = 0;
    }

    public boolean invisOrInBrush(Actor a) {
        ActorState[] states = {ActorState.INVISIBLE, ActorState.BRUSH};
        for (ActorState state : states) {
            if (a.getState(state)) return true;
        }
        return false;
    }

    public void useAbility(
            int ability,
            JsonNode spellData,
            int cooldown,
            int gCooldown,
            int castDelay,
            Point2D dest) {
        if (gCooldown > 0) {
            stopMoving(gCooldown);
            parentExt
                    .getTaskScheduler()
                    .schedule(new MovementStopper(true), castDelay, TimeUnit.MILLISECONDS);
        } else {
            stopMoving();
        }
        if (getClass() == UserActor.class) {
            String abilityString = "q";
            int abilityIndex = 0;
            if (ability == 2) {
                abilityString = "w";
                abilityIndex = 1;
            } else if (ability == 3) {
                abilityString = "e";
                abilityIndex = 2;
            }
            ExtensionCommands.actorAbilityResponse(
                    parentExt,
                    getUser(),
                    abilityString,
                    canCast[abilityIndex],
                    getReducedCooldown(cooldown),
                    gCooldown);
            if (canCast[abilityIndex]) {
                canCast[abilityIndex] = false;
                int finalAbilityIndex = abilityIndex;
                Runnable castReset = () -> canCast[finalAbilityIndex] = true;
                parentExt
                        .getTaskScheduler()
                        .schedule(castReset, getReducedCooldown(cooldown), TimeUnit.MILLISECONDS);
            }
        }
    }

    public boolean canDash() {
        return !getState(ActorState.ROOTED);
    }

    @Override
    public boolean canMove() {
        for (ActorState s :
                states.keySet()) { // removed CHARMED state from here to make charmer following
            // possible (I hope it doesn't break anything :))
            if (s == ActorState.ROOTED
                    || s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.AIRBORNE) {
                if (states.get(s)) return false;
            }
        }
        return canMove;
    }

    public boolean hasInterrupingCC() {
        ActorState[] states = {
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.POLYMORPH,
            ActorState.STUNNED,
            ActorState.AIRBORNE,
            ActorState.SILENCED
        };
        for (ActorState state : states) {
            if (getState(state)) return true;
        }
        return false;
    }

    public boolean hasDashAttackInterruptCC() {
        ActorState[] states = {
            ActorState.STUNNED,
            ActorState.CHARMED,
            ActorState.POLYMORPH,
            ActorState.FEARED,
            ActorState.SILENCED,
        };
        for (ActorState state : states) {
            if (getState(state)) return true;
        }
        return false;
    }

    public void setCanMove(boolean canMove) {
        this.canMove = canMove;
        if (canMove && states.get(ActorState.CHARMED)) move(movementLine.getP2());
    }

    public void resetTarget() {
        target = null;
        ExtensionCommands.setTarget(parentExt, player, id, "");
    }

    public void setState(ActorState[] states, boolean stateBool) {
        for (ActorState s : states) {
            this.states.put(s, stateBool);
            ExtensionCommands.updateActorState(parentExt, room, id, s, stateBool);
        }
    }

    public void setTarget(Actor a) {
        target = a;
        ExtensionCommands.setTarget(parentExt, player, id, a.getId());
        if (states.get(ActorState.CHARMED)) {
            if (canMove) move(movementLine.getP2());
        }
    }

    public boolean isState(ActorState state) {
        return states.get(state);
    }

    public boolean canUseAbility(int ability) {
        ActorState[] hinderingStates = {
            ActorState.POLYMORPH,
            ActorState.AIRBORNE,
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.SILENCED,
            ActorState.STUNNED
        };
        for (ActorState s : hinderingStates) {
            if (states.get(s)) return false;
        }
        return canCast[ability - 1];
    }

    public String getChampionName(String avatar) {
        String[] avatarComponents = avatar.split("_");
        if (avatarComponents.length > 1) {
            return avatarComponents[0];
        } else {
            return avatar;
        }
    }

    public boolean isCastingDashAbility(String avatar, int ability) { // all chars except fp
        String defaultAvatar = getChampionName(avatar);
        switch (defaultAvatar) {
            case "billy":
            case "cinnamonbun":
            case "peppermintbutler":
            case "finn":
            case "choosegoose":
                if (ability == 2) return true;
                break;
            case "fionna":
            case "gunter":
            case "rattleballs":
                if (ability == 1) return true;
                break;
            case "magicman":
                if (ability == 3 || ability == 2) return true;
                break;
        }
        return false;
    }

    public Line2D getMovementLine() {
        return movementLine;
    }

    public void stopMoving(int delay) {
        stopMoving();
        canMove = false;
        if (delay > 0) {
            TaskScheduler scheduler = parentExt.getTaskScheduler();
            scheduler.schedule(new MovementStopper(true), delay, TimeUnit.MILLISECONDS);
        } else canMove = true;
    }

    public float getRotation(Point2D dest) { // lmao
        double dx = dest.getX() - location.getX();
        double dy = dest.getY() - location.getY();
        double angleRad = Math.atan2(dy, dx);
        return (float) Math.toDegrees(angleRad) * -1 + 90f;
    }

    public void handlePolymorph(boolean enable, int duration) {
        if (enable) {
            handleSwapToPoly(duration);
        } else {
            handleSwapFromPoly();
        }
    }

    public void handleSwapToPoly(int duration) {
        addState(ActorState.SLOWED, 0.3d, duration);
        ExtensionCommands.swapActorAsset(parentExt, room, id, "flambit");
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "statusEffect_polymorph",
                1000,
                id + "_statusEffect_polymorph",
                true,
                "",
                true,
                false,
                team);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "flambit_aoe",
                3000,
                id + "_flambit_aoe",
                true,
                "",
                true,
                false,
                team);
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "fx_target_ring_2",
                3000,
                id + "_flambit_ring_",
                true,
                "",
                true,
                true,
                getOppositeTeam());
    }

    @Override
    public void addState(ActorState state, double delta, int duration) {
        if (spellShieldActive || System.currentTimeMillis() < iFrame) {
            ActorState[] ccStates = {
                ActorState.BRUSH,
                ActorState.CLEANSED,
                ActorState.IMMUNITY,
                ActorState.INVINCIBLE,
                ActorState.INVISIBLE,
                ActorState.REVEALED,
                ActorState.STEALTH,
                ActorState.TRANSFORMED
            };
            for (ActorState s : ccStates) {
                if (state == s) {
                    super.addState(state, delta, duration);
                    return;
                }
            }
            if (spellShieldActive) {
                triggerSpellShield();
            }
            return;
        }
        super.addState(state, delta, duration);
    }

    @Override
    public void addState(ActorState state, double delta, int duration, String fxId, String emit) {
        if (spellShieldActive || System.currentTimeMillis() < iFrame) {
            ActorState[] ccStates = {
                ActorState.BRUSH,
                ActorState.CLEANSED,
                ActorState.IMMUNITY,
                ActorState.INVINCIBLE,
                ActorState.INVISIBLE,
                ActorState.REVEALED,
                ActorState.STEALTH,
                ActorState.TRANSFORMED
            };
            for (ActorState s : ccStates) {
                if (state == s) {
                    super.addState(state, delta, duration, fxId, emit);
                    return;
                }
            }
            if (spellShieldActive) {
                triggerSpellShield();
            }
            return;
        }
        super.addState(state, delta, duration, fxId, emit);
    }

    public void handleSwapFromPoly() {
        String bundle = getSkinAssetBundle();
        ExtensionCommands.swapActorAsset(parentExt, room, id, bundle);
    }

    @Override
    public void handleCharm(UserActor charmer, int duration) {
        if (spellShieldActive || System.currentTimeMillis() < iFrame) {
            if (spellShieldActive) triggerSpellShield();
            return;
        }
        if (!states.get(ActorState.CHARMED) && !states.get(ActorState.IMMUNITY)) {
            this.charmer = charmer;
            addState(ActorState.CHARMED, 0d, duration);
        }
    }

    public void handleCyclopsHealing() {
        if (getHealth() != maxHealth && !hpPickup) {
            heal((int) (getMaxHealth() * 0.15d));
        }
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                getId(),
                "fx_health_regen",
                HP_DURATION,
                id + "healthPackFX",
                true,
                "",
                false,
                false,
                getTeam());
        hpPickup = true;
        healthPackPickUpTime = System.currentTimeMillis();
        updateStatMenu("healthRegen");
    }

    public void respawn() {
        Point2D respawnPoint = getRespawnPoint();
        Console.debugLog(
                displayName
                        + " Respawning at: "
                        + respawnPoint.getX()
                        + ","
                        + respawnPoint.getY()
                        + " for team "
                        + team);
        location = respawnPoint;
        futureCrystalActive = true;
        movementLine = new Line2D.Float(respawnPoint, respawnPoint);
        timeTraveled = 0f;
        canMove = true;
        setHealth((int) maxHealth, (int) maxHealth);
        dead = false;
        removeEffects();
        ExtensionCommands.snapActor(parentExt, room, id, location, location, false);
        ExtensionCommands.playSound(parentExt, room, id, "sfx/sfx_champion_respawn", location);
        ExtensionCommands.respawnActor(parentExt, room, id);
        addEffect("speed", 2d, 5000, "statusEffect_speed", "targetNode");
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "champion_respawn_effect",
                1000,
                id + "_respawn",
                true,
                "Bip001",
                false,
                false,
                team);
    }

    protected void basicAttackReset() {
        attackCooldown = 500;
    }

    public boolean enhanceCrit() {
        return hasBackpackItem("junk_1_grass_sword") && getStat("sp_category1") > 0;
    }

    private Point2D getRespawnPoint() {
        int teamNumber = parentExt.getRoomHandler(room.getName()).getTeamNumber(id, team);
        Point2D respawnPoint;
        boolean isPractice = parentExt.getRoomHandler(room.getName()).isPracticeMap();
        respawnPoint =
                isPractice
                        ? MapData.L1_PURPLE_SPAWNS[teamNumber]
                        : MapData.L2_PURPLE_SPAWNS[teamNumber];
        if (team == 1 && respawnPoint.getX() < 0)
            respawnPoint = new Point2D.Double(respawnPoint.getX() * -1, respawnPoint.getY());
        return respawnPoint;
    }

    public void addXP(int xp) {
        if (level != 10) {
            double glassesModifier =
                    ChampionData.getCustomJunkStat(this, "junk_5_glasses_of_nerdicon");
            if (glassesModifier > 0) {
                xp *= (1 + glassesModifier);
            }
            xp += xp;
            HashMap<String, Double> updateData = new HashMap<>(3);
            int level = ChampionData.getXPLevel(xp);
            if (level != this.level) {
                this.level = level;
                xp = ChampionData.getLevelXP(level - 1);
                updateData.put("level", (double) level);
                ExtensionCommands.playSound(parentExt, player, id, "sfx_level_up_beam");
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "level_up_beam",
                        1000,
                        id + "_levelUpBeam",
                        true,
                        "",
                        true,
                        false,
                        team);
                ChampionData.levelUpCharacter(parentExt, this);
            }
            updateData.put("xp", (double) xp);
            updateData.put("pLevel", getPLevel());
            ExtensionCommands.updateActorData(parentExt, room, id, updateData);
        }
    }

    public int getLevel() {
        return level;
    }

    public double getPLevel() {
        if (level == 10) return 0d;
        double lastLevelXP = ChampionData.getLevelXP(level - 1);
        double currentLevelXP = ChampionData.getLevelXP(level);
        double delta = currentLevelXP - lastLevelXP;
        return (xp - lastLevelXP) / delta;
    }

    private void processHitData(Actor a, JsonNode attackData, int damage) {
        if (a.getId().contains("turret"))
            a =
                    parentExt
                            .getRoomHandler(room.getName())
                            .getEnemyChampion(team, "princessbubblegum");
        if (a.getId().contains("skully"))
            a = parentExt.getRoomHandler(room.getName()).getEnemyChampion(team, "lich");
        String precursor = "attack";
        if (attackData.has("spellName")) precursor = "spell";
        if (aggressors.containsKey(a)) {
            aggressors.get(a).putLong("lastAttacked", System.currentTimeMillis());
            ISFSObject currentAttackData = aggressors.get(a);
            int tries = 0;
            for (String k : currentAttackData.getKeys()) {
                if (k.contains("attack")) {
                    ISFSObject attack0 = currentAttackData.getSFSObject(k);
                    if (attackData
                            .get(precursor + "Name")
                            .asText()
                            .equalsIgnoreCase(attack0.getUtfString("atkName"))) {
                        attack0.putInt("atkDamage", attack0.getInt("atkDamage") + damage);
                        aggressors.get(a).putSFSObject(k, attack0);
                        return;
                    } else tries++;
                }
            }
            String attackNumber = "";
            if (tries == 0) attackNumber = "attack1";
            else if (tries == 1) attackNumber = "attack2";
            else if (tries == 2) attackNumber = "attack3";
            ISFSObject attack1 = new SFSObject();
            attack1.putUtfString("atkName", attackData.get(precursor + "Name").asText());
            attack1.putInt("atkDamage", damage);
            String attackType = "physical";
            if (precursor.equalsIgnoreCase("spell") && isRegularAttack(attackData))
                attackType = "spell";
            attack1.putUtfString("atkType", attackType);
            attack1.putUtfString("atkIcon", attackData.get(precursor + "IconImage").asText());
            aggressors.get(a).putSFSObject(attackNumber, attack1);
        } else {
            ISFSObject playerData = new SFSObject();
            playerData.putLong("lastAttacked", System.currentTimeMillis());
            ISFSObject attackObj = new SFSObject();
            attackObj.putUtfString("atkName", attackData.get(precursor + "Name").asText());
            attackObj.putInt("atkDamage", damage);
            String attackType = "physical";
            if (precursor.equalsIgnoreCase("spell") && isRegularAttack(attackData))
                attackType = "spell";
            attackObj.putUtfString("atkType", attackType);
            attackObj.putUtfString("atkIcon", attackData.get(precursor + "IconImage").asText());
            playerData.putSFSObject("attack1", attackObj);
            aggressors.put(a, playerData);
        }
    }

    public boolean isRegularAttack(JsonNode attackData) {
        if (attackData.has("spellName")
                && attackData.get("spellName").asText().equalsIgnoreCase("rattleballs_spell_1_name")
                && attackData.has("counterAttack")) {
            return false;
        }
        String[] spellNames = {"princess_bubblegum_spell_2_name", "lich_spell_4_name"};
        for (String name : spellNames) {
            if (attackData.has("spellName")
                    && attackData.get("spellName").asText().equalsIgnoreCase(name)) return false;
        }
        return true;
    }

    protected HashMap<String, Double> initializeStats() {
        HashMap<String, Double> stats = new HashMap<>();
        stats.put("availableSpellPoints", 1d);
        for (int i = 1; i < 6; i++) {
            stats.put("sp_category" + i, 0d);
        }
        stats.put("kills", 0d);
        stats.put("deaths", 0d);
        stats.put("assists", 0d);
        JsonNode actorStats = parentExt.getActorStats(getAvatar());
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k, actorStats.get(k).asDouble());
        }
        return stats;
    }

    public String getBackpack() {
        return backpack;
    }

    public boolean hasBackpackItem(String item) {
        String[] items = ChampionData.getBackpackInventory(parentExt, backpack);
        for (String i : items) {
            if (i.equalsIgnoreCase(item)) return true;
        }
        return false;
    }

    protected int getReducedCooldown(double cooldown) {
        if (abilityDebug) return 0;
        double cooldownReduction = getPlayerStat("coolDownReduction");
        double ratio = 1 - (cooldownReduction / 100);
        return (int) Math.round(cooldown * ratio);
    }

    public void handleSpellVamp(double damage, boolean dotDamage) {
        double spellVamp = getPlayerStat("spellVamp");
        if (hits != 0) {
            if (dotDamage) spellVamp /= hits;
            else spellVamp /= (hits * 2);
        }
        if (getPlayerStat("spellVamp") * 0.3 > spellVamp)
            spellVamp = getPlayerStat("spellVamp") * 0.3d;
        double percentage = spellVamp / 100;
        int healing = (int) Math.round(damage * percentage);
        // Console.debugLog(displayName + " is healing for " + healing + " HP!");
        changeHealth(healing);
    }

    public void handleLifeSteal() {
        double damage = getPlayerStat("attackDamage");
        double lifesteal = getPlayerStat("lifeSteal") / 100;
        changeHealth((int) Math.round(damage * lifesteal));
    }

    @Override
    public double getPlayerStat(String stat) {
        if (stat.equalsIgnoreCase("healthRegen")) {
            if (hpPickup) return super.getPlayerStat(stat) + HEALTH_PACK_REGEN;
        }
        if (stat.equalsIgnoreCase("attackDamage")) {
            double attackDamage = super.getPlayerStat(stat);
            if (dcBuff == 2) attackDamage *= DC_AD_BUFF;
            attackDamage += (DEMON_SWORD_AD_BUFF * getMonsterBuffCount(stat));
            return attackDamage + magicNailStacks;

        } else if (stat.equalsIgnoreCase("armor")) {
            double armor = super.getPlayerStat(stat);
            if (dcBuff >= 1) armor *= DC_ARMOR_BUFF;
            return armor + (5 * getMonsterBuffCount(stat));

        } else if (stat.equalsIgnoreCase("spellResist")) {
            double mr = super.getPlayerStat(stat);
            if (dcBuff >= 1) mr *= DC_SPELL_RESIST_BUFF;
            return mr + (5 * getMonsterBuffCount(stat));

        } else if (stat.equalsIgnoreCase("speed")) {
            double speedBoost = SPEED_BOOST_PER_ROBO_STACK * roboStacks; // TODO: Make scalable
            if (speedBoost < 0) speedBoost = 0;
            if (dcBuff >= 1) return super.getPlayerStat(stat) * DC_SPEED_BUFF + speedBoost;
            return super.getPlayerStat(stat) + speedBoost;

        } else if (stat.equalsIgnoreCase("spellDamage")) {
            double spellDamage = super.getPlayerStat(stat);
            if (dcBuff == 2) spellDamage *= DC_PD_BUFF;
            if (glassesBuff != 0) spellDamage += glassesBuff;
            return spellDamage
                    + lightningSwordStacks
                    + (DEMON_SWORD_SD_BUFF * getMonsterBuffCount(stat));

        } else if (stat.equalsIgnoreCase("coolDownReduction")) {
            return super.getPlayerStat(stat) + robeStacks;
        }
        return super.getPlayerStat(stat);
    }

    public void resetRoboStacks() {
        roboStacks = 0;
        ExtensionCommands.removeFx(parentExt, room, id + "_roboSpeed");
        updateStatMenu("speed");
    }

    public void useGhostPouch() {
        double healfactor = ChampionData.getCustomJunkStat(this, "junk_5_ghost_pouch");
        for (UserActor ua :
                Champion.getUserActorsInRadius(
                        parentExt.getRoomHandler(room.getName()), location, 5f)) {
            if (ua.getTeam() == team && !ua.getId().equalsIgnoreCase(id)) {
                Console.debugLog("Healed player from ghost pouch!");
                ua.changeHealth((int) (ua.maxHealth * healfactor));
                // TODO: Add effect / SFX
            }
        }
    }

    protected void handleMonsterBuff(Monster m) {
        Monster.BuffType buff = m.getBuffType();
        if (buff != Monster.BuffType.NONE) {
            activeMonsterBuffs.add(buff);
            Champion.handleStatusIcon(
                    parentExt, this, m.getAvatar(), m.getBuffDescription(), 1000 * 60);
            Runnable removeBuff =
                    () -> {
                        Console.debugLog("Removed monster buff");
                        activeMonsterBuffs.remove(buff);
                        Console.debugLog(activeMonsterBuffs);
                        updateMonsterStatMenu(buff);
                    };
            SmartFoxServer.getInstance()
                    .getTaskScheduler()
                    .schedule(removeBuff, 60, TimeUnit.SECONDS);
        }
        updateMonsterStatMenu(buff);
    }

    protected void updateMonsterStatMenu(Monster.BuffType buff) {
        switch (buff) {
            case OWL:
            case GNOME:
                updateStatMenu("attackDamage");
                updateStatMenu("spellDamage");
                break;
            case BEAR:
            case WOLF:
                updateStatMenu("spellResist");
                updateStatMenu("armor");
                break;
        }
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        if (a.getActorType() == ActorType.PLAYER || a instanceof Bot) {
            killingSpree++;
            multiKill++;
            lastKilled = System.currentTimeMillis();
        }
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor killedUA = (UserActor) a;
            killedPlayers.add(killedUA);
            if (hasGameStat("spree")) {
                double endGameSpree = getGameStat("spree");
                if (killingSpree > endGameSpree) {
                    endGameStats.put("spree", (double) killingSpree);
                }
            } else {
                endGameStats.put("spree", (double) killingSpree);
            }
            if (ChampionData.getJunkLevel(this, "junk_5_ghost_pouch") > 0) {
                useGhostPouch();
            }
            if (ChampionData.getJunkLevel(this, "junk_1_ax_bass") > 0
                    && a.getActorType() == ActorType.PLAYER) {
                changeHealth((int) Math.round(maxHealth * 0.15d));
            }
            if (ChampionData.getJunkLevel(this, "junk_1_night_sword") > 0) {
                setState(ActorState.REVEALED, false);
                addState(ActorState.INVISIBLE, 0d, 2000);
                Runnable reveal =
                        () -> {
                            if (!getState(ActorState.BRUSH)) setState(ActorState.REVEALED, true);
                        };
                SmartFoxServer.getInstance()
                        .getTaskScheduler()
                        .schedule(reveal, 2000, TimeUnit.MILLISECONDS);
            }
        }
        if (ChampionData.getJunkLevel(this, "junk_1_magic_nail") > 0) addMagicNailStacks(a);
        if (ChampionData.getJunkLevel(this, "junk_2_lightning_sword") > 0)
            addLightningSwordStacks(a);
        if (ChampionData.getJunkLevel(this, "junk_4_wizard_robe") > 0) addRobeStacks(a);

        int additionalXP = 0;
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) a;
            int levelDiff = ua.getLevel() - level;
            if (levelDiff > 0) additionalXP = 15 * levelDiff;
        } else if (a.getActorType() == ActorType.MONSTER) {
            if (ChampionData.getJunkLevel(this, "junk_1_demon_blood_sword") > 0) {
                additionalXP += ((double) a.getXPWorth() * 0.15d);
                Monster m = (Monster) a;
                handleMonsterBuff(m);
            }

        } else if (a.getActorType() == ActorType.MINION) {
            if (ChampionData.getJunkLevel(this, "junk_1_grape_juice_sword") > 0) {
                additionalXP += ((double) a.getXPWorth() * 0.1d);
            }
        }
        addXP(a.getXPWorth() + additionalXP);
        // if (a.getActorType() == ActorType.PLAYER) updateXPWorth("kill");
        if (a.getActorType() == ActorType.TOWER) {
            for (UserActor ua : parentExt.getRoomHandler(room.getName()).getPlayers()) {
                if (ua.getTeam() == team && !ua.getId().equalsIgnoreCase(id)) {
                    ua.addXP(a.getXPWorth() + additionalXP);
                }
            }
            return;
        }
        for (Actor actor :
                Champion.getActorsInRadius(
                        parentExt.getRoomHandler(room.getName()), location, 8f)) {
            if (actor.getActorType() == ActorType.PLAYER
                    && !actor.getId().equalsIgnoreCase(id)
                    && actor.getTeam() == team
                    && a.getActorType() != ActorType.PLAYER) {
                UserActor ua = (UserActor) actor;
                ua.addXP(a.getXPWorth());
            }
        }
    }

    private void addMagicNailStacks(Actor killedActor) {
        int pointsPutIntoNail = (int) getStat("sp_category1");
        int amountOfStacks =
                killedActor.getActorType() == ActorType.PLAYER
                        ? NAIL_STACKS_PER_CHAMP
                        : NAIL_STACKS_PER_NON_CHAMPS;
        int stackCap = pointsPutIntoNail * DAMAGE_PER_NAIL_POINT;

        if (pointsPutIntoNail > 0) {
            if (magicNailStacks + amountOfStacks > stackCap) magicNailStacks = stackCap;
            else magicNailStacks += amountOfStacks;
            updateStatMenu("attackDamage");
        }
    }

    private void addLightningSwordStacks(Actor killedActor) {
        int pointsPutIntoNail = ChampionData.getJunkLevel(this, "junk_2_lightning_sword");
        int amountOfStacks =
                killedActor.getActorType() == ActorType.PLAYER
                        ? LIGHTNING_SWORD_STACKS_PER_CHAMP
                        : LIGHTNING_SWORD_STACKS_PER_NON_CHAMP;
        int stackCap = pointsPutIntoNail * DAMAGE_PER_LIGHTNING_POINT;

        if (pointsPutIntoNail > 0) {
            if (lightningSwordStacks + amountOfStacks > stackCap) lightningSwordStacks = stackCap;
            else lightningSwordStacks += amountOfStacks;
            updateStatMenu("spellDamage");
        }
    }

    private void addRobeStacks(Actor ka) {
        int pointsPutIntoRobe = ChampionData.getJunkLevel(this, "junk_4_wizard_robe");
        boolean champOrJgBoss =
                ka instanceof UserActor || ka instanceof Keeoth || ka instanceof GooMonster;

        double amountOfStacks = champOrJgBoss ? ROBE_CD_CHAMP_OR_JG_BOSS_KO : ROBE_CD_MINION_KO;
        int stackCap = pointsPutIntoRobe * CDR_PER_ROBE_POINT;

        if (pointsPutIntoRobe > 0) {
            if (robeStacks + amountOfStacks > stackCap) robeStacks = stackCap;
            else robeStacks += amountOfStacks;
            Console.debugLog("Robe stacks: " + robeStacks);
            updateStatMenu("coolDownReduction");
        }
    }

    public void addGameStat(String stat, double value) {
        if (endGameStats.containsKey(stat)) endGameStats.put(stat, endGameStats.get(stat) + value);
        else setGameStat(stat, value);
    }

    public void setGameStat(String stat, double value) {
        endGameStats.put(stat, value);
    }

    public void addDamageGameStat(UserActor ua, double value, AttackType type) {
        super.addDamageGameStat(ua, value, type);
        ua.addGameStat("damageDealtChamps", value);
    }

    public void handleDamageTakenStat(AttackType type, double value) {
        addGameStat("damageReceivedTotal", value);
        if (type == AttackType.PHYSICAL) addGameStat("damageReceivedPhysical", value);
        else addGameStat("damageReceivedSpell", value);
    }

    public double getGameStat(String stat) {
        return endGameStats.get(stat);
    }

    public boolean hasGameStat(String stat) {
        return endGameStats.containsKey(stat);
    }

    public int getSpellDamage(JsonNode attackData, boolean singleTarget) {
        try {
            int damage =
                    (int)
                            Math.round(
                                    attackData.get("damage").asDouble()
                                            + (getPlayerStat("spellDamage")
                                                    * attackData.get("damageRatio").asDouble()));
            if (ChampionData.getJunkLevel(this, "junk_2_demonic_wishing_eye") > 0) {
                double chance = getPlayerStat("criticalChance") / 100d;
                if (!singleTarget) chance /= 2d;
                if (Math.random() < chance) {
                    Console.debugLog("Ability crit! Chance: " + chance);
                    damage *= 1.25;
                    ExtensionCommands.playSound(parentExt, room, "", "sfx/sfx_map_ping", location);
                }
            }
            return damage;
        } catch (Exception e) {
            e.printStackTrace();
            return attackData.get("damage").asInt();
        }
    }

    public void fireProjectile(
            Projectile projectile, Point2D location, Point2D dest, float abilityRange) {
        double x = location.getX();
        double y = location.getY();
        double dx = dest.getX() - location.getX();
        double dy = dest.getY() - location.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        double unitX = dx / length;
        double unitY = dy / length;
        double extendedX = x + abilityRange * unitX;
        double extendedY = y + abilityRange * unitY;
        Point2D lineEndPoint = new Point2D.Double(extendedX, extendedY);
        double speed =
                parentExt.getActorStats(projectile.getProjectileAsset()).get("speed").asDouble();
        ExtensionCommands.createProjectile(
                parentExt,
                room,
                this,
                projectile.getId(),
                projectile.getProjectileAsset(),
                location,
                lineEndPoint,
                (float) speed);
        parentExt.getRoomHandler(room.getName()).addProjectile(projectile);
    }

    public void fireMMProjectile(
            Projectile projectile, Point2D location, Point2D dest, float abilityRange) {
        double x = location.getX();
        double y = location.getY();
        double dx = dest.getX() - location.getX();
        double dy = dest.getY() - location.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        double unitX = dx / length;
        double unitY = dy / length;
        double extendedX = x + abilityRange * unitX;
        double extendedY = y + abilityRange * unitY;
        Point2D lineEndPoint = new Point2D.Double(extendedX, extendedY);
        double speed =
                parentExt.getActorStats(projectile.getProjectileAsset()).get("speed").asDouble();
        ExtensionCommands.createProjectile(
                parentExt,
                room,
                this,
                projectile.getId(),
                projectile.getProjectileAsset(),
                location,
                lineEndPoint,
                (float) speed);
        parentExt.getRoomHandler(room.getName()).addProjectile(projectile);
    }

    public void handleDCBuff(int teamSizeDiff, boolean removeSecondBuff) {
        String[] stats = {"armor", "spellResist", "speed"};
        String[] stats2 = {"attackDamage", "spellDamage"};
        if (removeSecondBuff) {
            dcBuff = 1;
            ExtensionCommands.updateActorData(parentExt, room, id, getPlayerStats(stats2));
            ExtensionCommands.removeStatusIcon(parentExt, player, "DC Buff #2");
            ExtensionCommands.removeFx(parentExt, room, id + "_dcbuff2");
            return;
        }
        switch (teamSizeDiff) {
            case 0:
                dcBuff = 0;
                ExtensionCommands.updateActorData(parentExt, room, id, getPlayerStats(stats));
                ExtensionCommands.removeStatusIcon(parentExt, player, "DC Buff #1");
                ExtensionCommands.removeFx(parentExt, room, id + "_dcbuff1");
                break;
            case 1:
            case -1:
                dcBuff = 1;
                ExtensionCommands.updateActorData(parentExt, room, id, getPlayerStats(stats));
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        "DC Buff #1",
                        "Some coward left the battle! Here's something to help even the playing field!",
                        "icon_parity",
                        0);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "disconnect_buff_duo",
                        1000 * 15 * 60,
                        id + "_dcbuff1",
                        true,
                        "",
                        false,
                        false,
                        team);
                break;
            case 2:
            case -2:
                dcBuff = 2;
                ExtensionCommands.updateActorData(parentExt, room, id, getPlayerStats(stats2));
                ExtensionCommands.addStatusIcon(
                        parentExt,
                        player,
                        "DC Buff #2",
                        "You're the last one left, finish the mission",
                        "icon_parity2",
                        0);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "disconnect_buff_solo",
                        1000 * 15 * 60,
                        id + "_dcbuff2",
                        true,
                        "",
                        false,
                        false,
                        team);
                break;
        }
    }

    private HashMap<String, Double> getPlayerStats(String[] stats) {
        HashMap<String, Double> playerStats = new HashMap<>(stats.length);
        for (String s : stats) {
            playerStats.put(s, getPlayerStat(s));
        }
        return playerStats;
    }

    public void updateStatMenu(String stat) {
        // Console.debugLog("Updating stat menu: " + stat + " with " + getPlayerStat(stat));
        ExtensionCommands.updateActorData(parentExt, room, id, stat, getPlayerStat(stat));
    }

    protected void updateStatMenu(String[] stats) {
        for (String s : stats) {
            ExtensionCommands.updateActorData(parentExt, room, id, s, getPlayerStat(s));
        }
    }

    public void cleanseEffects() {
        ActorState[] cleansedStats = {
            ActorState.SLOWED,
            ActorState.STUNNED,
            ActorState.STUNNED,
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.BLINDED,
            ActorState.ROOTED,
            ActorState.CLEANSED
        };
        for (ActorState s : cleansedStats) {
            if (effectHandlers.containsKey(s.toString()))
                effectHandlers.get(s.toString()).endAllEffects();
        }
    }

    public void destroy() {
        dead = true;
        ExtensionCommands.destroyActor(parentExt, room, id);
    }

    public void setLastAuto() {
        lastAuto = System.currentTimeMillis();
        if (ChampionData.getJunkLevel(this, "junk_1_fight_king_sword") > 0) {
            if (fightKingStacks > 0)
                ExtensionCommands.removeStatusIcon(parentExt, player, "fight_king_icon");
            ExtensionCommands.addStatusIcon(
                    parentExt,
                    player,
                    "fight_king_icon",
                    "Your next ability is enhanced!",
                    "junk_1_fight_king_sword",
                    (int) ChampionData.getCustomJunkStat(this, "junk_1_fight_king_sword"));
            fightKingStacks++;
        }
    }

    public void setLastSpell() {
        lastSpell = System.currentTimeMillis();
        if (ChampionData.getJunkLevel(this, "junk_2_cosmic_gauntlet") > 0) {
            if (cosmicStacks > 0)
                ExtensionCommands.removeStatusIcon(parentExt, player, "cosmic_gauntlet_icon");
            cosmicStacks++;
            ExtensionCommands.addStatusIcon(
                    parentExt,
                    player,
                    "cosmic_gauntlet_icon",
                    "Your next attack is empowered!",
                    "junk_2_cosmic_gauntlet",
                    (int) ChampionData.getCustomJunkStat(this, "junk_2_cosmic_gauntlet"));
        }
    }

    public int getCosmicStacks() {
        return cosmicStacks;
    }

    public void resetCosmicStacks() {
        cosmicStacks = 0;
        ExtensionCommands.removeStatusIcon(parentExt, player, "cosmic_gauntlet_icon");
    }

    public void logExceptionMessage(String avatar, int spellNum) {
        String characterName = getChampionName(avatar).toUpperCase();
        String message =
                String.format(
                        "EXCEPTION OCCURED DURING ABILITY EXECUTION! CHARACTER: %s, ABILITY: %d",
                        characterName, spellNum);
        Console.logWarning(message);
    }

    public void clearIconHandlers() {
        Set<String> iconNames = new HashSet<>(iconHandlers.keySet());
        for (String i : iconNames) {
            ExtensionCommands.removeStatusIcon(parentExt, player, i);
            iconHandlers.get(i).cancel(true);
        }
        iconHandlers = new HashMap<>();
    }

    @Override
    public void removeEffects() {
        super.removeEffects();
        clearIconHandlers();
    }

    public void addIconHandler(String iconName, ScheduledFuture<?> handler) {
        iconHandlers.put(iconName, handler);
    }

    public void removeIconHandler(String iconName) {
        iconHandlers.remove(iconName);
    }

    public void handleNumbChuckStacks(Actor a) {
        if (numbChuckVictim == null) {
            numbChuckVictim = a.getId();
            numbSlow = true;
            return;
        }
        if (a.getId().equalsIgnoreCase(numbChuckVictim)) {
            if (numbSlow) {
                a.addState(
                        ActorState.SLOWED,
                        ChampionData.getCustomJunkStat(this, "junk_1_numb_chucks"),
                        1500);
                Console.debugLog("Numb Chuck slow applied!");
            }
            numbSlow = !numbSlow;
        } else {
            numbChuckVictim = a.getId();
            numbSlow = true;
        }
    }

    public boolean hasGlassesPoint() {
        return hasGlassesPoint;
    }

    public void setGlassesPoint(boolean val) {
        hasGlassesPoint = val;
    }

    @Override
    public void heal(int delta) {
        if (ChampionData.getJunkLevel(this, "junk_1_ax_bass") > 0) return;
        super.heal(delta);
    }

    public int getMonsterBuffCount(String stat) {
        int count = 0;
        for (Monster.BuffType buff : activeMonsterBuffs) {
            switch (stat) {
                case "attackDamage":
                case "spellDamage":
                    if (buff == Monster.BuffType.OWL || buff == Monster.BuffType.GNOME) count++;
                    break;
                case "armor":
                case "spellResist":
                    if (buff == Monster.BuffType.BEAR || buff == Monster.BuffType.WOLF) count++;
                    break;
            }
        }
        // Console.debugLog("Monster count: " + count);
        return count;
    }

    protected class MovementStopper implements Runnable {

        boolean move;

        public MovementStopper(boolean move) {
            this.move = move;
        }

        @Override
        public void run() {
            canMove = move;
        }
    }

    protected class RangedAttack implements Runnable {

        Actor target;
        Runnable attackRunnable;
        String projectile;
        String emitNode;

        public RangedAttack(Actor target, Runnable attackRunnable, String projectile) {
            this.target = target;
            this.attackRunnable = attackRunnable;
            this.projectile = projectile;
        }

        public RangedAttack(
                Actor target, Runnable attackRunnable, String projectile, String emitNode) {
            this.target = target;
            this.attackRunnable = attackRunnable;
            this.projectile = projectile;
            this.emitNode = emitNode;
        }

        @Override
        public void run() {
            String emit = "Bip01";
            if (emitNode != null) emit = emitNode;
            float time = (float) (target.getLocation().distance(location) / 10f);
            ExtensionCommands.createProjectileFX(
                    parentExt, room, projectile, id, target.getId(), emit, "targetNode", time);
            parentExt
                    .getTaskScheduler()
                    .schedule(attackRunnable, (int) (time * 1000), TimeUnit.MILLISECONDS);
        }
    }
}
