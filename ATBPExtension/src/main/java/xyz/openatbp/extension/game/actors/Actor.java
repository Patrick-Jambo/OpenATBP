package xyz.openatbp.extension.game.actors;

import static xyz.openatbp.extension.game.actors.UserActor.E_GUN_STACK_CD;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.*;
import xyz.openatbp.extension.game.champions.IceKing;
import xyz.openatbp.extension.pathfinding.PathFinder;

public abstract class Actor {
    protected static final float KNOCKBACK_SPEED = 11;
    protected static final int FEAR_MOVING_DISTANCE = 3; // should be close to og, subject to change

    public enum AttackType {
        PHYSICAL,
        SPELL
    }

    protected double currentHealth;
    protected double maxHealth;
    protected Point2D location;
    protected Line2D movementLine;
    protected boolean dead = false;
    protected float timeTraveled;
    protected String id;
    protected Room room;
    protected int team;
    protected String avatar;
    protected ATBPExtension parentExt;
    protected int level = 1;
    protected boolean canMove = true;
    protected double attackCooldown;
    protected ActorType actorType;
    protected Map<ActorState, Boolean> states = Champion.getBlankStates();
    protected String displayName = "FuzyBDragon";
    protected Map<String, Double> stats;
    protected List<ISFSObject> damageQueue = new ArrayList<>();
    protected Actor target;
    protected List<Point2D> path;
    protected int pathIndex = 1;
    protected int xpWorth;
    protected String bundle;
    protected boolean towerAggroCompanion = false;
    protected Map<String, EffectHandler> effectHandlers = new HashMap<>();
    protected Map<String, FxHandler> fxHandlers = new HashMap<>();
    protected UserActor charmer;
    protected boolean isDashing = false;

    protected Point2D moveStartPoint;
    protected Point2D moveDestination;
    protected List<Point2D> movePointsToDest;
    protected int movePointsIndex = 0;

    protected long elapsedMoveTimeMs;
    protected long totalMoveTimeMs;
    protected int visualTargetIndex = 0;

    protected boolean isMoving = false;
    protected float moveSpeed = 3;

    protected boolean pickedUpHealthPack = false;
    protected Long healthPackPickUpTime;

    protected static final int BASIC_ATTACK_DELAY = 500;

    public double getPHealth() {
        return currentHealth / maxHealth;
    }

    public int getHealth() {
        return (int) currentHealth;
    }

    public int getMaxHealth() {
        return (int) maxHealth;
    }

    public Point2D getLocation() {
        return this.location;
    }

    public String getId() {
        return this.id;
    }

    public int getTeam() {
        return this.team;
    }

    public int getOppositeTeam() {
        if (this.getTeam() == 1) return 0;
        else return 1;
    }

    public void setLocation(Point2D location) {
        this.location = location;
        this.movementLine = new Line2D.Float(location, location);
        this.timeTraveled = 0f;
    }

    public String getAvatar() {
        return this.avatar;
    }

    public ActorType getActorType() {
        return this.actorType;
    }

    public void reduceAttackCooldown() {
        this.attackCooldown -= 100;
    }

    public boolean withinRange(Actor a) {
        if (a.getActorType() == ActorType.BASE)
            return a.getLocation().distance(this.location) - 1.5f
                    <= this.getPlayerStat("attackRange");
        return a.getLocation().distance(this.location) <= this.getPlayerStat("attackRange");
    }

    public void stopMoving() {
        isMoving = false;
        moveStartPoint = location;
        moveDestination = location;
        movePointsToDest = new ArrayList<>();
        movePointsIndex = 0;
        elapsedMoveTimeMs = 0;
        totalMoveTimeMs = 0;
        visualTargetIndex = 0;

        ExtensionCommands.moveActor(parentExt, room, id, location, location, moveSpeed, true);
    }

    public void resetMovement() {
        isMoving = false;
        moveStartPoint = location;
        moveDestination = location;
        movePointsToDest = new ArrayList<>();
        movePointsIndex = 0;
        elapsedMoveTimeMs = 0;
        totalMoveTimeMs = 0;
        visualTargetIndex = 0;
    }

    protected boolean isStopped() {
        if (this.movementLine == null)
            this.movementLine = new Line2D.Float(this.location, this.location);
        if (this.path != null)
            return this.path.get(this.path.size() - 1).distance(this.location) <= 0.01d;
        return this.location.distance(this.movementLine.getP2()) < 0.01d;
    }

