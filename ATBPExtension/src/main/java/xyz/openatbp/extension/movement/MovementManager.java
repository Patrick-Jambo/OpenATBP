package xyz.openatbp.extension.movement;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import com.smartfoxserver.v2.entities.Room;

import xyz.openatbp.extension.ATBPExtension;
import xyz.openatbp.extension.Console;
import xyz.openatbp.extension.GameManager;
import xyz.openatbp.extension.game.actors.Actor;

public class MovementManager {
    // todo: move all movement realated stuff here

    public static void handleMovementRequest(
            ATBPExtension parentExt, Actor a, Point2D playerClickedLocation) {
        Point2D currentLocation = a.getLocation();
        Room room = a.getRoom();

        if (currentLocation.equals(playerClickedLocation)) {
            // Jeśli już jesteśmy w celu, a postać się ruszała (np. z poprzedniej komendy),
            // zatrzymaj ją.
            if (a.getIsMoving()) a.clearMovement();
            return;
        }

        String groupId = room.getGroupId();
        boolean isPractice = GameManager.isPracticeMap(groupId);

        ArrayList<Path2D> mapObstacles = parentExt.getMapObstacles(isPractice);
        Path2D mapBoundaries = parentExt.getMapBoundaries(isPractice);

        Point2D tempDestination = playerClickedLocation; // Cel, który będziemy modyfikować

        // 1. Walidacja celu (playerClickedLocation) względem granic mapy
        if (mapBoundaries == null || mapBoundaries.getPathIterator(null).isDone()) {
            Console.logWarning(
                    "MovementManager: Granice mapy nie są załadowane! Zezwalanie na ruch (ryzykowne).");
        } else if (!mapBoundaries.contains(playerClickedLocation)) {
            Console.debugLog(
                    "Oryginalny cel (" + playerClickedLocation + ") jest POZA granicami mapy.");
            Point2D pointOnBoundary =
                    findClosestPointOnPathTowards(
                            currentLocation, playerClickedLocation, mapBoundaries);
            if (pointOnBoundary != null) {
                tempDestination = pointOnBoundary;
                Console.debugLog("Nowy cel (na granicy mapy): " + tempDestination);
            } else {
                Console.debugLog(
                        "Nie można znaleźć punktu na granicy mapy (z "
                                + currentLocation
                                + " do "
                                + playerClickedLocation
                                + "). Ruch anulowany.");
                a.clearMovement();
                return;
            }
        }

        // Jeśli po korekcji granic cel stał się obecną lokalizacją (np. stoimy przy granicy i
        // klikamy za nią)
        if (currentLocation.equals(tempDestination)) {
            if (a.getIsMoving()) a.clearMovement(); // Zatrzymaj, jeśli się ruszał
            return;
        }

        // 2. Walidacja celu (oryginalnego kliknięcia) względem przeszkód
        // Jeśli oryginalny klik był w przeszkodzie, chcemy ustawić cel na punkt tuż przed tą
        // przeszkodą.
        Path2D obstacleContainingOriginalClick =
                getObstacleContainingPoint(mapObstacles, playerClickedLocation);
        if (obstacleContainingOriginalClick != null) {
            Console.debugLog(
                    "Oryginalny cel (" + playerClickedLocation + ") jest WEWNĄTRZ przeszkody.");
            Point2D pointBeforeObstacle =
                    findClosestPointOnPathTowards(
                            currentLocation,
                            playerClickedLocation,
                            obstacleContainingOriginalClick);
            if (pointBeforeObstacle != null) {
                tempDestination = pointBeforeObstacle; // Nadpisz tempDestination
                Console.debugLog("Nowy cel (przed przeszkodą): " + tempDestination);
            } else {
                Console.debugLog(
                        "Nie można znaleźć punktu przed przeszkodą (dla celu wewnątrz). Ruch anulowany.");
                a.clearMovement();
                return;
            }
        }

        // Jeśli po korekcji celu względem przeszkody, cel stał się obecną lokalizacją
        if (currentLocation.equals(tempDestination)) {
            if (a.getIsMoving()) a.clearMovement();
            return;
        }

        // 3. Mamy teraz 'tempDestination'. Sprawdź, czy linia do niego przecina jakieś przeszkody.
        Line2D.Float movementLineToTempDest = new Line2D.Float(currentLocation, tempDestination);
        boolean pathfindingIsActuallyNeeded = false;

        for (Path2D obstacle : mapObstacles) {
            // Jeśli tempDestination to punkt na krawędzi TEJ przeszkody (bo tam skorygowaliśmy
            // cel),
            // to linia do tego punktu może "dotykać" tej przeszkody, ale to niekoniecznie wymaga
            // pathfindingu,
            // chyba że linia przecina jej wnętrze lub inną przeszkodę.
            // isLineIntersectingPath jest dość proste i zwróci true przy dotknięciu.
            // To jest trudny przypadek brzegowy.
            if (obstacle == obstacleContainingOriginalClick
                    && tempDestination.distanceSq(playerClickedLocation) < 0.01
                    && obstacle.contains(playerClickedLocation)) {
                // Jeśli oryginalny klik był w tej przeszkodzie, a my już ustawiliśmy cel na jej
                // krawędź,
                // zakładamy, że ruch do tej krawędzi jest "bezpieczny" i nie wymaga pathfindingu
                // (chyba że droga do tej krawędzi przecinałaby INNĄ przeszkodę, co jest rzadkie).
                // To uproszczenie, które może wymagać dopracowania.
                continue; // Pomiń sprawdzanie tej przeszkody dla pathfindingu, jeśli cel jest na
                // jej krawędzi.
            }

            if (isLineIntersectingPath(movementLineToTempDest, obstacle)) {
                pathfindingIsActuallyNeeded = true;
                Console.debugLog(
                        "Linia ruchu do "
                                + tempDestination
                                + " przecina przeszkodę: "
                                + obstacle.getBounds2D());
                break;
            }
        }

        if (pathfindingIsActuallyNeeded) {
            // === PATHFINDING JEST POTRZEBNY ===
            Console.debugLog(
                    "Pathfinding potrzebny z " + currentLocation + " do " + tempDestination);
            List<Point2D> path =
                    findPath(currentLocation, tempDestination, mapObstacles, mapBoundaries);

            if (path != null && !path.isEmpty()) {
                a.setPath(path);
            } else {
                Console.debugLog(
                        "Pathfinding (findPath) nie powiódł się lub zwrócił pustą ścieżkę dla celu: "
                                + tempDestination
                                + ".");
                // Fallback: Spróbuj ruszyć do najbliższego punktu przed pierwszą napotkaną
                // przeszkodą na linii do tempDestination.
                // To jest bardziej agresywny fallback niż po prostu zatrzymanie się.
                Point2D pointBeforeCollisionFallback = tempDestination;
                double minObstacleDistSqFallback = Double.MAX_VALUE;
                boolean intersectionFoundForFallback = false;

                for (Path2D obstacle : mapObstacles) {
                    if (isLineIntersectingPath(
                            movementLineToTempDest, obstacle)) { // Użyj linii do tempDestination
                        Point2D intersectionPoint =
                                findClosestPointOnPathTowards(
                                        currentLocation, tempDestination, obstacle);
                        if (intersectionPoint != null) {
                            intersectionFoundForFallback = true;
                            double distSq = currentLocation.distanceSq(intersectionPoint);
                            if (distSq < minObstacleDistSqFallback) {
                                minObstacleDistSqFallback = distSq;
                                pointBeforeCollisionFallback = intersectionPoint;
                            }
                        }
                    }
                }

                if (intersectionFoundForFallback
                        && (!pointBeforeCollisionFallback.equals(tempDestination)
                                || currentLocation.distanceSq(pointBeforeCollisionFallback)
                                        > 0.01)) {
                    Console.debugLog(
                            "Fallback: Ruch do punktu przed kolizją: "
                                    + pointBeforeCollisionFallback);
                    a.setSingleDestination(pointBeforeCollisionFallback);
                } else {
                    Console.debugLog(
                            "Fallback: Nie znaleziono punktu przed kolizją lub punkt jest zbyt blisko/identyczny. Ruch anulowany.");
                    a.clearMovement();
                }
            }
        } else {
            // === RUCH BEZPOŚREDNI (do tempDestination) ===
            Console.debugLog("Ruch bezpośredni do: " + tempDestination);
            a.setSingleDestination(tempDestination);
        }
    }

