package xyz.openatbp.extension.game.actors;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.pathfinding.MovementManager;

public abstract class Bot extends Actor {
    private static final boolean MOVEMENT_DEBUG = false;
    public static final int CYCLOPS_DURATION = 60000;
    public static final double POLYMORPH_SLOW_VALUE = 0.3d;
    private static final float TOWER_RANGE = 6f;
    private static final Point2D firstPoint = new Point2D.Float(15, 0);
    private static final double HP_PERCENT_HP_PACK = 0.6;
    private static final double HP_PERCENT_BASE = 0.2;
    private static final double HP_PERCENT_MINIONS = 0.6;
    private static final double HP_PERCENT_OWLS_LOW_LV = 0.6;
    private static final double HP_PERCENT_OWLS_HIGH_LV = 0.2;
    private static final double HP_PERCENT_GNOMES = 0.4;
    private final Point2D spawnPoint;
    private static final int POLYMORPH_DURATION = 3000;
    private static final int FOUNTAIN_HEAL = 250;

    protected final boolean testing = false;

    protected int deathTime = testing ? 1 : 10;
    protected int level = 1;
    protected int xp = 0;
    protected boolean isAutoAttacking = false;
    protected boolean isDashing = false;
    protected Long lastAttackedByMinion = 0L;
    protected Long lastAttackedByTower = 0L;
    protected Actor enemyTower = null;
    protected Actor enemyBaseTower = null;
    protected boolean wentToStartPoint = false;
    protected boolean pickedUpHealthPack = false;
    protected Long healthPackTime = 0L;

    protected Long lastPolymorphTime = 0L;
    protected boolean isPolymorphed = false;
    protected UserActor enemy;
    protected Long enemyDmgTime = 0L;
    protected HashMap<Actor, Long> agressors = new HashMap<>();

    protected static final int BASIC_ATTACK_DELAY = 500;
    protected int qCooldownMs;
    protected int wCooldownMs;
    protected int eCooldownMs;

    protected int qGCooldownMs;
    protected int wGCooldownMs;
    protected int eGCooldownMs;

    protected int qCastDelayMS;
    protected int wCastDelayMS;
    protected int eCastDelayMS;

    protected Long lastQUse = 0L;
    protected Long lastWUse = 0L;
    protected Long lastEUse = 0L;

    protected int globalCooldown = 0;

    public Bot(ATBPExtension parentExt, Room room, String avatar, int team, Point2D spawnPoint) {
        this.room = room;
        this.parentExt = parentExt;
        this.location = spawnPoint;
        this.avatar = avatar;
        this.id = avatar + "_" + team;
        this.team = team;
        this.movementLine = new Line2D.Float(this.location, this.location);
        this.actorType = ActorType.COMPANION;
        this.stats = initializeStats();
        this.spawnPoint = spawnPoint;
        this.displayName = avatar.toUpperCase() + " BOT";

        Runnable create =
                () ->
                        ExtensionCommands.createActor(
                                parentExt, room, id, avatar, location, 0f, team);
        parentExt.getTaskScheduler().schedule(create, 200, TimeUnit.MILLISECONDS);

        if (MOVEMENT_DEBUG) {
            ExtensionCommands.createActor(
                    parentExt, room, id + "moveDebug", "creep1", location, 0f, 1);
        }
        levelUpStats();
    }

    @Override
    public void die(Actor a) {
        dead = true;
        currentHealth = 0;
        canMove = false;

        Actor realKiller = a;

        if (a.getActorType() != ActorType.PLAYER && !agressors.isEmpty()) {
            for (Actor aggressor : agressors.keySet()) {
                if (System.currentTimeMillis() - agressors.get(aggressor) <= 10000
                        && aggressor instanceof UserActor) {
                    realKiller = aggressor;
                    UserActor player = (UserActor) realKiller;
                    player.setLastKilled(System.currentTimeMillis());
                }
            }
        }

        if (isPolymorphed) {
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
        }

        if (!getState(ActorState.AIRBORNE)) stopMoving();
        ExtensionCommands.knockOutActor(parentExt, room, id, realKiller.getId(), deathTime);

        Runnable respawn = this::respawn;
        parentExt.getTaskScheduler().schedule(respawn, deathTime, TimeUnit.SECONDS);

        if (realKiller.getActorType() == ActorType.PLAYER) {
            UserActor killer = (UserActor) realKiller;
            killer.increaseStat("kills", 1);
            RoomHandler roomHandler = parentExt.getRoomHandler(room.getName());
            roomHandler.addScore(killer, killer.getTeam(), 25);
            killer.addXP(100);
        }
    }

