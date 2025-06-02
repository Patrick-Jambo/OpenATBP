package xyz.openatbp.extension;

import static com.mongodb.client.model.Filters.eq;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import com.smartfoxserver.v2.core.SFSEventType;
import com.smartfoxserver.v2.entities.Room;
import com.smartfoxserver.v2.entities.User;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.extensions.SFSExtension;
import com.smartfoxserver.v2.util.TaskScheduler;

import xyz.openatbp.extension.evthandlers.*;
import xyz.openatbp.extension.game.actors.UserActor;
import xyz.openatbp.extension.reqhandlers.*;

public class ATBPExtension extends SFSExtension {
    Map<String, StressLogger> commandStressLog = new HashMap<>();

    HashMap<String, JsonNode> actorDefinitions = new HashMap<>();
    HashMap<String, JsonNode> itemDefinitions = new HashMap<>();

    Path2D mainMapBoundaries;
    Path2D practiceMapBoundaries;

    ArrayList<Path2D> mainMapObstacles;
    ArrayList<Path2D> practiceMapObstacles;

    ArrayList<Vector<Float>>[] brushColliders;
    ArrayList<Vector<Float>>[] practiceBrushColliders;

    ArrayList<Path2D> brushPaths;
    ArrayList<Path2D> practiceBrushPaths;

    HashMap<String, List<String>> tips = new HashMap<>();

    HashMap<String, RoomHandler> roomHandlers = new HashMap<>();
    MongoClient mongoClient;
    MongoDatabase database;
    MongoCollection<Document> playerDatabase;
    MongoCollection<Document> championDatabase;
    MongoCollection<Document> matchHistoryDatabase;

    TaskScheduler taskScheduler;

