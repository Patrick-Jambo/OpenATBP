package xyz.openatbp.extension;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.game.GameMap;
import xyz.openatbp.extension.game.RoomGroup;
import xyz.openatbp.extension.game.actors.Bot;

public class PvERoomHandler extends MainMapRoomHandler {
    private final Bot[] botList = new Bot[3];

    public PvERoomHandler(
            ATBPExtension parentExt, Room room, Point2D[] mapBoundary, List<Point2D[]> obstacles) {
        super(parentExt, room, mapBoundary, obstacles);

        List<String> botAvatars = new ArrayList<>(3);

        RoomGroup roomGroup = GameManager.getRoomGroupEnum(room.getGroupId());

        if (roomGroup == RoomGroup.PVB) {
            for (int i = 0; i < 3; i++) {
                Bot b =
                        GameModeSpawns.createRandomBot(
                                botAvatars, false, parentExt, room, 1, GameMap.BATTLE_LAB);
                if (b != null) {
                    botAvatars.add(b.getAvatar());
                    botList[i] = b;
                    companions.add(b);
                }
            }
        }
    }

    @Override
    public void run() {
        super.run();

        for (Bot b : botList) {
            if (b != null) b.update(mSecondsRan);
        }
    }
}
