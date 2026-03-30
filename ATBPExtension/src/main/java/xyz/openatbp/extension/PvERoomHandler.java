package xyz.openatbp.extension;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.game.BotMapConfig;
import xyz.openatbp.extension.game.actors.Bot;
import xyz.openatbp.extension.game.bots.FinnBot;
import xyz.openatbp.extension.game.bots.IceKingBot;

public class PvERoomHandler extends MainMapRoomHandler {
    private Bot[] bots = new Bot[3];

    public PvERoomHandler(ATBPExtension parentExt, Room room) {
        super(parentExt, room);

        BotMapConfig mapConfig = BotMapConfig.createMainMap(1);
        bots[0] = new FinnBot(parentExt, room, "finn", 1, mapConfig);
        bots[1] = new IceKingBot(parentExt, room, "iceking", 1, mapConfig);
        bots[2] = new IceKingBot(parentExt, room, "lemeongrab", 1, mapConfig);
    }

    @Override
    public void run() {
        super.run();

        for (Bot b : bots) {
            if (b != null) b.update(mSecondsRan);
        }
    }
}