    public void init() {
        this.addEventHandler(SFSEventType.USER_JOIN_ROOM, JoinRoomEventHandler.class);
        this.addEventHandler(SFSEventType.USER_JOIN_ZONE, JoinZoneEventHandler.class);
        this.addEventHandler(SFSEventType.USER_LOGIN, UserLoginEventHandler.class);
        this.addEventHandler(SFSEventType.ROOM_ADDED, RoomCreatedEventHandler.class);
        this.addEventHandler(SFSEventType.USER_DISCONNECT, UserDisconnect.class);

        this.addRequestHandler("req_hit_actor", HitActorHandler.class);
        this.addRequestHandler("req_keep_alive", Stub.class);
        this.addRequestHandler("req_goto_room", GotoRoomHandler.class);
        this.addRequestHandler("req_move_actor", MoveActorHandler.class);
        this.addRequestHandler("req_delayed_login", Stub.class);
        this.addRequestHandler("req_buy_item", Stub.class);
        this.addRequestHandler("req_pickup_item", Stub.class);
        this.addRequestHandler("req_do_actor_ability", DoActorAbilityHandler.class);
        this.addRequestHandler("req_console_message", Stub.class);
        this.addRequestHandler("req_mini_map_message", PingHandler.class);
        this.addRequestHandler("req_use_spell_point", SpellPointHandler.class);
        this.addRequestHandler("req_reset_spell_points", SpellPointHandler.class);
        this.addRequestHandler("req_toggle_auto_level", AutoLevelHandler.class);
        this.addRequestHandler("req_client_ready", ClientReadyHandler.class);
        this.addRequestHandler("req_dump_player", Stub.class);
        this.addRequestHandler("req_auto_target", AutoTargetHandler.class);
        this.addRequestHandler("req_admin_command", Stub.class);
        this.addRequestHandler("req_spam", Stub.class);
        this.taskScheduler = getApi().getNewScheduler(2);
        Properties props = getConfigProperties();
        if (!props.containsKey("mongoURI"))
            throw new RuntimeException(
                    "Mongo URI not set. Please create config.properties in the extension folder and define it.");

        try {
            mongoClient = MongoClients.create(props.getProperty("mongoURI"));
            database = mongoClient.getDatabase("openatbp");
            playerDatabase = database.getCollection("users");
            championDatabase = database.getCollection("champions");
            matchHistoryDatabase = database.getCollection("matches");
            loadDefinitions();
            loadColliders();
            loadItems();
            loadTips();
            loadBrushes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        trace("ATBP Extension loaded");
    }

    @Override
    public void destroy() { // Destroys all room tasks to prevent memory leaks
        for (String key : roomHandlers.keySet()) {
            if (roomHandlers.get(key) != null) roomHandlers.get(key).stopScript(true);
        }
        super.destroy();
    }

    public TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    public ArrayList<Path2D> getMapObstacles(boolean isPracticeMap) {
        return isPracticeMap ? practiceMapObstacles : mainMapObstacles;
    }

    public Path2D getMapBoundaries(boolean isPracticeMap) {
        return isPracticeMap ? practiceMapBoundaries : mainMapBoundaries;
    }

    public JsonNode getDefinition(String actorName) {
        return actorDefinitions.get(actorName);
    }

    public boolean roomHandlerExists(String roomId) {
        return this.roomHandlers.containsKey(roomId);
    }

    public RoomHandler getRoomHandler(String roomId) {
        return roomHandlers.get(roomId);
    }

    public MongoCollection<Document> getPlayerDatabase() {
        return this.playerDatabase;
    }

    public MongoCollection<Document> getChampionDatabase() {
        return this.championDatabase;
    }

    public MongoCollection<Document> getMatchHistoryDatabase() {
        return this.matchHistoryDatabase;
    }

    public JsonNode getActorStats(String actorName) {
        JsonNode node = actorDefinitions.get(actorName);
        if (node.has("MonoBehaviours")) {
            if (node.get("MonoBehaviours").has("ActorData")) {
                if (node.get("MonoBehaviours").get("ActorData").has("actorStats")) {
                    return node.get("MonoBehaviours").get("ActorData").get("actorStats");
                }
            }
        }
        return null;
    }

    public JsonNode getActorData(String actorName) {
        JsonNode node = actorDefinitions.get(actorName);
        if (node.has("MonoBehaviours")) {
            if (node.get("MonoBehaviours").has("ActorData"))
                return node.get("MonoBehaviours").get("ActorData");
        }
        return null;
    }

    public int getActorXP(String actorName) {
        JsonNode node = getActorData(actorName).get("scriptData");
        return node.get("xp").asInt();
    }

    public int getHealthScaling(String actorName) {
        return getActorData(actorName).get("scriptData").get("healthScaling").asInt();
    }

    public JsonNode getAttackData(String actorName, String attack) {
        try {
            if (actorName.contains("turret")) {
                return this.getActorData("princessbubblegum").get("spell2");
            }
            return this.getActorData(actorName).get(attack);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getDisplayName(String actorName) {
        return this.getActorData(actorName).get("playerData").get("playerDisplayName").asText();
    }

    public String getRandomTip(String type) {
        List<String> tips = this.tips.get(type);
        int num = (int) (Math.random() * (tips.size() - 1));
        return tips.get(num);
    }

    public Path2D getBoundariesPath(String filePath) {
        JSONArray boundaryArray = getJsonArray(filePath, "boundaries");
        return getPath2DFromArray(boundaryArray);
    }

    public Path2D getPath2DFromArray(JSONArray array) {
        ArrayList<Vector<Float>> vertices = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject vertexObj = array.getJSONObject(i);
            float x = (float) vertexObj.getDouble("x");
            float z = (float) vertexObj.getDouble("z");

            Vector<Float> vec = new Vector<>(2);
            vec.add(x);
            vec.add(z);

            vertices.add(vec);
        }

        return createPathFromVertices(vertices);
    }

    public ArrayList<Path2D> getObstaclePaths(String fileName) {
        ArrayList<Path2D> obstaclePaths = new ArrayList<>();
        JSONArray obstacleArray = getJsonArray(fileName, "obstacles");

        for (int i = 0; i < obstacleArray.length(); i++) {
            JSONObject obstacleObj = obstacleArray.getJSONObject(i);
            JSONArray jsonArray = obstacleObj.getJSONArray("vertices");
            Path2D obstaclePath = getPath2DFromArray(jsonArray);

            obstaclePaths.add(obstaclePath);
        }

        return obstaclePaths;
    }

    private JSONArray getJsonArray(String filePath, String key) {
        JSONArray array = new JSONArray();

        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            JSONObject root = new JSONObject(content);

            return root.getJSONArray(key);

        } catch (IOException exception) {
            exception.printStackTrace();
        }

        return array;
    }

    public static Path2D.Float createPathFromVertices(ArrayList<Vector<Float>> vertices) {
        Path2D.Float path = new Path2D.Float();

        if (vertices == null || vertices.size() < 3) {
            Console.logWarning("Not enough vertices to create a path2d, returning empty path");
            return path;
        }

        Vector<Float> firstVertex = vertices.get(0);
        path.moveTo(firstVertex.get(0), firstVertex.get(1));

        for (int v = 1; v < vertices.size(); v++) {
            Vector<Float> currentVertex = vertices.get(v);
            path.lineTo(currentVertex.get(0), currentVertex.get(1));
        }

        path.closePath();
        return path;
    }

    public void startScripts(Room room) { // Creates a new task scheduler for a room
        if (!this.roomHandlers.containsKey(room.getName())) {
            String groupId = room.getGroupId();
            int HP_RATE = MapData.NORMAL_HP_SPAWN_RATE;
            String[] pSpawns = GameManager.L1_SPAWNS;
            String rName = room.getName();

            switch (groupId) {
                case "Tutorial":
                    roomHandlers.put(rName, new TutorialRoomHandler(this, room));
                    break;

                case "Practice":
                    roomHandlers.put(rName, new PracticeRoomHandler(this, room, pSpawns, HP_RATE));
                    break;

                case "ARAM":
                    roomHandlers.put(rName, new AramRoomHandler(this, room));
                    break;

                case "PVE":
                case "PVP":
                    roomHandlers.put(rName, new MainMapRoomHandler(this, room));
                    break;
            }
            Console.debugLog("Starting script for room " + room.getName());
        }
    }

    public void stopScript(
            String roomId, boolean abort) { // Stops a task scheduler when room is deleted
        if (!roomHandlers.containsKey(roomId)) return;
        Console.debugLog("Stopping rooom: " + roomId);
        roomHandlers.get(roomId).stopScript(abort);
        roomHandlers.remove(roomId);
    }

    public ArrayList<Path2D> getBrushPaths(boolean practice) {
        if (!practice) return this.brushPaths;
        else return this.practiceBrushPaths;
    }

    public Path2D getBrush(int num, ArrayList<Path2D> brushPaths) {
        return brushPaths.get(num);
    }

    public int getBrushNum(Point2D loc, ArrayList<Path2D> brushPaths) {
        for (int i = 0; i < brushPaths.size(); i++) {
            Path2D p = brushPaths.get(i);
            if (p.contains(loc)) return i;
        }
        return -1;
    }

    @Override
    public void send(String cmdName, ISFSObject params, User recipient) {
        super.send(cmdName, params, recipient);
        /*
        if (this.commandStressLog.containsKey(cmdName)) {
            this.commandStressLog.get(cmdName).update(params);
        } else this.commandStressLog.put(cmdName, new StressLogger(cmdName));

         */
    }

    public boolean isBrushOccupied(RoomHandler room, UserActor a, ArrayList<Path2D> brushPaths) {
        try {
            int brushNum = this.getBrushNum(a.getLocation(), brushPaths);
            if (brushNum == -1) return false;
            Path2D brush = brushPaths.get(brushNum);
            for (UserActor ua : room.getPlayers()) {
                if (ua.getTeam() != a.getTeam() && brush.contains(ua.getLocation())) return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getElo(String tegID) {
        try {
            Document playerData = this.playerDatabase.find(eq("user.TEGid", tegID)).first();
            if (playerData != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode data = mapper.readTree(playerData.toJson());
                return data.get("player").get("elo").asInt();
            } else return -1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private void loadDefinitions()
            throws IOException { // Reads xml files and turns them into JsonNodes
        File path = new File(getCurrentFolder() + "/definitions");
        File[] files = path.listFiles();
        ObjectMapper mapper = new XmlMapper();
        assert files != null;
        for (File f : files) {
            if (f.getName().contains("xml")) {
                JsonNode node = mapper.readTree(f);
                actorDefinitions.put(f.getName().replace(".xml", ""), node);
            }
        }
    }

    private void loadItems() throws IOException {
        File path = new File(getCurrentFolder() + "/data/items");
        File[] files = path.listFiles();
        ObjectMapper mapper = new ObjectMapper();
        assert files != null;
        for (File f : files) {
            JsonNode node = mapper.readTree(f);
            itemDefinitions.put(f.getName().replace(".json", ""), node);
        }
    }

    private void loadTips() throws IOException {
        File tipsFile = new File(getCurrentFolder() + "/data/tips.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tipsJson = mapper.readTree(tipsFile);
        ArrayNode categoryNode = (ArrayNode) tipsJson.get("gameTips").get("category");
        for (JsonNode category : categoryNode) {
            String jsonString =
                    mapper.writeValueAsString(category.get("tip"))
                            .replace("[", "")
                            .replace("]", "")
                            .replaceAll("\"", "");
            String[] tips = jsonString.split(",");
            this.tips.put(category.get("name").asText(), Arrays.asList(tips));
        }
    }

    private void loadColliders() throws IOException {
        String currentDir = getCurrentFolder();
        String mainMapBoundaries = currentDir + "/data/colliders/mainMapBoundaries.json";
        String practiceMapBoundaries = currentDir + "/data/colliders/practiceMapBoundaries.json";

        String mainMapObstacles = currentDir + "/data/colliders/mainMapObstacles.json";
        String practiceMapObstacles = currentDir + "/data/colliders/practiceMapObstacles.json";

        this.mainMapBoundaries = getBoundariesPath(mainMapBoundaries);
        this.practiceMapBoundaries = getBoundariesPath(practiceMapBoundaries);

        this.mainMapObstacles = getObstaclePaths(mainMapObstacles);
        this.practiceMapObstacles = getObstaclePaths(practiceMapObstacles);
    }

    private void loadBrushes()
            throws IOException { // Reads json files and turns them into JsonNodes
        File mainMap = new File(getCurrentFolder() + "/data/colliders/brush.json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(mainMap);
        ArrayNode colliders = (ArrayNode) node.get("BrushAreas").get("brush");
        brushColliders = new ArrayList[colliders.size()];
        brushPaths = new ArrayList<>(colliders.size());
        for (int i = 0;
                i < colliders.size();
                i++) { // Reads all colliders and makes a list of their vertices
            Path2D path = new Path2D.Float();
            ArrayNode vertices = (ArrayNode) colliders.get(i).get("vertex");
            ArrayList<Vector<Float>> vecs = new ArrayList<>(vertices.size());
            for (int g = 0; g < vertices.size(); g++) {
                if (g == 0) {
                    path.moveTo(
                            vertices.get(g).get("x").asDouble(),
                            vertices.get(g).get("z").asDouble());
                } else { // Draws lines from each vertex to make a shape
                    path.lineTo(
                            vertices.get(g).get("x").asDouble(),
                            vertices.get(g).get("z").asDouble());
                }
                Vector<Float> vertex = new Vector<>(2);
                vertex.add(0, (float) vertices.get(g).get("x").asDouble());
                vertex.add(1, (float) vertices.get(g).get("z").asDouble());
                vecs.add(vertex);
            }
            path.closePath();
            brushPaths.add(path);
            brushColliders[i] = vecs;
        }

        File practiceMap = new File(getCurrentFolder() + "/data/colliders/practiceBrush.json");
        JsonNode node2 = mapper.readTree(practiceMap);
        ArrayNode colliders2 = (ArrayNode) node2.get("BrushAreas").get("brush");
        practiceBrushColliders = new ArrayList[colliders2.size()];
        practiceBrushPaths = new ArrayList<>(colliders2.size());
        for (int i = 0;
                i < colliders2.size();
                i++) { // Reads all colliders and makes a list of their vertices
            Path2D path = new Path2D.Float();
            ArrayNode vertices = (ArrayNode) colliders2.get(i).get("vertex");
            ArrayList<Vector<Float>> vecs = new ArrayList<>(vertices.size());
            for (int g = 0; g < vertices.size(); g++) {
                if (g == 0) {
                    path.moveTo(
                            vertices.get(g).get("x").asDouble(),
                            vertices.get(g).get("z").asDouble());
                } else { // Draws lines from each vertex to make a shape
                    path.lineTo(
                            vertices.get(g).get("x").asDouble(),
                            vertices.get(g).get("z").asDouble());
                }
                Vector<Float> vertex = new Vector<>(2);
                vertex.add(0, (float) vertices.get(g).get("x").asDouble());
                vertex.add(1, (float) vertices.get(g).get("z").asDouble());
                vecs.add(vertex);
            }
            path.closePath();
            practiceBrushPaths.add(path);
            practiceBrushColliders[i] = vecs;
        }
    }
}