    public void setCanMove(boolean move) {
        this.canMove = move;
    }

    public double getSpeed() {
        return this.getPlayerStat("speed");
    }

    public void setState(ActorState state, boolean enabled) {
        this.states.put(state, enabled);
        ExtensionCommands.updateActorState(this.parentExt, this.room, this.id, state, enabled);
    }

    public double getStat(String stat) {
        return this.stats.get(stat);
    }

    public void move(Point2D destination) {
        if (!this.canMove()) {
            return;
        }
        this.movementLine = new Line2D.Float(this.location, destination);
        this.timeTraveled = 0f;
        ExtensionCommands.moveActor(
                this.parentExt,
                this.room,
                this.id,
                this.location,
                destination,
                (float) this.getPlayerStat("speed"),
                true);
    }

    public boolean isNotAMonster(Actor a) {
        return a.getActorType() != ActorType.MONSTER;
    }

    public void clearPath() {
        this.path = null;
        this.pathIndex = 1;
    }

    public int getXPWorth() {
        return this.xpWorth;
    }

    protected void handleActiveEffects() {
        List<String> badKeys = new ArrayList<>();
        for (String k : this.effectHandlers.keySet()) {
            if (this.effectHandlers.get(k).update()) badKeys.add(k);
        }
        for (String k : badKeys) {
            this.effectHandlers.remove(k);
        }
        List<String> badFx = new ArrayList<>();
        for (String k : this.fxHandlers.keySet()) {
            if (this.fxHandlers.get(k).update()) badFx.add(k);
        }
        for (String k : badFx) {
            this.fxHandlers.remove(k);
        }
    }

    public void addFx(String fxId, String emit, int duration) {
        if (this.fxHandlers.get(fxId) != null) {
            this.fxHandlers.get(fxId).addFx(duration);
        } else {
            this.fxHandlers.put(fxId, new FxHandler(this, fxId, emit, duration));
        }
    }

    public void removeFx(String fxId) {
        if (this.fxHandlers.get(fxId) != null) {
            this.fxHandlers.get(fxId).forceStopFx();
        }
    }

    public void addEffect(String stat, double delta, int duration, String fxId, String emit) {
        if (this.actorType == ActorType.TOWER || this.actorType == ActorType.BASE) return;
        if (!this.effectHandlers.containsKey(stat))
            this.effectHandlers.put(stat, new EffectHandler(this, stat));
        this.effectHandlers.get(stat).addEffect(delta, duration);
        this.addFx(fxId, emit, duration);
    }

    public void addEffect(String stat, double delta, int duration) {
        if (this.actorType == ActorType.TOWER || this.actorType == ActorType.BASE) return;
        if (!this.effectHandlers.containsKey(stat))
            this.effectHandlers.put(stat, new EffectHandler(this, stat));
        this.effectHandlers.get(stat).addEffect(delta, duration);
    }

    public void addState(ActorState state, double delta, int duration) {
        if (this.actorType == ActorType.TOWER || this.actorType == ActorType.BASE) return;
        if (this.getState(ActorState.IMMUNITY) && this.isCC(state)) return;
        if (!this.effectHandlers.containsKey(state.toString()))
            this.effectHandlers.put(state.toString(), new EffectHandler(this, state));
        this.effectHandlers.get(state.toString()).addState(delta, duration);
    }

    public void addState(ActorState state, double delta, int duration, String fxId, String emit) {
        if (this.actorType == ActorType.TOWER || this.actorType == ActorType.BASE) return;
        if (this.getState(ActorState.IMMUNITY) && this.isCC(state)) return;
        if (!this.effectHandlers.containsKey(state.toString()))
            this.effectHandlers.put(state.toString(), new EffectHandler(this, state));
        this.effectHandlers.get(state.toString()).addState(delta, duration);
        this.addFx(fxId, emit, duration);
    }

    public void dash() {}

    public void leap() {}

    public void teleport() {}

    public void handleCharm(UserActor charmer, int duration) {}

    public void moveTowardsCharmer(UserActor charmer) {}

    public void handleFear(Point2D source, int duration) {}

    public Point2D dash(Point2D dest, boolean noClip, double dashSpeed) {
        return null;
    }

