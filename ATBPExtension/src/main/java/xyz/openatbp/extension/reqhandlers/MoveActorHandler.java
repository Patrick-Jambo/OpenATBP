package xyz.openatbp.extension.reqhandlers;

import java.awt.geom.Point2D;

import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.BaseClientRequestHandler;

import xyz.openatbp.extension.*;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.movement.MovementManager;

public class MoveActorHandler extends BaseClientRequestHandler {
    @Override
    public void handleClientRequest(
            User sender, ISFSObject params) { // Called when player clicks on the map to move

        // Console.debugLog(params.getDump()); // comment this pls
        ATBPExtension parentExt = (ATBPExtension) getParentExtension();

        RoomHandler roomHandler = parentExt.getRoomHandler(sender.getLastJoinedRoom().getName());
        if (roomHandler == null && (int) sender.getLastJoinedRoom().getProperty("state") != 3)
            ExtensionCommands.abortGame(parentExt, sender.getLastJoinedRoom());
        if (roomHandler == null) {
            return;
        }
        UserActor user = roomHandler.getPlayer(String.valueOf(sender.getId()));
        if (user != null) user.resetTarget();
        if (user != null && user.canPerformNewMove()) {
            user.resetIdleTime();

            String objValue = "timeSinceBasicAttack";
            ISFSObject statsValue = sender.getVariable("stats").getSFSObjectValue();

            long timeSinceBasicAttack = statsValue.getLong(objValue);
            int BASIC_ATTACK_DELAY = UserActor.BASIC_ATTACK_DELAY;

            if ((System.currentTimeMillis() - timeSinceBasicAttack) < BASIC_ATTACK_DELAY) {
                return;
            }

            float dx = params.getFloat("dest_x");
            float dz = params.getFloat("dest_z");

            Point2D destination = new Point2D.Float(dx, dz);

            MovementManager.handleMovementRequest(parentExt, user, destination);

        } else if (user != null && user.getIsAutoAttacking()) {
            float dx = params.getFloat("dest_x");
            float dz = params.getFloat("dest_z");

            user.queueMovement(new Point2D.Float(dx, dz));
        }
    }
}