    private static Path2D getObstacleContainingPoint(ArrayList<Path2D> obstacles, Point2D point) {
        for (Path2D obs : obstacles) {
            if (obs.contains(point)) return obs;
        }
        return null;
    }

    private static Point2D findClosestPointOnPathTowards(
            Point2D start, Point2D target, Path2D pathShape) {

        if (start == null
                || target == null
                || pathShape == null
                || pathShape.getPathIterator(null).isDone()) {
            return null; // Podstawowa walidacja
        }
        if (start.equals(target)) return start; // Jeśli start i target to ten sam punkt

        Line2D.Float ray = new Line2D.Float(start, target);
        PathIterator pi = pathShape.getPathIterator(null);
        double[] coords = new double[6];
        Point2D.Double lastPoint = null;
        Point2D.Double firstPoint = null;
        Point2D closestIntersection = null;
        double minDistanceSqToStart = Double.MAX_VALUE;

        while (!pi.isDone()) {
            pi.currentSegment(coords);
            Point2D.Double currentPoint = new Point2D.Double(coords[0], coords[1]);
            if (firstPoint == null) firstPoint = currentPoint;

            if (lastPoint != null
                    && !lastPoint.equals(currentPoint)) { // Unikaj krawędzi o zerowej długości
                Line2D.Double edge = new Line2D.Double(lastPoint, currentPoint);
                if (ray.intersectsLine(edge)) {
                    Point2D intersection = getLineIntersectionPoint(ray, edge);
                    if (intersection != null) {
                        double distSq = start.distanceSq(intersection);
                        if (distSq < minDistanceSqToStart) {
                            minDistanceSqToStart = distSq;
                            closestIntersection = intersection;
                        }
                    }
                }
            }
            lastPoint = currentPoint;
            pi.next();
        }

        // Sprawdź krawędź zamykającą
        if (lastPoint != null
                && firstPoint != null
                && !lastPoint.equals(firstPoint)
                && !lastPoint.equals(firstPoint)) { // !lastPoint.equals(firstPoint) było dwa razy
            Line2D.Double closingEdge = new Line2D.Double(lastPoint, firstPoint);
            if (ray.intersectsLine(closingEdge)) {
                Point2D intersection = getLineIntersectionPoint(ray, closingEdge);
                if (intersection != null) {
                    double distSq = start.distanceSq(intersection);
                    if (distSq < minDistanceSqToStart) {
                        minDistanceSqToStart = distSq;
                        closestIntersection = intersection;
                    }
                }
            }
        }

        if (closestIntersection != null) {
            // Wektor od punktu przecięcia W KIERUNKU punktu startowego (aby odsunąć się "przed"
            // ścianę)
            double vecToStartX = start.getX() - closestIntersection.getX();
            double vecToStartY = start.getY() - closestIntersection.getY();
            double distVecToStart =
                    Math.sqrt(vecToStartX * vecToStartX + vecToStartY * vecToStartY);

            double epsilon = 0.1; // Mała wartość odsunięcia

            // Jeśli punkt startowy jest praktycznie na punkcie przecięcia, nie odsuwaj
            // lub jeśli target był wewnątrz, a my chcemy stanąć DOKŁADNIE na krawędzi (zależy od
            // logiki gry)
            // Obecne odsunięcie jest od punktu przecięcia w kierunku punktu startowego
            if (distVecToStart > 1e-4) { // Unikaj dzielenia przez zero lub prawie zero
                double moveBackX =
                        closestIntersection.getX() + (vecToStartX / distVecToStart) * epsilon;
                double moveBackY =
                        closestIntersection.getY() + (vecToStartY / distVecToStart) * epsilon;
                return new Point2D.Double(moveBackX, moveBackY);
            }
            return closestIntersection; // Zwróć sam punkt przecięcia, jeśli start jest na nim lub
            // za blisko
        }
        return null; // Jeśli nie znaleziono żadnego przecięcia na linii ruchu
    }