    public boolean hasMovementCC() {
        ActorState[] cc = {
            ActorState.AIRBORNE,
            ActorState.STUNNED,
            ActorState.ROOTED,
            ActorState.FEARED,
            ActorState.CHARMED
        };
        for (ActorState effect : cc) {
            if (this.states.get(effect)) return true;
        }
        return false;
    }

    public boolean hasAttackCC() {
        ActorState[] cc = {
            ActorState.AIRBORNE,
            ActorState.STUNNED,
            ActorState.CHARMED,
            ActorState.FEARED,
            ActorState.BLINDED
        };
        for (ActorState effect : cc) {
            if (this.states.get(effect)) return true;
        }
        return false;
    }

    public boolean hasTempStat(String stat) {
        return this.effectHandlers.containsKey(stat);
    }

    public double getPlayerStat(String stat) {
        double currentStat = this.stats.get(stat);
        // Console.debugLog("Current Stat " + stat + " is " + currentStat);
        if (currentStat + this.getTempStat(stat) < 0) return 0; // Stat will never drop below 0
        return stat.equalsIgnoreCase("attackSpeed")
                ? getCappedAttackSpeed()
                : (currentStat + this.getTempStat(stat));
    }

    public double getTempStat(String stat) {
        double regularStat = 0d;
        if (this.effectHandlers.containsKey(stat))
            regularStat = this.effectHandlers.get(stat).getCurrentDelta();
        switch (stat) {
            case "speed":
                double slowStat = 0d;
                if (this.effectHandlers.containsKey(ActorState.SLOWED.toString())) {
                    slowStat =
                            this.effectHandlers.get(ActorState.SLOWED.toString()).getCurrentDelta();
                }
                return regularStat + slowStat;
        }
        // if (regularStat != 0) Console.debugLog("TempStat " + stat + " is " + regularStat);
        return regularStat;
    }

    private double getCappedAttackSpeed() {
        double currentAttackSpeed = this.stats.get("attackSpeed") + this.getTempStat("attackSpeed");
        return currentAttackSpeed < 500 ? 500 : currentAttackSpeed;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public abstract void handleKill(Actor a, JsonNode attackData);

    public void handleElectrodeGun(Actor attacker, JsonNode attackData) {
        AttackType type = getAttackType(attackData);
        if (attacker instanceof UserActor && type == AttackType.SPELL) {
            UserActor ua = (UserActor) attacker;
            int gunLevel = ChampionData.getJunkLevel(ua, "junk_2_electrode_gun");
            if (gunLevel > 0) {
                String desc =
                        "Abilities grant a stack on hit (3s CD). At 3 stacks, next ability stuns the first champion hit for 0.5/1/1.5/2s.";
                String name = "icon_electrode_gun_";
                if (ua.eGunStacks == 3) {
                    int stunDuration = 500 * gunLevel;

                    this.addState(ActorState.STUNNED, 0, stunDuration);
                    ExtensionCommands.createActorFX(
                            parentExt,
                            room,
                            id,
                            "magicman_snake_explosion",
                            1000,
                            id + "_eGunProc",
                            true,
                            "",
                            false,
                            false,
                            team);
                    ExtensionCommands.playSound(
                            parentExt, room, id, "electrode_gun_effect", location);

                    ExtensionCommands.removeStatusIcon(
                            ua.getParentExt(), ua.player, name + ua.eGunStacks);
                    ExtensionCommands.removeFx(ua.parentExt, ua.room, ua.getId() + "_eGunBuff");

                    ua.eGunStacks = 0;
                    ExtensionCommands.addStatusIcon(
                            ua.getParentExt(),
                            ua.player,
                            name + ua.eGunStacks,
                            desc,
                            name + ua.eGunStacks,
                            E_GUN_STACK_CD);

                } else if (System.currentTimeMillis() - ua.lastEGunStack >= E_GUN_STACK_CD) {
                    ua.lastEGunStack = System.currentTimeMillis();

                    ExtensionCommands.removeStatusIcon(
                            ua.getParentExt(), ua.player, name + ua.eGunStacks);

                    ua.eGunStacks++;

                    int duration = ua.eGunStacks != 3 ? E_GUN_STACK_CD : 0;

                    ExtensionCommands.addStatusIcon(
                            ua.getParentExt(),
                            ua.player,
                            name + ua.eGunStacks,
                            desc,
                            name + ua.eGunStacks,
                            duration);

                    if (ua.eGunStacks == 3) {
                        ExtensionCommands.createActorFX(
                                ua.parentExt,
                                ua.room,
                                ua.id,
                                "electrode_gun_buff",
                                1000 * 60 * 15,
                                ua.getId() + "_eGunBuff",
                                true,
                                "",
                                false,
                                false,
                                ua.team);
                    }
                }
            }
        }
    }

    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        if (a.getClass() == IceKing.class && this.hasMovementCC()) damage *= 1.1;
        if (a.getActorType() == ActorType.PLAYER) {
            UserActor ua = (UserActor) a;
            if (ChampionData.getJunkLevel(ua, "junk_2_peppermint_tank") > 0
                    && getAttackType(attackData) == AttackType.SPELL) {
                if (ua.getLocation().distance(this.location) < 2d) {
                    damage +=
                            (damage * ChampionData.getCustomJunkStat(ua, "junk_2_peppermint_tank"));
                    Console.debugLog("Increased damage from peppermint tank.");
                }
            }
            // this.handleElectrodeGun(ua, a, damage, attackData);
        }

        this.currentHealth -= damage;
        if (this.currentHealth <= 0) this.currentHealth = 0;
        ISFSObject updateData = new SFSObject();
        updateData.putUtfString("id", this.id);
        updateData.putInt("currentHealth", (int) this.currentHealth);
        updateData.putDouble("pHealth", this.getPHealth());
        updateData.putInt("maxHealth", (int) this.maxHealth);
        ExtensionCommands.updateActorData(parentExt, this.room, updateData);
        return this.currentHealth <= 0;
    }

