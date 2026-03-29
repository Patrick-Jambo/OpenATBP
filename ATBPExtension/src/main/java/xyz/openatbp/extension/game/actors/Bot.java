package xyz.openatbp.extension.game.actors;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.ActorState;
import xyz.openatbp.extension.game.ActorType;
import xyz.openatbp.extension.game.BotMapConfig;
import xyz.openatbp.extension.game.Champion;
import xyz.openatbp.extension.game.champions.GooMonster;
import xyz.openatbp.extension.game.champions.Keeoth;
import xyz.openatbp.extension.pathfinding.MovementManager;

public abstract class Bot extends Actor {
    private static final boolean MOVEMENT_DEBUG = false;
    public static final int CYCLOPS_DURATION = 60000;
    public static final double POLYMORPH_SLOW_VALUE = 0.3d;
    private static final float TOWER_RANGE = 6f;
    public static final int INT = 15;
    public static final int HP_PACK_REGEN = INT;
    private final Point2D spawnPoint;
    private static final int POLYMORPH_DURATION = 3000;
    private static final int FOUNTAIN_HEAL = 250;

    protected final boolean testing = false;

    protected int deathTime = testing ? 1 : 10;
    protected int level = 1;
    protected int xp = 0;
    protected boolean isAutoAttacking = false;
    protected boolean isDashing = false;

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

    protected UserActor lastPlayerAttacker = null;
    protected Long lastPlayerAttackTime = 0L;
    protected Long lastAttackedByMinion = 0L;
    protected Long lastAttackedByTower = 0L;

    protected enum BotState {
        RETREATING, // go to hp packs or return to base
        FLEEING, // tower/minions are attacking the bot
        FIGHTING, // attack enemies
        ALTAR, // focus on capturing an altar
        JUNGLING, // attack jungle camp
        PUSHING, // push lane
    }

    protected Point2D altarToCapture;
    protected Point2D[] lanePath;
    protected BotMapConfig mapConfig;

    public Bot(
            ATBPExtension parentExt, Room room, String avatar, int team, BotMapConfig mapConfig) {
        this.room = room;
        this.parentExt = parentExt;
        this.mapConfig = mapConfig;
        this.location = mapConfig.respawnPoint;
        this.avatar = avatar;
        this.id = avatar + "_" + team;
        this.team = team;
        this.movementLine = new Line2D.Float(this.location, this.location);
        this.actorType = ActorType.COMPANION;
        this.stats = initializeStats();
        this.spawnPoint = mapConfig.respawnPoint;
        this.displayName = avatar.toUpperCase() + " BOT";
        this.xpWorth = 25;
        this.lanePath = mapConfig.midLanePath; // TODO: HARDCODED FOR NOW

        ExtensionCommands.createActor(parentExt, room, id, avatar, location, 0f, team);

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
            killer.addXP(this.getXPWorth());
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

            lastPlayerAttacker = ua;
            lastPlayerAttackTime = System.currentTimeMillis();
        }

        if (a.getActorType() == ActorType.MINION) {
            lastAttackedByMinion = System.currentTimeMillis();
        }
        if (a instanceof Tower) {
            lastAttackedByTower = System.currentTimeMillis();
        }

        if (a.equals(enemy) && location.distance(a.getLocation()) <= 8) {
            enemyDmgTime = System.currentTimeMillis();
        }