    protected int getSpellDamage(JsonNode attackData) {
        try {
            double dmg = attackData.get("damage").asDouble();
            double spellDMG = getPlayerStat("spellDamage");
            double dmgRatio = attackData.get("damageRatio").asDouble();

            return (int) Math.round(dmg + (spellDMG * dmgRatio));
        } catch (Exception e) {
            e.printStackTrace();
            return attackData.get("damage").asInt();
        }
    }

    protected boolean isNonStructureEnemy(Actor a) {
        return (a.getTeam() != team
                && a.getActorType() != ActorType.BASE
                && a.getActorType() != ActorType.TOWER);
    }

    @Override
    public boolean damaged(Actor a, int damage, JsonNode attackData) {
        agressors.put(a, System.currentTimeMillis());
        if (a instanceof UserActor) {
            UserActor ua = (UserActor) a;
            ua.checkTowerAggro(ua);
        }

        if (a.equals(enemy) && location.distance(a.getLocation()) <= 8) {
            enemyDmgTime = System.currentTimeMillis();
        }

        if (pickedUpHealthPack) {
            pickedUpHealthPack = false;
            setStat("healthRegen", getStat("healthRegen") - 15);
            ExtensionCommands.removeFx(parentExt, room, id + "healthPackFX");
        }
        if (a.getActorType() == ActorType.MINION) {
            lastAttackedByMinion = System.currentTimeMillis();
        }
        if (a instanceof Tower) {
            lastAttackedByTower = System.currentTimeMillis();
        }
        handlePolymorph(attackData);
        return super.damaged(a, damage, attackData);
    }