    public void addToDamageQueue(
            Actor attacker, double damage, JsonNode attackData, boolean dotDamage) {
        if (this.currentHealth <= 0) return;

        damage = handleLichHand(attacker, attackData, damage);

        ISFSObject data = new SFSObject();
        data.putClass("attacker", attacker);
        data.putDouble("damage", damage);
        data.putClass("attackData", attackData);
        this.damageQueue.add(data);
        if (attacker instanceof UserActor
                && getAttackType(attackData) == AttackType.SPELL
                && getActorType() != ActorType.TOWER
                && getActorType() != ActorType.BASE
                && !attackData.get("spellName").asText().equalsIgnoreCase("flame cloak")) {
            UserActor ua = (UserActor) attacker;
            ua.addHit(dotDamage);
            ua.handleSpellVamp(this.getMitigatedDamage(damage, AttackType.SPELL, ua), dotDamage);
        }

        handleSai(attacker, attackData);
    }

    public double handleLichHand(Actor attacker, JsonNode attackData, double damage) {
        if (attacker instanceof UserActor) {
            UserActor ua = (UserActor) attacker;
            boolean leveledHand = ChampionData.getJunkLevel(ua, "junk_2_lich_hand") > 0;

            if (leveledHand && getAttackType(attackData) == AttackType.SPELL) {
                int lichHandStacks = ua.getLichHandStacks();
                Actor lichHandTarget = ua.getLichHandTarget();
                Long lastStackTime = ua.getLastLichHandStack();

                int MAX_STACKS = 3;
                int STACK_COOLDOWN = 1000;

                boolean cdEnded = System.currentTimeMillis() - lastStackTime >= STACK_COOLDOWN;
                double damageMultiplier = 0;

                if (lichHandTarget != null && lichHandTarget.equals(this)) {
                    damageMultiplier = lichHandStacks * 0.1;

                    if (lichHandStacks < MAX_STACKS && cdEnded) {
                        ua.setLichHandStacks(lichHandStacks + 1);
                        ua.setLastLichHandStack(System.currentTimeMillis());
                    }

                } else {
                    ua.setLichHandStacks(1);
                    ua.setLastLichHandStack(System.currentTimeMillis());
                }

                Console.debugLog("damage multi: " + damageMultiplier);

                damage = damage * (1 + damageMultiplier);
                ua.setLichHandTarget(this);
            }
        }
        return damage;
    }

