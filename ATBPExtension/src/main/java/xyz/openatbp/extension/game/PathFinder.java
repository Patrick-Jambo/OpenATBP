package xyz.openatbp.extension.game;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class PathFinder {
    public static float INTERSECTION_STOP_DISTANCE = 0.5f;
    private List<Obs> obstacleList;
    private final Path2D mapArea;

    public PathFinder(Point2D[] mapBoundaries, List<Point2D[]> obstacles) {
        Path2D area = new Path2D.Float();
        area.moveTo(mapBoundaries[0].getX(), mapBoundaries[0].getY());

        for (int i = 1; i < mapBoundaries.length; i++) {
            area.lineTo(mapBoundaries[i].getX(), mapBoundaries[i].getY());
        }

        area.closePath();
        this.mapArea = area;

        List<Obs> obsList = new ArrayList<>();

        for (Point2D[] obstacle : obstacles) {
            Obs obs = getObs(obstacle);
            obsList.add(obs);
        }

        this.obstacleList = obsList;
    }

    private static Obs getObs(Point2D[] obstacle) {
        Path2D obstacleShape = new Path2D.Float();
        obstacleShape.moveTo(obstacle[0].getX(), obstacle[0].getY());

        for (int i = 1; i < obstacle.length; i++) {
            obstacleShape.lineTo(obstacle[i].getX(), obstacle[i].getY());
        }
        obstacleShape.closePath();

        List<Line2D> edges = new ArrayList<>();

        for (int i = 0; i < obstacle.length; i++) {
            Point2D p1 = obstacle[i];
            Point2D p2 = obstacle[(i + 1) % obstacle.length];

            Line2D line = new Line2D.Float(p1, p2);
            edges.add(line);
        }
        Obs obs = new Obs(obstacleShape, edges);
        return obs;
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

        boolean intersection = false;
        Point2D intersectionPoint = null;
        boolean isInside = false;

        for (Obs obs : obstacleList) {
            if (obs.getPath().contains(end)) {
                isInside = true;
            }

            for (Line2D edge : obs.getEdges()) {
                if (edge.intersectsLine(movementLine)) {
                    intersection = true;
                    intersectionPoint = getIntersectionPoint(movementLine, edge);
                    break;
                }
            }
            if (intersection) break;
        }

        List<Point2D> movePointsToDest = new ArrayList<>();
        if (!intersection && !isInside && mapArea.contains(end)) {
            movePointsToDest.add(end);
        }

        if (isInside
                && intersection
                && intersectionPoint != null
                && mapArea.contains(intersectionPoint)) {
            double distToIntersection = start.distance(intersectionPoint);

            float lDist = (float) distToIntersection - INTERSECTION_STOP_DISTANCE;
            Line2D line = Champion.getAbilityLine(start, intersectionPoint, lDist);
            movePointsToDest.add(line.getP2());
        }

        return movePointsToDest;
    }

    private double calculateDet(Point2D p1, Point2D p2) {
        return (p1.getX() * p2.getY()) - (p2.getX() * p1.getY());
    }

    public Point2D getIntersectionPoint(Line2D line1, Line2D line2) {
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
}
