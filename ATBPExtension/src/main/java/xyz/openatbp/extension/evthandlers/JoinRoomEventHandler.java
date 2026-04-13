package xyz.openatbp.extension.evthandlers;

import java.util.ArrayList;

import com.smartfoxserver.v2.core.ISFSEvent;
import com.smartfoxserver.v2.core.SFSEventParam;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.extensions.BaseServerEventHandler;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.ExtensionCommands;
import xyz.openatbp.extension.GameManager;
import xyz.openatbp.extension.game.RoomGroup;

public class JoinRoomEventHandler extends BaseServerEventHandler {
    @Override
    public void handleServerEvent(ISFSEvent event) { // Initialize everything
        Room room = (Room) event.getParameter(SFSEventParam.ROOM);
        User sender = (User) event.getParameter(SFSEventParam.USER);
        Console.debugLog(sender.getName() + " has joined room!");
        sender.setProperty("joined", true);
        ArrayList<User> users = (ArrayList<User>) room.getUserList();
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();
        int maxPlayers = room.getMaxUsers();
        // if(true) maxPlayers = 4; //Remove after testing
        if (GameManager.playersLoaded(users, maxPlayers)
                && (int) room.getProperty("state")
                        == 0) { // If all players have loaded into the room
            room.setProperty("state", 1);
            if (room.getGroupId().equals(RoomGroup.PVB.name()) && room.getMaxUsers() == 3) {
                String[] avatars = {"jake", "iceking", "finn"};
                String[] names = {"JAKE BOT", "ICE KING BOT", "FINN BOT"};

                for (int i = 0; i < avatars.length; i++) {
                    ExtensionCommands.addUser(
                            parentExt,
                            room,
                            i,
                            names[i],
                            avatars[i],
                            1,
                            avatars[i],
                            "belt_champion",
                            0,
                            false);
                }
            }
            GameManager.addPlayer(room, parentExt); // Add users to the game
            GameManager.loadPlayers(room, parentExt); // Load the players into the map
        }
    }
}