    public void handleSai(Actor attacker, JsonNode attackData) {
        if (attacker instanceof UserActor && getAttackType(attackData) == AttackType.PHYSICAL) {

            UserActor ua = (UserActor) attacker;
            if (ua.hasBackpackItem("junk_1_sai")) {
                int critChance = (int) ua.getPlayerStat("criticalChance");
                Long lastSaiProc = ua.getLastSaiProcTime();

                Random random = new Random();
                int randomNumber = random.nextInt(100);
                boolean proc = randomNumber < critChance;

                int SAI_CD = UserActor.SAI_PROC_COOLDOWN;

                if (proc && System.currentTimeMillis() - lastSaiProc >= SAI_CD) {
                    ua.setLastSaiProcTime(System.currentTimeMillis());
                    double delta = this.getPlayerStat("armor") * -((double) critChance / 100.0);

                    this.addEffect("armor", delta, SAI_CD);
                }
            }
        }
    }

    public void addToDamageQueue(
            Actor attacker,
            double damage,
            JsonNode attackData,
            boolean dotDamage,
            String debugString) {
        if (this.currentHealth <= 0) return;
        if (attacker.getActorType() == ActorType.PLAYER)
            Console.debugLog(
                    attacker.getDisplayName()
                            + " is adding damage to "
                            + this.id
                            + " at "
                            + System.currentTimeMillis()
                            + " with "
                            + debugString);
        ISFSObject data = new SFSObject();
        data.putClass("attacker", attacker);
        data.putDouble("damage", damage);
        data.putClass("attackData", attackData);
        this.damageQueue.add(data);
        if (attacker.getActorType() == ActorType.PLAYER
                && this.getAttackType(attackData) == AttackType.SPELL
                && this.getActorType() != ActorType.TOWER
                && this.getActorType() != ActorType.BASE) {
            UserActor ua = (UserActor) attacker;
            ua.addHit(dotDamage);
            ua.handleSpellVamp(this.getMitigatedDamage(damage, AttackType.SPELL, ua), dotDamage);
        }
    }

    public void handleDamageQueue() {
        List<ISFSObject> queue = new ArrayList<>(this.damageQueue);
        this.damageQueue = new ArrayList<>();
        if (this.currentHealth <= 0 || this.dead) {
            return;
        }
        for (ISFSObject data : queue) {
            Actor damager = (Actor) data.getClass("attacker");
            double damage = data.getDouble("damage");
            JsonNode attackData = (JsonNode) data.getClass("attackData");
            if (this.damaged(damager, (int) damage, attackData)) {
                if (damager.getId().contains("turret") || damager.getId().contains("skully")) {
                    int enemyTeam = damager.getTeam() == 0 ? 1 : 0;
                    RoomHandler rh = parentExt.getRoomHandler(room.getName());

                    if (damager.getId().contains("turret")) {
                        damager = rh.getEnemyChampion(enemyTeam, "princessbubblegum");
                    } else if (damager.getId().contains("skully")) {
                        damager = rh.getEnemyChampion(enemyTeam, "lich");
                    }
                }
                damager.handleKill(this, attackData);
                this.die(damager);
                return;
            }
        }
    }

    public abstract void attack(Actor a);

    public abstract void die(Actor a);

    public abstract void update(int msRan);

    public void rangedAttack(Actor a) {
        Console.debugLog(this.id + " is using an undefined method.");
    }

    public Room getRoom() {
        return this.room;
    }

    public void changeHealth(int delta) {
        ISFSObject data = new SFSObject();
        this.currentHealth += delta;
        if (this.currentHealth > this.maxHealth) this.currentHealth = this.maxHealth;
        else if (this.currentHealth < 0) this.currentHealth = 0;
        data.putInt("currentHealth", (int) this.currentHealth);
        data.putInt("maxHealth", (int) this.maxHealth);
        data.putDouble("pHealth", this.getPHealth());
        ExtensionCommands.updateActorData(this.parentExt, this.room, this.id, data);
    }

    public void handleStructureRegen(
            Long lastAction, int TIME_REQUIRED_TO_REGEN, float REGEN_VALUE) {
        if (System.currentTimeMillis() - lastAction >= TIME_REQUIRED_TO_REGEN
                && getHealth() != maxHealth) {
            int delta = (int) (getMaxHealth() * REGEN_VALUE);
            changeHealth(delta);
        }
    }

    public void heal(int delta) {
        this.changeHealth(delta);
    }