        if (pickedUpHealthPack) {
            pickedUpHealthPack = false; // TODO:
            setStat("healthRegen", getStat("healthRegen") - HP_PACK_REGEN);
            ExtensionCommands.removeFx(parentExt, room, id + "healthPackFX");
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

    private Actor getClosestActor(List<Actor> actors, boolean playerFocus) {
        Actor closestActor = null;
        Actor closestPlayer = null;
        double minActorDistance = 10000;
        double minPlayerDistance = 10000;

        for (Actor a : actors) {
            double distanceToActor = a.getLocation().distance(location);

            if (distanceToActor < minActorDistance) {
                minActorDistance = distanceToActor;
                closestActor = a;
            }

            if (a instanceof UserActor && distanceToActor < minPlayerDistance) {
                minPlayerDistance = distanceToActor;
                closestPlayer = a;
            }

            if (playerFocus && closestPlayer != null) {
                return closestPlayer;
            }
        }
        return closestActor;
    }

    private Actor getClosestMonster(List<Monster> monsters) {
        double minDistance = 10000;
        Actor target = null;

        for (Actor a : monsters) {
            double distance = a.getLocation().distance(location);
            if (distance < minDistance) {
                minDistance = distance;
                target = a;
            }
        }
        return target;
    }

    private boolean canWinFight() {
        // Can win vs champion?
        if (System.currentTimeMillis() - lastPlayerAttackTime <= 2000) {
            boolean isUnderAnyTower = false;
            for (Point2D allyTowerLocation : mapConfig.allyTowers) {
                if (location.distance(allyTowerLocation) <= TOWER_RANGE / 2.0) {
                    isUnderAnyTower = true;
                    break;
                }
            }

            if (isUnderAnyTower
                    && lastPlayerAttacker != null
                    && lastPlayerAttacker.getLocation().distance(location) <= 3
                    && !lastPlayerAttacker.isDead()) {
                return true;
            }

            if (System.currentTimeMillis() - lastEUse >= eCooldownMs
                    && lastPlayerAttacker != null
                    && !lastPlayerAttacker.isDead()
                    && lastPlayerAttacker.getPHealth() <= getPHealth()) {
                return true;
            }

            RoomHandler rh = parentExt.getRoomHandler(room.getName());
            List<Actor> enemies = Champion.getEnemyActorsInRadius(rh, team, location, 6f);
            enemies.removeIf(a -> !(a instanceof UserActor));
            if (System.currentTimeMillis() - lastQUse >= qCooldownMs
                    && System.currentTimeMillis() - lastWUse >= wCooldownMs
                    && enemies.size() == 1
                    && enemies.get(0) != null
                    && enemies.get(0).getPHealth() <= getPHealth()) {
                return true;
            }
        }
        return false;
    }

    private int getClosestWaypointIndex(Point2D locationToCheck) {
        double minDist = 10000;
        int closest = 0;
        for (int i = 0; i < lanePath.length; i++) {
            double dist = locationToCheck.distance(lanePath[i]);
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        return closest;
    }

    protected boolean canPushToPoint(Point2D closestAllyMinion, Point2D nextPushPoint) {
        if (team == 0) return nextPushPoint.getX() <= closestAllyMinion.getX();
        else return nextPushPoint.getX() >= closestAllyMinion.getX();
    }

    protected Point2D getNextPushWaypoint() {
        if (lanePath == null) {
            Console.logWarning("WARNING: lanePath is null in getNextPushWaypoint()!");
            return null;
        }

        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Actor> actors = rh.getActors();
        actors.removeIf(a -> a.getTeam() != team || !(a instanceof Minion));

        Actor closestMinion = getClosestActor(actors, false);
        if (closestMinion == null) return null;

        Point2D closestMinionP = getClosestActor(actors, false).getLocation();

        int current = getClosestWaypointIndex(closestMinionP);
        if (current < lanePath.length - 1) {
            Point2D nextLanePoint = lanePath[current + 1];
            return canPushToPoint(closestMinionP, nextLanePoint)
                    ? nextLanePoint
                    : lanePath[current];
        }
        return lanePath[current]; // already at end
    }

    protected Point2D getNextFleeWaypoint() {
        int current = getClosestWaypointIndex(location);
        if (current > 0) {
            return lanePath[current - 1];
        }
        return mapConfig.respawnPoint; // already at start, go home
    }

    protected boolean isEnemyProtectedByTower(Actor a) {
        RoomHandler rh = parentExt.getRoomHandler(room.getName());
        List<Tower> towers = rh.getTowers();
        towers.removeIf(t -> t.getTeam() == team);
        List<BaseTower> baseTowers = rh.getBaseTowers();
        baseTowers.removeIf(bT -> bT.getTeam() == team);
        towers.addAll(baseTowers);

        return towers.stream()
                .anyMatch(t -> t.getLocation().distance(a.getLocation()) <= TOWER_RANGE);
    }

    protected BotState evaluateBotState() {
        if (getHealth() <= 0 || isDead()) return null;

        // LOW HP
        if (getPHealth() <= 0.25) {
            if (canWinFight() && lastPlayerAttacker != null) {
                this.target = lastPlayerAttacker;
                return BotState.FIGHTING;
            }
            return BotState.RETREATING;
        }

        // Attacked by tower or minions
        if (System.currentTimeMillis() - lastAttackedByTower <= 2000
                || (System.currentTimeMillis() - lastAttackedByMinion <= 1000
                        && level < 3
                        && getPLevel() < 0.4)) {
            return BotState.FLEEING;
        }

        // PLAYER ATTACKED THE BOT
        if (lastPlayerAttacker != null) {
            boolean wasAttackedRecently = System.currentTimeMillis() - lastPlayerAttackTime <= 2000;
            if (wasAttackedRecently && !isEnemyProtectedByTower(lastPlayerAttacker)) {
                Console.debugLog("protected player test");
                this.target = lastPlayerAttacker;
                return BotState.FIGHTING;
            }
        }

        // DEFEND NEXUS
        RoomHandler rh = parentExt.getRoomHandler(room.getName());

        List<Actor> enemies =
                Champion.getEnemyActorsInRadius(rh, team, mapConfig.allyNexus, TOWER_RANGE);
        if (!enemies.isEmpty()) {
            List<BaseTower> baseTowers = rh.getBaseTowers();
            baseTowers.removeIf(bT -> bT.getTeam() != team);
            if (baseTowers.isEmpty()) { // enemies can attack nexus, should defend
                this.target = getClosestActor(enemies, true);
                return BotState.FIGHTING;
            }
        }

        // DEFEND BASE TOWER
        List<Tower> towers = rh.getTowers();
        towers.removeIf(t -> t.getTeam() != team);

        if ((mapConfig.isPractice() && towers.isEmpty())
                || !mapConfig.isPractice() && towers.size() < 2) {
            List<BaseTower> baseTowers = rh.getBaseTowers();
            baseTowers.removeIf(bT -> bT.getTeam() != team);

            if (!baseTowers.isEmpty()) { // check if base tower is alive
                BaseTower bT = baseTowers.get(0);

                List<Actor> enemiesBaseTower =
                        Champion.getEnemyActorsInRadius(rh, team, bT.location, TOWER_RANGE);

                if (!enemiesBaseTower.isEmpty()) { // someone is attacking the base tower, defend it
                    this.target = getClosestActor(enemiesBaseTower, true);
                    return BotState.FIGHTING;
                }
            }
        }

        // DEFEND TOWERS
        if (!towers.isEmpty()) {
            for (Tower t : towers) {
                List<Actor> enemiesUnderTower =
                        Champion.getEnemyActorsInRadius(rh, team, t.location, TOWER_RANGE);
                if (!enemiesUnderTower.isEmpty()) {
                    this.target = getClosestActor(enemiesUnderTower, true);
                    return BotState.FIGHTING;
                }
            }
        }

        // ATTACK NEARBY ENEMIES (NON MONSTERS)
        List<Actor> nearbyEnemies =
                Champion.getEnemyActorsInRadius(rh, team, location, TOWER_RANGE);

        nearbyEnemies.removeIf(a -> a instanceof Monster);
        nearbyEnemies.removeIf(a -> isEnemyProtectedByTower(a) && a instanceof UserActor);

        if (!nearbyEnemies.isEmpty()) {
            this.target = getClosestActor(nearbyEnemies, true);
            return BotState.FIGHTING;
        }

        // CAPTURE MID ALTAR
        int midAltarStatus = rh.getAltarStatus(mapConfig.offenseAltar);
        if (midAltarStatus != 10) { // mid altar can be captured
            altarToCapture = mapConfig.offenseAltar;
            return BotState.ALTAR;
        }

        // ATTACK JUNGLE CAMPS
        // TODO: Add Keeoth and Goomonster attack action
        List<Actor> allies = rh.getActorsInRadius(location, 4f);
        allies.removeIf(a -> !(a instanceof Bot) || a == this || a.getTeam() != team);

        if ((level >= 3 && getPHealth() >= 0.4)
                || (level == 2 && allies.size() == 1 && getPLevel() >= 0.4)
                || (allies.size() == 2 && getPLevel() >= 0.4)) {
            List<Monster> jungleMonsters = rh.getCampMonsters();
            jungleMonsters.removeIf(jm -> jm instanceof Keeoth || jm instanceof GooMonster);

            this.target = getClosestMonster(jungleMonsters);
            return BotState.JUNGLING;
        }

        // PUSH LANES
        return BotState.PUSHING;
        // TODO: Implement defense altars?
    }

    protected void executeBotState(BotState stateToExecute) {
        switch (stateToExecute) {
            case FIGHTING:
            case JUNGLING:
                handleFightingAbilities();
                attemptAttack(target);
                break;
            case RETREATING:
                handleRetreatAbilities();

                List<Point2D> validPacks = new ArrayList<>();
                for (String s : mapConfig.hpPacks.keySet()) {
                    int healthPackStatus = room.getVariable("spawns").getSFSObjectValue().getInt(s);
                    if (healthPackStatus == 61) validPacks.add(mapConfig.hpPacks.get(s));
                }

                Point2D closestPack = null;
                double minDistance = 10000;
                for (Point2D pack : validPacks) {
                    double distance = pack.distance(location);
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestPack = pack;
                    }
                }

                if (closestPack != null) moveWithCollision(closestPack);
                else moveWithCollision(mapConfig.respawnPoint);

                break;
            case FLEEING:
                handleRetreatAbilities();
                Point2D fleePoint = getNextFleeWaypoint();
                moveWithCollision(fleePoint);
                break;
            case ALTAR:
                if (altarToCapture != null) moveWithCollision(altarToCapture);
                break;
            case PUSHING:
                Point2D nextPushPoint = getNextPushWaypoint();
                if (nextPushPoint != null) moveWithCollision(nextPushPoint);
                break;
        }
    }

    public abstract void handleFightingAbilities();

    public abstract void handleRetreatAbilities();

    @Override
    public void update(int msRan) {
        if (dead) return;

        if (globalCooldown > 0) globalCooldown -= 100;
        if (globalCooldown <= 0) globalCooldown = 0;
        if (attackCooldown > 0) attackCooldown -= 100;

        handleDamageQueue();
        handleActiveEffects();

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

        handleRespawnTimer(msRan);
        handleFountainRegen(msRan);
        // handleSwapFromPoly();
        handleHpPackEnd();

        if (msRan % 5000 == 0) {
            handlePassiveXP();
        }

        // BOT ACTIONS
        BotState botState = evaluateBotState();
        if (botState != null) {
            // Console.debugLog("Bot state: " + botState);
            executeBotState(botState);
        }
    }

    private void handleFountainRegen(int msRan) {
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
    }

    private void handleRespawnTimer(int msRan) {
        if (msRan % 1000 == 0) {
            if (!testing) {
                int newDeath = 10 + ((msRan / 1000) / 60);
                if (newDeath != deathTime) deathTime = newDeath;
                if (currentHealth < maxHealth) regenHealth();
            }
        }
    }

    public void handleHpPackEnd() {
        if (pickedUpHealthPack
                && System.currentTimeMillis() - healthPackPickUpTime >= CYCLOPS_DURATION) {
            pickedUpHealthPack = false;
            ExtensionCommands.removeFx(parentExt, room, id + "healthPackFX");
        }
    }

    protected void handleSwapFromPoly() {
        if (isPolymorphed && System.currentTimeMillis() - lastPolymorphTime >= POLYMORPH_DURATION) {
            isPolymorphed = false;
            ExtensionCommands.swapActorAsset(parentExt, room, id, getSkinAssetBundle());
        }
    }

    protected void faceTarget(Actor target) {
        if (target != null) {
            Point2D rotationPoint =
                    Champion.getAbilityLine(location, target.getLocation(), 0.1f).getP2();
            setLocation(rotationPoint);

            ExtensionCommands.moveActor(
                    parentExt,
                    room,
                    id,
                    location,
                    rotationPoint,
                    (float) getPlayerStat("speed"),
                    true);
        }
    }

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

    protected void handleMoving(Point2D destination) {
        if (location.distance(destination) > 0.1) {
            moveWithCollision(destination);
        }
    }

    @Override
    public double getPlayerStat(String stat) {
        if (stat.equals("healthRegen") && pickedUpHealthPack)
            return super.getPlayerStat(stat) + HP_PACK_REGEN;
        return super.getPlayerStat(stat);
    }

    protected void regenHealth() {
        double healthRegen = getPlayerStat("healthRegen");
        if (currentHealth + healthRegen <= 0) healthRegen = (currentHealth - 1) * -1;
        changeHealth((int) healthRegen);
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
        int enemyLevel = 0;
        int count = 0;

        for (UserActor ua : handler.getPlayers()) {
            if (ua.getTeam() != team) {
                count++;
                enemyLevel = ua.getLevel();
            }
        }

        int extraXp = 0;
        if (count != 0) {
            float averageLevel = (float) (enemyLevel / count);
            extraXp = (int) (2 * (averageLevel - this.level));
        }
        if (extraXp < 0) extraXp = 0;
        this.xp += 2 + extraXp;
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
    public void attack(Actor a) {
        if (this.attackCooldown == 0) {
            this.applyStopMovingDuringAttack();
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
            if (this.attackCooldown < BASIC_ATTACK_DELAY) this.attackCooldown = BASIC_ATTACK_DELAY;
            double damage = this.getPlayerStat("attackDamage");
            if (crit) {
                damage *= this.getPlayerStat("criticalDamage");
            }
            Champion.DelayedAttack delayedAttack =
                    new Champion.DelayedAttack(parentExt, this, a, (int) damage, "basicAttack");
            try {
                String projectileFx =
                        this.parentExt
                                .getActorData(this.getAvatar())
                                .get("scriptData")
                                .get("projectileAsset")
                                .asText();
                if (projectileFx != null
                        && !projectileFx.isEmpty()
                        && !parentExt
                                .getActorData(this.avatar)
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

    public abstract boolean canUseQ();

    public abstract boolean canUseW();

    public abstract boolean canUseE();

    public abstract void useQ(Point2D destination);

    public abstract void useW(Point2D destination);

    public abstract void useE(Point2D destination);

    public abstract void levelUpStats();
}