    protected void handlePolymorph(JsonNode attackData) {
        if (attackData.has("spellName")
                && attackData.get("spellName").asText().equals("flame_spell_2_name")) {
            lastPolymorphTime = System.currentTimeMillis();
            isPolymorphed = true;
            addState(ActorState.SLOWED, POLYMORPH_SLOW_VALUE, POLYMORPH_DURATION);

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
                    POLYMORPH_DURATION,
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
                    POLYMORPH_DURATION,
                    id + "_flambit_ring_",
                    true,
                    "",
                    true,
                    true,
                    getOppositeTeam());
        }
    }

    public boolean timeOk(int ability) {
        long lastUse;
        long cd;
        switch (ability) {
            case 1:
                lastUse = lastQUse;
                cd = qCooldownMs;
                break;
            case 2:
                lastUse = lastWUse;
                cd = wCooldownMs;
                break;
            case 3:
                lastUse = lastEUse;
                cd = eCooldownMs;
                break;
            default:
                return false;
        }
        return globalCooldown <= 0 && System.currentTimeMillis() - lastUse >= cd;
    }

    @Override
    public void update(int msRan) {
        if (dead) return;

        globalCooldown -= msRan;
        if (globalCooldown <= 0) globalCooldown = 0;
        handleDamageQueue();
        handleActiveEffects();

        if (msRan == 3000) {
            enemy = parentExt.getRoomHandler(room.getName()).getPlayers().get(0);
        }

        if (attackCooldown > 0) attackCooldown -= 100;

        if (!isStopped() && canMove()) timeTraveled += 0.1f;
        location =
                MovementManager.getRelativePoint(
                        movementLine, getPlayerStat("speed"), timeTraveled);
        handlePathing();
        if (MOVEMENT_DEBUG)
            ExtensionCommands.moveActor(
                    parentExt,
                    room,
                    id + "moveDebug",
                    location,
                    location,
                    (float) getPlayerStat("speed"),
                    false);

        if (msRan % 1000 == 0) {
            if (!testing) {
                int newDeath = 10 + ((msRan / 1000) / 60);
                if (newDeath != deathTime) deathTime = newDeath;
                if (currentHealth < maxHealth) regenHealth();
            }
        }

        if (msRan % 500 == 0) {
            RoomHandler handler = parentExt.getRoomHandler(room.getName());
            Point2D blueFountain = handler.getFountainsCenter().get(1);
            if (location.distance(blueFountain) <= TOWER_RANGE && getHealth() != getMaxHealth()) {
                changeHealth(FOUNTAIN_HEAL);
                ExtensionCommands.createActorFX(
                        parentExt,
                        room,
                        id,
                        "fx_health_regen",
                        3000,
                        id + "_fountainRegen",
                        true,
                        "Bip01",
                        false,
                        false,
                        team);
            }
        }

        if (msRan % 5000 == 0) {
            handlePassiveXP();
        }

        // bot actions
        RoomHandler handler = parentExt.getRoomHandler(room.getName());

        float topAltarY = MapData.L1_AALTAR_Z;
        float botAltarY = MapData.L1_DALTAR_Z;

        Point2D topAltarLocation = new Point2D.Float(0, topAltarY);
        Point2D botAltarLocation = new Point2D.Float(0, botAltarY);

        int topStatus = handler.getAltarStatus(topAltarLocation);
        int botStatus = handler.getAltarStatus(botAltarLocation);

        List<Actor> enemyActorsInRadius =
                Champion.getEnemyActorsInRadius(handler, team, location, 5f);

        if (pickedUpHealthPack && System.currentTimeMillis() - healthPackTime >= CYCLOPS_DURATION) {
            pickedUpHealthPack = false;
            ExtensionCommands.removeFx(parentExt, room, id + "healthPackFX");
        }

        if (getPHealth() < HP_PERCENT_HP_PACK
                && room.getVariable("spawns").getSFSObjectValue().getInt("bh1") == 91) {
            // Console.debugLog("Health pack");
            Point2D bh1 = new Point2D.Float(MapData.L1_BLUE_HEALTH_X, MapData.L1_BLUE_HEALTH_Z);
            handleMoving(bh1);
            return;
        }

        if (getPHealth() < HP_PERCENT_BASE) {
            // Console.debugLog("Return to base");
            handleMoving(spawnPoint);
            return;
        }

        if (System.currentTimeMillis() - enemyDmgTime <= 5000
                && !enemy.isDead()
                && shouldAttackTarget(enemy)) {
            // Console.debugLog("Attack Player");
            attemptAttack(enemy);
            return;
        }

        if (location.distance(firstPoint) <= 0.5) wentToStartPoint = true;

        if (!wentToStartPoint) {
            handleMoving(firstPoint);
            return;
        }

        if ((System.currentTimeMillis() - lastAttackedByMinion <= 1000
                        && getPHealth() < HP_PERCENT_MINIONS)
                || System.currentTimeMillis() - lastAttackedByTower <= 1000) {
            run();
            return;
        }

        handleAttackActions(enemyActorsInRadius);

        if ((topStatus == 10 && botStatus == 10) || !shouldMoveToAltar()) {
            // Console.debugLog("Altars captured or shouldn't move there, do something else");
            List<Actor> actors = handler.getActors();
            List<Actor> enemies =
                    actors.stream().filter(a -> a.getTeam() != team).collect(Collectors.toList());

            List<Actor> owls = new ArrayList<>();
            for (Actor a : enemies) {
                if (a.getActorType() == ActorType.MONSTER
                        && a.getHealth() > 0
                        && a.getId().contains("owl")) {
                    owls.add(a);
                }
            }

            if (!owls.isEmpty() && shouldAttackJungleCamp(true)) {
                // Console.debugLog("Attack Owls");
                attackClosestActor(owls);
                return;
            }

            List<Actor> gnomes = new ArrayList<>();
            for (Actor actor : enemies) {
                if (actor.getActorType() == ActorType.MONSTER
                        && actor.getHealth() > 0
                        && actor.getId().contains("gnome")) {
                    gnomes.add(actor);
                }
            }

            if (!gnomes.isEmpty() && shouldAttackJungleCamp(false)) {
                // Console.debugLog("Attack Gnomes");
                attackClosestActor(gnomes);
                return;
            }

            enemies.removeIf(a -> a instanceof Monster);

            // Console.debugLog("Attack closest enemy");
            attackClosestActor(enemies);

        } else if (topStatus != 10 && shouldMoveToAltar()) {
            // Console.debugLog("Top altar");
            handleMoving(topAltarLocation);
        } else if (shouldMoveToAltar()) {
            // Console.debugLog("Bot altar");
            handleMoving(botAltarLocation);
        }
    }

    protected void handleSwapFromPoly() {
        if (isPolymorphed && System.currentTimeMillis() - lastPolymorphTime >= POLYMORPH_DURATION) {
            isPolymorphed = false;
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
        }
    }

    public abstract void handleAttackActions(List<Actor> enemyActorsInRadius);

    public abstract boolean canUseQ(List<Actor> enemies);

    public abstract void useQ(Point2D destination);

    public abstract boolean canUseW(List<Actor> enemies);

    public abstract void useW(Point2D destination);

    public abstract boolean canUseE(List<Actor> enemies);

    public abstract void useE(Point2D destination);

    @Override
    public boolean canMove() {
        if (isDashing || isAutoAttacking) return false;
        return super.canMove();
    }

    @Override
    public boolean canAttack() {
        if (isDashing || isPolymorphed) return false;
        return super.canAttack();
    }

    protected boolean isStructure(Actor a) {
        return a.getActorType() == ActorType.TOWER || a.getActorType() == ActorType.BASE;
    }

    protected void handleMoving(Point2D destination) {
        if (location.distance(destination) > 0.1) {
            moveWithCollision(destination);
        }
    }

    protected boolean shouldMoveToAltar() {
        Tower firstBlueTower = null;
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        List<Tower> towers = handler.getTowers();
        for (Tower t : towers) {
            if (t.getTeam() == team && t.getTowerNum() == 4) {
                firstBlueTower = t;
            }
        }
        return firstBlueTower != null || enemy.isDead();
        // move to altars if first tower is not destroyed or when enemy is dead
    }

    public abstract void handlePassive();

    protected void attackClosestActor(List<Actor> targets) {
        double distance = 10000;
        Actor target = null;
        for (Actor a : targets) {
            if (a.getLocation().distance(location) < distance && shouldAttackTarget(a)) {
                distance = a.getLocation().distance(location);
                target = a;
            }
        }
        if (target != null) {
            this.target = target;
            attemptAttack(target);
        }
    }

    protected void regenHealth() {
        double healthRegen = getPlayerStat("healthRegen");
        if (currentHealth + healthRegen <= 0) healthRegen = (currentHealth - 1) * -1;
        changeHealth((int) healthRegen);
    }

    public void handleCyclopsHealing() {
        pickedUpHealthPack = true;
        heal((int) (getMaxHealth() * 0.15));
        healthPackTime = System.currentTimeMillis();
        ExtensionCommands.createActorFX(
                parentExt,
                room,
                id,
                "fx_health_regen",
                CYCLOPS_DURATION,
                id + "healthPackFX",
                true,
                "",
                false,
                false,
                getTeam());
        setStat("healthRegen", getStat("healthRegen") + 15);
    }

    protected void run() {
        // Console.debugLog("Run");
        Point2D runPoint = new Point2D.Float((float) location.getX() + 5, (float) location.getY());
        handleMoving(runPoint);
    }

    protected boolean shouldAttackJungleCamp(boolean owls) {
        if (level > 2 && level < 6 && getPHealth() > HP_PERCENT_OWLS_LOW_LV && owls
                || level > 5 && getPHealth() > HP_PERCENT_OWLS_HIGH_LV && owls) {
            return true;
        }
        return level > 4 && getPHealth() > HP_PERCENT_GNOMES && enemyTower.isDead() && !owls;
    }

    protected boolean shouldAttackTarget(Actor a) {
        float towerY = MapData.L1_TOWER_Z;
        float purpleTower0X = MapData.L1_PURPLE_TOWER_0[0];
        float purpleTower1X = MapData.L1_PURPLE_TOWER_1[0];

        Point2D purpleTower0Location = new Point2D.Float(purpleTower0X, towerY);
        Point2D purpleTower1Location = new Point2D.Float(purpleTower1X, towerY);

        RoomHandler handler = parentExt.getRoomHandler(room.getName());

        List<Tower> towers = handler.getTowers();
        List<BaseTower> baseTowers = handler.getBaseTowers();

        if (enemyTower == null) enemyTower = towers.get(0);
        if (enemyBaseTower == null) enemyBaseTower = baseTowers.get(0);

        List<Actor> actorsInRadiusTower1 =
                Champion.getActorsInRadius(handler, purpleTower1Location, 6f);
        List<Actor> allyMinionsTower1 = new ArrayList<>();
        for (Actor actor : actorsInRadiusTower1) {
            if (actor.getTeam() == team
                    && actor.getActorType() == ActorType.MINION
                    && actor.getHealth() > 0) {
                allyMinionsTower1.add(actor);
            }
        }

        List<Actor> actorsInRadiusTower0 =
                Champion.getActorsInRadius(handler, purpleTower0Location, 6f);
        List<Actor> allyMinionsTower0 = new ArrayList<>();
        for (Actor actor : actorsInRadiusTower0) {
            if (actor.getTeam() == team
                    && actor.getActorType() == ActorType.MINION
                    && actor.getHealth() > 0) {
                allyMinionsTower0.add(actor);
            }
        }

        double dT1 = a.getLocation().distance(purpleTower1Location);
        double dT0 = a.getLocation().distance(purpleTower0Location);

        if (dT1 <= TOWER_RANGE && allyMinionsTower1.isEmpty() && !enemyTower.isDead()) {
            return false;

        } else if (dT0 <= TOWER_RANGE && allyMinionsTower0.isEmpty() && !enemyBaseTower.isDead()) {
            return false;

        } else if ((dT1 <= TOWER_RANGE && !enemyTower.isDead()
                        || dT0 <= TOWER_RANGE && !enemyBaseTower.isDead())
                && a.getActorType() == ActorType.PLAYER) {
            return false;
        }
        if (allyMinionsTower1.isEmpty()
                && (float) a.getLocation().getX() < -10f
                && !enemyTower.isDead()) {
            return false;
        } else if (allyMinionsTower0.isEmpty()
                && (float) a.getLocation().getX() < -26
                && !enemyBaseTower.isDead()) {
            return false;
        }
        return true;
    }

    protected void attemptAttack(Actor target) {
        if (target != null) {
            this.target = target;
            if (!withinRange(target) && canMove()) {
                handleMoving(target.getLocation());
            } else if (withinRange(target)) {
                if (!isStopped()) stopMoving();
                if (canAttack()) attack(target);
            }
        }
    }

    public abstract void levelUpStats();

    public void respawn() {
        dead = false;
        canMove = true;
        setHealth((int) maxHealth, (int) maxHealth);
        setLocation(spawnPoint);
        removeEffects();
        agressors.clear();
        ExtensionCommands.snapActor(parentExt, room, id, location, location, false);
        ExtensionCommands.playSound(parentExt, room, id, "sfx/sfx_champion_respawn", location);
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

        ExtensionCommands.respawnActor(parentExt, room, id);
    }

    @Override
    public void handleKill(Actor a, JsonNode attackData) {
        if (level != 10) {
            xp += a.getXPWorth();
            checkLevelUp();
        }
    }

    public double getPLevel() {
        if (level == 10) return 0d;
        double lastLevelXP = ChampionData.getLevelXP(level - 1);
        double currentLevelXP = ChampionData.getLevelXP(level);
        double delta = currentLevelXP - lastLevelXP;
        return (xp - lastLevelXP) / delta;
    }

    private void handlePassiveXP() {
        RoomHandler handler = parentExt.getRoomHandler(room.getName());
        UserActor player = handler.getPlayers().get(0);

        if (player != null) {
            int playerLevel = player.getLevel();
            int botLevel = this.level;

            int additionalXP = 2;
            additionalXP *= (botLevel - playerLevel);
            if (additionalXP < 0) {
                additionalXP = 0;
            }
            int totalXPToAdd = 2 + additionalXP;
            xp += totalXPToAdd;
            checkLevelUp();
        }
    }

    private void checkLevelUp() {
        int level = ChampionData.getXPLevel(xp);
        if (level != this.level) {
            this.level = level;
            // Console.debugLog("level up");

            HashMap<String, Double> updateData = new HashMap<>(3);
            updateData.put("level", (double) level);
            updateData.put("xp", (double) xp);
            updateData.put("pLevel", getPLevel());

            ExtensionCommands.updateActorData(parentExt, room, id, updateData);
            levelUpStats();
            ExtensionCommands.playSound(parentExt, room, id, "sfx_level_up_beam", location);

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
        }
    }

    @Override
    public void attack(Actor a) {}

    protected void applyStopMovingDuringAttack() {
        stopMoving();
        isAutoAttacking = true;
        Runnable resetIsAttacking = () -> isAutoAttacking = false;
        parentExt
                .getTaskScheduler()
                .schedule(resetIsAttacking, BASIC_ATTACK_DELAY, TimeUnit.MILLISECONDS);
    }

    protected void scheduleTask(Runnable task, int timeMs) {
        parentExt.getTaskScheduler().schedule(task, timeMs, TimeUnit.MILLISECONDS);
    }

    protected boolean handleAttack(Actor a) {
        if (this.attackCooldown == 0) {
            double critChance = this.getPlayerStat("criticalChance") / 100d;
            double random = Math.random();
            boolean crit = random < critChance;

            ExtensionCommands.attackActor(
                    parentExt,
                    room,
                    this.id,
                    a.getId(),
                    (float) a.getLocation().getX(),
                    (float) a.getLocation().getY(),
                    crit,
                    true);

            this.attackCooldown = this.getPlayerStat("attackSpeed");
            return crit;
        }
        return false;
    }

    @Override
    public void setTarget(Actor a) {}
}