    public void setHealth(int currentHealth, int maxHealth) {
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        if (this.currentHealth > this.maxHealth) this.currentHealth = this.maxHealth;
        else if (this.currentHealth < 0) this.currentHealth = 0;
        ISFSObject data = new SFSObject();
        data.putInt("currentHealth", (int) this.currentHealth);
        data.putInt("maxHealth", (int) this.maxHealth);
        data.putDouble("pHealth", this.getPHealth());
        data.putInt("health", (int) this.maxHealth);
        ExtensionCommands.updateActorData(this.parentExt, this.room, this.id, data);
    }

    public int getMitigatedDamage(double rawDamage, AttackType attackType, Actor attacker) {
        try {
            double armor =
                    this.getPlayerStat("armor")
                            * (1 - (attacker.getPlayerStat("armorPenetration") / 100));
            double spellResist =
                    this.getPlayerStat("spellResist")
                            * (1 - (attacker.getPlayerStat("spellPenetration") / 100));
            if (armor < 0) armor = 0;
            if (spellResist < 0) spellResist = 0;
            if (armor > 65) armor = 65;
            if (spellResist > 65) spellResist = 65;
            double modifier;
            if (attackType == AttackType.PHYSICAL) {
                modifier = (100 - armor) / 100d; // Max Armor 80
            } else modifier = (100 - spellResist) / 100d; // Max Shields 70
            return (int) Math.round(rawDamage * modifier);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void removeEffects() {
        for (String k : this.effectHandlers.keySet()) {
            this.effectHandlers.get(k).endAllEffects();
        }
    }

    public double getCollisionRadius() {
        JsonNode data = parentExt.getActorData(avatar);
        if (data == null) return 0.5;
        JsonNode collisionRadius = data.get("collisionRadius");
        if (collisionRadius == null) return 0.5;
        return collisionRadius.asDouble();
    }

    public void setStat(String key, double value) {
        this.stats.put(key, value);
    }

    protected HashMap<String, Double> initializeStats() {
        HashMap<String, Double> stats = new HashMap<>();
        JsonNode actorStats = this.parentExt.getActorStats(this.avatar);
        for (Iterator<String> it = actorStats.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            stats.put(k, actorStats.get(k).asDouble());
        }
        return stats;
    }

    public void snap(Point2D newLocation) {
        ExtensionCommands.snapActor(
                this.parentExt, this.room, this.id, this.location, newLocation, true);
        this.location = newLocation;
        this.timeTraveled = 0f;
        this.movementLine = new Line2D.Float(this.location, this.movementLine.getP2());
    }

    protected AttackType getAttackType(JsonNode attackData) {
        if (attackData.has("spellType")) return AttackType.SPELL;
        String type = attackData.get("attackType").asText();
        if (type.equalsIgnoreCase("physical")) return AttackType.PHYSICAL;
        else return AttackType.SPELL;
    }

    public ATBPExtension getParentExt() {
        return this.parentExt;
    }

    public void addDamageGameStat(UserActor ua, double value, AttackType type) {
        ua.addGameStat("damageDealtTotal", value);
        if (type == AttackType.PHYSICAL) ua.addGameStat("damageDealtPhysical", value);
        else ua.addGameStat("damageDealtSpell", value);
    }

    public boolean canMove() {
        for (ActorState s : this.states.keySet()) {
            if (s == ActorState.ROOTED
                    || s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.CHARMED
                    || s == ActorState.AIRBORNE) {
                if (this.states.get(s)) return false;
            }
        }
        return this.canMove;
    }

    public boolean getState(ActorState state) {
        return this.states.get(state);
    }

    private boolean isCC(ActorState state) {
        ActorState[] cc = {
            ActorState.SLOWED,
            ActorState.AIRBORNE,
            ActorState.STUNNED,
            ActorState.ROOTED,
            ActorState.BLINDED,
            ActorState.SILENCED,
            ActorState.FEARED,
            ActorState.CHARMED,
            ActorState.POLYMORPH
        };
        for (ActorState c : cc) {
            if (state == c) return true;
        }
        return false;
    }

    public boolean canAttack() {
        for (ActorState s : this.states.keySet()) {
            if (s == ActorState.STUNNED
                    || s == ActorState.FEARED
                    || s == ActorState.CHARMED
                    || s == ActorState.AIRBORNE
                    || s == ActorState.POLYMORPH) {
                if (this.states.get(s)) return false;
            }
        }
        if (this.attackCooldown < 0) this.attackCooldown = 0;
        return this.attackCooldown == 0;
    }

    public void handleKnockback(Point2D source, float distance) {}

    public void handlePathing() {
        if (this.path != null && this.location.distance(this.movementLine.getP2()) <= 0.9d) {
            if (this.pathIndex + 1 < this.path.size()) {
                this.pathIndex++;
                this.move(this.path.get(this.pathIndex));
            } else {
                this.path = null;
            }
        }
    }

    public boolean isDead() {
        return this.dead;
    }

    protected boolean isPointNearDestination(Point2D p) {
        if (this.path != null) return this.path.get(this.path.size() - 1).distance(p) <= 0.2d;
        else return this.movementLine.getP2().distance(p) <= 0.2d;
    }

    public void handlePull(Point2D source, double pullDistance) {}

    public String getPortrait() {
        return this.getAvatar();
    }

    public String getFrame() {
        if (this.getActorType() == ActorType.PLAYER) {
            String[] frameComponents = this.getAvatar().split("_");
            if (frameComponents.length > 1) {
                return frameComponents[0];
            } else {
                return this.getAvatar();
            }
        } else {
            return this.getAvatar();
        }
    }

    public String getSkinAssetBundle() {
        return this.parentExt.getActorData(this.avatar).get("assetBundle").asText();
    }

    public abstract void setTarget(Actor a);

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
            if (this.emitNode != null) emit = this.emitNode;
            float time = (float) (target.getLocation().distance(location) / 10f);
            ExtensionCommands.createProjectileFX(
                    parentExt, room, projectile, id, target.getId(), emit, "targetNode", time);
            parentExt
                    .getTaskScheduler()
                    .schedule(attackRunnable, (int) (time * 1000), TimeUnit.MILLISECONDS);
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
                this.room,
                this,
                projectile.getId(),
                projectile.getProjectileAsset(),
                location,
                lineEndPoint,
                (float) speed);
        this.parentExt.getRoomHandler(this.room.getName()).addProjectile(projectile);
    }