    public static List<Point2D> findPath(
            Point2D start, Point2D end, ArrayList<Path2D> obstacles, Path2D boundaries) {
        // To jest miejsce na implementację A* lub innego algorytmu.
        // Na razie, jako placeholder, zwrócimy pustą listę, co spowoduje, że postać się nie ruszy.
        Console.logWarning("MovementManager.findPath() nie jest zaimplementowane!");
        return new ArrayList<>(); // Zwróć pustą listę, aby to obsłużyć
    }

    public static boolean isLineIntersectingPath(Line2D line, Path2D path) {
        PathIterator pi = path.getPathIterator(null);

        double[] cords = new double[6];
        Point2D.Double lastPoint = null;
        Point2D.Double firstPoint = null;

        while (!pi.isDone()) {
            pi.currentSegment(cords);
            Point2D.Double currentPoint = new Point2D.Double(cords[0], cords[1]);

            if (firstPoint == null) {
                firstPoint = currentPoint;
            }

            if (lastPoint != null) {
                Line2D.Double edge = new Line2D.Double(lastPoint, currentPoint);

                if (line.intersectsLine(edge)) {
                    return true;
                }
            }
            lastPoint = currentPoint;
            pi.next();
        }
        if (lastPoint != null && !lastPoint.equals(firstPoint)) {
            Line2D.Double closingEdge = new Line2D.Double(lastPoint, firstPoint);

            return line.intersectsLine(closingEdge);
        }
        return false;
    }

