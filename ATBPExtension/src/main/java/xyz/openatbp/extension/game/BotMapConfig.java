package xyz.openatbp.extension.game;

import java.awt.geom.Point2D;
import java.util.HashMap;

import xyz.openatbp.extension.MapData;

public class BotMapConfig {
    public final Point2D respawnPoint;
    public final Point2D offenseAltar;
    public final Point2D defenseAltar;
    public final Point2D defenseAltar2;
    public final Point2D gnomesLocation;
    public final Point2D owlsLocation;
    public final Point2D grassBearLocation;
    public final Point2D hugWolfLocation;
    public final Point2D gooLocation;
    public final Point2D keeothLocation;
    public final HashMap<String, Point2D> hpPacks;
    public final Point2D[] enemyTowers;
    public final Point2D[] allyTowers;
    public final Point2D enemyNexus;
    public final Point2D allyNexus;
    public final Point2D[] topLanePath;
    public final Point2D[] botLanePath;
    public final Point2D[] midLanePath;

    public BotMapConfig(
            Point2D respawnPoint,
            Point2D offenseAltar,
            Point2D defenseAltar,
            Point2D defenseAltar2,
            Point2D gnomesLocation,
            Point2D owlsLocation,
            Point2D grassBearLocation,
            Point2D hugWolfLocation,
            Point2D gooLocation,
            Point2D keeothLocation,
            HashMap<String, Point2D> hpPacks,
            Point2D[] enemyTowers,
            Point2D[] allyTowers,
            Point2D enemyNexus,
            Point2D allyNexus,
            Point2D[] topLanePath,
            Point2D[] botLanePath,
            Point2D[] midLanePath) {
        this.respawnPoint = respawnPoint;
        this.offenseAltar = offenseAltar;
        this.defenseAltar = defenseAltar;
        this.defenseAltar2 = defenseAltar2;
        this.gnomesLocation = gnomesLocation;
        this.owlsLocation = owlsLocation;
        this.grassBearLocation = grassBearLocation;
        this.hugWolfLocation = hugWolfLocation;
        this.gooLocation = gooLocation;
        this.keeothLocation = keeothLocation;
        this.hpPacks = hpPacks;
        this.enemyTowers = enemyTowers;
        this.allyTowers = allyTowers;
        this.enemyNexus = enemyNexus;
        this.allyNexus = allyNexus;
        this.topLanePath = topLanePath;
        this.botLanePath = botLanePath;
        this.midLanePath = midLanePath;
    }