    public void stopMoving(int delay) {
        this.stopMoving();
        this.canMove = false;
        if (delay > 0) {
            parentExt
                    .getTaskScheduler()
                    .schedule(new MovementStopper(true), delay, TimeUnit.MILLISECONDS);
        } else this.canMove = true;
    }

    public String getChampionName(String avatar) {
        String[] avatarComponents = avatar.split("_");
        if (avatarComponents.length > 1) {
            return avatarComponents[0];
        } else {
            return avatar;
        }
    }

    public void basicAttackReset() {
        attackCooldown = 500;
    }

    public void handleCyclopsHealing() {
        if (this.getHealth() != this.maxHealth && !this.pickedUpHealthPack) {
            this.heal((int) (this.getMaxHealth() * 0.15d));
        }
        ExtensionCommands.createActorFX(
                this.parentExt,
                this.room,
                this.getId(),
                "fx_health_regen",
                60000,
                this.id + "healthPackFX",
                true,
                "",
                false,
                false,
                this.getTeam());
        this.pickedUpHealthPack = true;
        this.healthPackPickUpTime = System.currentTimeMillis();
    }

    public void startMoveTo(Point2D endPoint) {
        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        PathFinder pF = rh.getPathFinder();

        movePointsToDest = pF.getMovePointsToDest(location, endPoint);

        /*if (movePointsToDest.size() > 1) {
            for (Point2D p : movePointsToDest) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "gnome_a",
                        id + Math.random(),
                        5000,
                        (float) p.getX(),
                        (float) p.getY(),
                        false,
                        team,
                        0f);
            }

        } else if (movePointsToDest.size() == 1) {
            Point2D p = movePointsToDest.get(0);
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "candy_caster",
                    id + Math.random(),
                    2000,
                    (float) p.getX(),
                    (float) p.getY(),
                    false,
                    team,
                    0f);
        }*/

        movePointsIndex = 0;
        elapsedMoveTimeMs = 0;

        if (movePointsToDest.isEmpty()) {
            isMoving = false;
            return;
        }

        // skip points sitting right on top of us
        while (movePointsIndex < movePointsToDest.size()
                && location.distance(movePointsToDest.get(movePointsIndex)) < 0.001) {
            movePointsIndex++;
        }
        if (movePointsIndex >= movePointsToDest.size()) {
            isMoving = false;
            return;
        }

        moveStartPoint = location;
        moveDestination = movePointsToDest.get(movePointsIndex);

        double distance = location.distance(moveDestination);
        totalMoveTimeMs = Math.max(1, (long) ((distance / moveSpeed) * 1000.0));
        isMoving = true;

        // tell the client to go to roughly collinear point
        // instead of the immediate next waypoint
        visualTargetIndex = findVisualTargetIndex();
        ExtensionCommands.moveActor(
                parentExt,
                room,
                id,
                location,
                movePointsToDest.get(visualTargetIndex),
                moveSpeed,
                true);
    }