    public static void updateHitbox(Actor a, float deltaTimeSeconds) {
        boolean isMoving = a.getIsMoving();
        Point2D currentDestination = a.getCurrentDestination();
        Point2D location = a.getLocation();

        if (!isMoving || currentDestination == null) {
            return;
        }

        double dirX = currentDestination.getX() - location.getX();
        double dirY = currentDestination.getY() - location.getY();

        double distanceToDest = location.distance(currentDestination);

        float speed = (float) a.getPlayerStat("speed");

        double distanceThisTick = speed * deltaTimeSeconds;

        if (distanceToDest <= distanceThisTick) {

            a.setLocation(currentDestination);

            if (a.hasPath()) {
                a.advancePath();
            } else {
                a.setIsMoving(false);
                a.setCurrentDestination(null);
            }

            Console.debugLog(a.getClass().getSimpleName() + " reached destination");

        } else {
            double normalizedDirX = dirX / distanceToDest;
            double normalizedDirY = dirY / distanceToDest;

            float newX = (float) (location.getX() + normalizedDirX * distanceThisTick);
            float newY = (float) (location.getY() + normalizedDirY * distanceThisTick);

            Point2D newLocation = new Point2D.Float(newX, newY);
            a.setLocation(newLocation);
        }
    }

    public static Point2D getLineIntersectionPoint(Line2D line1, Line2D line2) {
        double p0_x = line1.getX1();
        double p0_y = line1.getY1();
        double p1_x = line1.getX2();
        double p1_y = line1.getY2();

        double p2_x = line2.getX1();
        double p2_y = line2.getY1();
        double p3_x = line2.getX2();
        double p3_y = line2.getY2();

        double s1_x, s1_y, s2_x, s2_y;
        s1_x = p1_x - p0_x;
        s1_y = p1_y - p0_y;
        s2_x = p3_x - p2_x;
        s2_y = p3_y - p2_y;

        double s, t;
        double denominator = (-s2_x * s1_y + s1_x * s2_y);

        // Linie są współliniowe lub równoległe
        if (denominator == 0) {
            return null;
        }

        s = (-s1_y * (p0_x - p2_x) + s1_x * (p0_y - p2_y)) / denominator;
        t = (s2_x * (p0_y - p2_y) - s2_y * (p0_x - p2_x)) / denominator;

        if (s >= 0 && s <= 1 && t >= 0 && t <= 1) {
            // Punkt przecięcia leży na obu odcinkach
            double intersectX = p0_x + (t * s1_x);
            double intersectY = p0_y + (t * s1_y);
            return new Point2D.Double(intersectX, intersectY);
        }

        return null; // Brak przecięcia na odcinkach
    }

    public static boolean isPointOnLineSegment(Point2D p, Point2D a, Point2D b) {
        double epsilon = 1e-5; // Mała tolerancja
        // Sprawdź, czy p jest współliniowy z a i b
        // (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x) == 0
        double crossProduct =
                (b.getX() - a.getX()) * (p.getY() - a.getY())
                        - (b.getY() - a.getY()) * (p.getX() - a.getX());

        if (Math.abs(crossProduct) > epsilon) {
            return false; // Nie jest współliniowy
        }

        // Sprawdź, czy p leży w prostokącie otaczającym odcinek ab
        if (p.getX() < Math.min(a.getX(), b.getX()) - epsilon
                || p.getX() > Math.max(a.getX(), b.getX()) + epsilon
                || p.getY() < Math.min(a.getY(), b.getY()) - epsilon
                || p.getY() > Math.max(a.getY(), b.getY()) + epsilon) {
            return false;
        }

        return true;
    }
}