    public static BotMapConfig createPractice(int team) {
        Point2D respawnPoint = MapData.L1_PURPLE_SPAWNS[0];
        if (team == 1) {
            float x = (float) MapData.L1_PURPLE_SPAWNS[0].getX() * -1;
            float y = (float) MapData.L1_PURPLE_SPAWNS[0].getY();
            respawnPoint = new Point2D.Float(x, y);
        }

        Point2D offenseAltar = new Point2D.Float(0, MapData.L1_AALTAR_Z);
        Point2D deffenseAltar = new Point2D.Float(0, MapData.L1_DALTAR_Z);

        Point2D gnomes = MapData.L1_GNOMES[0];
        Point2D owls = MapData.L1_OWLS[0];

        Point2D purpleHpPack =
                new Point2D.Float(MapData.L1_BLUE_HEALTH_X * -1, MapData.L1_BLUE_HEALTH_Z);
        Point2D blueHpPack = new Point2D.Float(MapData.L1_BLUE_HEALTH_X, MapData.L1_BLUE_HEALTH_Z);

        HashMap<String, Point2D> hpPacks = new HashMap<>();

        if (team == 0) hpPacks.put("ph1", purpleHpPack);
        else hpPacks.put("bh1", blueHpPack);

        Point2D[] enemyTowers = new Point2D[2];
        Point2D[] allyTowers = new Point2D[2];

        Point2D purpleTower1 =
                new Point2D.Float(MapData.L1_PURPLE_TOWER_0[0], MapData.L1_PURPLE_TOWER_0[1]);
        Point2D purpleTower2 =
                new Point2D.Float(MapData.L1_PURPLE_TOWER_1[0], MapData.L1_PURPLE_TOWER_1[1]);

        Point2D blueTower1 =
                new Point2D.Float(MapData.L1_BLUE_TOWER_3[0], MapData.L1_BLUE_TOWER_3[1]);
        Point2D blueTower2 =
                new Point2D.Float(MapData.L1_BLUE_TOWER_4[0], MapData.L1_BLUE_TOWER_4[1]);

        if (team == 0) {
            enemyTowers[0] = blueTower1;
            enemyTowers[1] = blueTower2;
            allyTowers[0] = purpleTower1;
            allyTowers[1] = purpleTower2;
        } else {
            enemyTowers[0] = purpleTower1;
            enemyTowers[1] = purpleTower2;
            allyTowers[0] = blueTower1;
            allyTowers[1] = blueTower2;
        }

        Point2D enemyNexus;
        Point2D allyNexus;

        Point2D purpleNexus =
                new Point2D.Float(MapData.L1_PURPLE_BASE[0], MapData.L1_PURPLE_BASE[1]);
        Point2D blueNexus = new Point2D.Float(MapData.L1_BLUE_BASE[0], MapData.L1_BLUE_BASE[1]);

        if (team == 0) {
            enemyNexus = blueNexus;
            allyNexus = purpleNexus;
        } else {
            enemyNexus = purpleNexus;
            allyNexus = blueNexus;
        }

        Point2D[] midLanePath = getPracticeMidLanePath(team);

        return new BotMapConfig(
                respawnPoint,
                offenseAltar,
                deffenseAltar,
                null,
                gnomes,
                owls,
                null,
                null,
                null,
                null,
                hpPacks,
                enemyTowers,
                allyTowers,
                enemyNexus,
                allyNexus,
                null,
                null,
                midLanePath);
    }

    public boolean isPractice() {
        return keeothLocation == null && defenseAltar2 == null;
    }

    private static Point2D[] getPracticeMidLanePath(int team) {
        int xChange = team == 0 ? 1 : -1;

        return new Point2D[] {
            new Point2D.Float(-47.433697f * xChange, 0.25673252f),
            new Point2D.Float(-44.56323f * xChange, -3.832757f),
            new Point2D.Float(-36.172974f * xChange, -2.8828542f),
            new Point2D.Float(-36.172974f * xChange, -2.8828542f),
            new Point2D.Float(-23.244114f * xChange, -2.7537377f),
            new Point2D.Float(-13.155279f * xChange, -2.7126164f),
            new Point2D.Float(-6.6917067f * xChange, -2.280657f),
            new Point2D.Float(-3.7524607f * xChange, -1.2206067f),
            new Point2D.Float(-3.7524607f * xChange, -1.2206067f),
            new Point2D.Float(6.2069736f * xChange, 1.5437235f),
            new Point2D.Float(9.985384f * xChange, -0.94444215f),
            new Point2D.Float(13.83657f * xChange, 1.6118965f),
            new Point2D.Float(22.899439f * xChange, 2.5792289f),
            new Point2D.Float(27.893726f * xChange, -0.8201581f),
            new Point2D.Float(34.648197f * xChange, -2.6134179f),
            new Point2D.Float(38.612743f * xChange, 2.8533993f)
        };
    }

    public static BotMapConfig createMainMap(int team) {
        return null;
    }

    public boolean hasGrassBear() {
        return grassBearLocation != null;
    }

    public boolean hasHugWolf() {
        return hugWolfLocation != null;
    }

    public boolean hasGooMonster() {
        return gooLocation != null;
    }

    public boolean hasKeeoth() {
        return keeothLocation != null;
    }

    public boolean hasDefenseAlter2() {
        return defenseAltar2 != null;
    }
}