    public void handleMovementUpdate() {
        if (!isMoving) return;

        elapsedMoveTimeMs += 100;

        // consume one or more segments per tick — carry overflow forward
        // so short segments never cause a one-tick stall
        while (elapsedMoveTimeMs >= totalMoveTimeMs) {
            long overflow = elapsedMoveTimeMs - totalMoveTimeMs;
            location = moveDestination;

            if (movePointsToDest == null || movePointsIndex + 1 >= movePointsToDest.size()) {
                isMoving = false;
                elapsedMoveTimeMs = 0;
                return;
            }

            movePointsIndex++;
            moveStartPoint = location;
            moveDestination = movePointsToDest.get(movePointsIndex);
            double dist = location.distance(moveDestination);

            // skip zero length segments immediately
            while (dist <= 0.001 && movePointsIndex + 1 < movePointsToDest.size()) {
                movePointsIndex++;
                location = moveDestination;
                moveStartPoint = location;
                moveDestination = movePointsToDest.get(movePointsIndex);
                dist = location.distance(moveDestination);
            }
            if (dist <= 0.001) {
                isMoving = false;
                elapsedMoveTimeMs = 0;
                return;
            }

            totalMoveTimeMs = Math.max(1, (long) ((dist / moveSpeed) * 1000.0));
            elapsedMoveTimeMs = overflow;

            // moved past the point the client was aiming at
            // pick a new visual target and redirect the client
            if (movePointsIndex > visualTargetIndex) {
                visualTargetIndex = findVisualTargetIndex();
                ExtensionCommands.moveActor(
                        parentExt,
                        room,
                        id,
                        location,
                        movePointsToDest.get(visualTargetIndex),
                        moveSpeed,
                        true);
            }
        }

        // interpolate within the current segment

        double progress = Math.min(1.0, (double) elapsedMoveTimeMs / totalMoveTimeMs);
        double x =
                moveStartPoint.getX() + (moveDestination.getX() - moveStartPoint.getX()) * progress;
        double y =
                moveStartPoint.getY() + (moveDestination.getY() - moveStartPoint.getY()) * progress;
        location = new Point2D.Float((float) x, (float) y);
    }

    public void scheduleTask(Runnable task, int timeMs) {
        parentExt.getTaskScheduler().schedule(task, timeMs, TimeUnit.MILLISECONDS);
    }

    /* Scans forward from the current waypoint index and returns the index of the last point before
    the cumulative direction change exceeds ~10°. The client animates a single straight
    line to that point while the server tracks through every intermediate waypoint accurately.*/

    private int findVisualTargetIndex() {
        if (movePointsToDest == null || movePointsIndex >= movePointsToDest.size() - 1) {
            return movePointsToDest.size() - 1;
        }

        double totalAngle = 0;
        int best = movePointsIndex;

        for (int i = movePointsIndex; i < movePointsToDest.size() - 1; i++) {
            Point2D a = (i == movePointsIndex) ? location : movePointsToDest.get(i - 1);
            Point2D b = movePointsToDest.get(i);
            Point2D c = movePointsToDest.get(i + 1);

            double a1 = Math.atan2(b.getY() - a.getY(), b.getX() - a.getX());
            double a2 = Math.atan2(c.getY() - b.getY(), c.getX() - b.getX());
            double diff = Math.abs(a2 - a1);
            if (diff > Math.PI) diff = 2.0 * Math.PI - diff;

            totalAngle += diff;
            if (totalAngle > Math.toRadians(10)) {
                return i; // turn happens here — visual target stops at this point
            }
            best = i + 1;
        }
        return best; // remaining path is roughly straight
    }

    protected class MovementStopper implements Runnable {

        boolean move;

        public MovementStopper(boolean move) {
            this.move = move;
        }

        @Override
        public void run() {
            canMove = this.move;
        }
    }
}
