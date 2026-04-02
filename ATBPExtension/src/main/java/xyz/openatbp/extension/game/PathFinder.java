package xyz.openatbp.extension.game;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.ExtensionCommands;

public class PathFinder {
    private Point2D[] mapBoundary;
    private List<Point2D[]> obstacles;

    public static float INTERSECTION_STOP_DISTANCE = 0.5f;
    private List<Obs> obstacleList;
    private final Path2D mapArea;
    private final List<Line2D> mapEdges;

    public PathFinder(Point2D[] mapBoundary, List<Point2D[]> obstacles) {
        this.mapBoundary = mapBoundary;
        this.obstacles = obstacles;

        Path2D area = new Path2D.Float();
        area.moveTo(mapBoundary[0].getX(), mapBoundary[0].getY());

        for (int i = 1; i < mapBoundary.length; i++) {
            area.lineTo(mapBoundary[i].getX(), mapBoundary[i].getY());
        }

        area.closePath();
        this.mapArea = area;
        this.mapEdges = getEdges(List.of(mapBoundary));

        List<Obs> obsList = new ArrayList<>();

        for (Point2D[] obstacle : obstacles) {
            Obs obs = getObs(obstacle);
            obsList.add(obs);
        }

        this.obstacleList = obsList;
    }

    public static Obs getObs(Point2D[] obstacle) {
        Path2D obstacleShape = new Path2D.Float();
        obstacleShape.moveTo(obstacle[0].getX(), obstacle[0].getY());

        for (int i = 1; i < obstacle.length; i++) {
            obstacleShape.lineTo(obstacle[i].getX(), obstacle[i].getY());
        }
        obstacleShape.closePath();

        List<Line2D> edges = getEdges(List.of(obstacle));
        return new Obs(obstacleShape, edges);
    }

    public static List<Line2D> getEdges(List<Point2D> verticesList) {
        List<Line2D> edges = new ArrayList<>();

        for (int i = 0; i < verticesList.size(); i++) {
            Point2D p1 = verticesList.get(i);
            Point2D p2 = verticesList.get((i + 1) % verticesList.size());
            Line2D line = new Line2D.Float(p1, p2);
            edges.add(line);
        }
        return edges;
    }

    public Path2D getMapShape() {
        return this.mapArea;
    }

    public List<Obs> getObstacleList() {
        return this.obstacleList;
    }

    public List<Point2D> getMovePointsToDest(Point2D start, Point2D end) {
        // end point outside an obstacle but line intersects an obstacle = pathfinding needed
        // end point inside an obstacle = move to obstacle edge
        // no intersection and end point outside obstacles = simple movement

        Line2D movementLine = new Line2D.Float(start, end);

        boolean obstacleIntersection = false;
        Point2D intersectionPoint = null;
        boolean isInside = false;

        for (Obs obs : obstacleList) {
            if (obs.getPath().contains(end)) {
                isInside = true;
            }

            for (Line2D edge : obs.getEdges()) {
                if (edge.intersectsLine(movementLine)) {
                    obstacleIntersection = true;
                    intersectionPoint = getIntersectionPoint(movementLine, edge);
                    break;
                }
            }
            if (obstacleIntersection) break;
        }

        List<Point2D> movePointsToDest = new ArrayList<>();
        if (!obstacleIntersection && !isInside && mapArea.contains(end)) {
            movePointsToDest.add(end);
        }

        if (isInside
                && obstacleIntersection
                && intersectionPoint != null
                && mapArea.contains(intersectionPoint)) {
            Point2D validPoint = getValidMovePointToIntersection(start, intersectionPoint);
            movePointsToDest.add(validPoint);
        }

        if (!isInside && !obstacleIntersection) {
            // move point outside the map

            Point2D mapIntersection = null;

            for (Line2D edge : mapEdges) {
                if (edge.intersectsLine(movementLine)) {
                    mapIntersection = getIntersectionPoint(movementLine, edge);
                    break;
                }
            }

            if (mapIntersection != null) {
                Point2D validPoint = getValidMovePointToIntersection(start, mapIntersection);
                movePointsToDest.add(validPoint);
            }
        }

        return movePointsToDest;
    }

    public static Point2D getValidMovePointToIntersection(
            Point2D startPoint, Point2D exactIntersection) {
        float distance = (float) startPoint.distance(exactIntersection);
        float newDistance = distance - INTERSECTION_STOP_DISTANCE;

        Line2D line = Champion.getAbilityLine(startPoint, exactIntersection, newDistance);
        return line.getP2();
    }

    private static double calculateDet(Point2D p1, Point2D p2) {
        return (p1.getX() * p2.getY()) - (p2.getX() * p1.getY());
    }

    public static Point2D getIntersectionPoint(Line2D line1, Line2D line2) {
        // first point L1
        double L1_x1 = line1.getX1();
        double L1_y1 = line1.getY1();
        Point2D p1 = new Point2D.Double(L1_x1, L1_y1);

        // second point L1
        double L1_x2 = line1.getX2();
        double L1_y2 = line1.getY2();
        Point2D p2 = new Point2D.Double(L1_x2, L1_y2);

        // first point L2
        double L2_x1 = line2.getX1();
        double L2_y1 = line2.getY1();

        // second point L2
        double L2_x2 = line2.getX2();
        double L2_y2 = line2.getY2();

        Point2D x_Diff = new Point2D.Double(L1_x1 - L1_x2, L2_x1 - L2_x2);
        Point2D y_Diff = new Point2D.Double(L1_y1 - L1_y2, L2_y1 - L2_y2);

        double divisorDet = calculateDet(x_Diff, y_Diff);
        if (divisorDet == 0) return null;

        double L1_d = calculateDet(line1.getP1(), line1.getP2());
        double L2_d = calculateDet(line2.getP1(), line2.getP2());

        Point2D d = new Point2D.Double(L1_d, L2_d);

        double x = calculateDet(d, x_Diff) / divisorDet;
        double y = calculateDet(d, y_Diff) / divisorDet;
        return new Point2D.Double(x, y);
    }

    public void displayMapBoundaries(ATBPExtension parentExt, Room room, String id, int team) {
        for (Point2D p : mapBoundary) {
            ExtensionCommands.createWorldFX(
                    parentExt,
                    room,
                    id,
                    "skully",
                    id + Math.random(),
                    1000 * 60 * 15,
                    (float) p.getX(),
                    (float) p.getY(),
                    false,
                    team,
                    0f);
        }
    }

    public void displayObstacles(ATBPExtension parentExt, Room room, String id, int team) {
        for (Point2D[] obstacle : obstacles) {
            for (Point2D p : obstacle) {
                ExtensionCommands.createWorldFX(
                        parentExt,
                        room,
                        id,
                        "skully",
                        id + Math.random(),
                        1000 * 60 * 15,
                        (float) p.getX(),
                        (float) p.getY(),
                        false,
                        team,
                        0f);
            }
        }
    }
}
