package xyz.openatbp.extension.game;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class PathFinder {
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
        boolean isInside = false;

        for (Obs obs : obstacleList) {
            if (obs.getPath().contains(end)) {
                isInside = true;
                break;
            }

            for (Line2D edge : obs.getEdges()) {
                if (edge.intersectsLine(movementLine)) {
                    intersection = true;
                    break;
                }
            }
            if (intersection) break;
        }

        List<Point2D> movePointsToDest = new ArrayList<>();
        if (!intersection && !isInside) {
            movePointsToDest.add(end);
        }
        return movePointsToDest;
    }
}
