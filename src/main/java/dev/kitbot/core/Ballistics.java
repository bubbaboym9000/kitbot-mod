package dev.kitbot.core;

/**
 * Arrow ballistics for a fully drawn bow. Pure Java, no Minecraft classes, so it can be unit tested anywhere.
 *
 * Minecraft arrows fly at 3 blocks/tick when fully drawn, lose 1% of their velocity per tick and fall
 * 0.05 blocks/tick^2. {@link #aim} finds the launch direction that makes an arrow pass through a
 * (possibly moving) target point.
 */
public final class Ballistics {
    public static final double SPEED = 3.0;
    public static final double GRAVITY = 0.05;
    public static final double DRAG = 0.99;

    private Ballistics() {}

    /** Height and flight time of an arrow once it has travelled {@code h} blocks horizontally. */
    public record Flight(double y, double ticks) {}

    /** Unit launch direction plus the flight time in ticks. */
    public record Aim(double dirX, double dirY, double dirZ, double ticks) {}

    /** Where is an arrow launched at {@code pitch} radians (up positive) when it reaches horizontal distance h? */
    public static Flight flightAt(double h, double pitch) {
        double vx = SPEED * Math.cos(pitch);
        double vy = SPEED * Math.sin(pitch);
        double x = 0;
        double y = 0;
        for (int t = 1; t <= 400; t++) {
            double px = x;
            double py = y;
            x += vx;
            y += vy;
            vx *= DRAG;
            vy *= DRAG;
            vy -= GRAVITY;
            if (x >= h) {
                double f = (h - px) / (x - px);
                return new Flight(py + (y - py) * f, t - 1 + f);
            }
            if (vx < 1e-4) break;
        }
        return null; // never gets that far
    }

    /** Lowest launch pitch whose arrow passes through (h, dy), as {pitch, ticks}; null when out of range. */
    public static double[] solvePitch(double h, double dy) {
        Flight prev = null;
        double prevPitch = 0;
        for (double pitch = -1.0; pitch <= 1.2; pitch += 0.01) {
            Flight f = flightAt(h, pitch);
            if (f == null) {
                prev = null;
                continue;
            }
            if (prev != null && prev.y() < dy && f.y() >= dy) {
                double lo = prevPitch;
                double hi = pitch;
                Flight res = f;
                for (int i = 0; i < 30; i++) {
                    double mid = (lo + hi) / 2;
                    Flight r = flightAt(h, mid);
                    if (r == null) {
                        hi = mid;
                        continue;
                    }
                    if (r.y() < dy) {
                        lo = mid;
                    } else {
                        hi = mid;
                        res = r;
                    }
                }
                return new double[] {hi, res.ticks()};
            }
            prev = f;
            prevPitch = pitch;
        }
        return null;
    }

    /**
     * Aim from the shooter's eye to a target point, leading it by its velocity (blocks per tick, horizontal
     * only; vertical motion is too jumpy to predict). Returns null when the target is out of range.
     */
    public static Aim aim(double ex, double ey, double ez,
                          double px, double py, double pz,
                          double vx, double vz) {
        double t = 0;
        double[] sol = null;
        double ax = px;
        double az = pz;
        for (int i = 0; i < 4; i++) {
            ax = px + vx * t;
            az = pz + vz * t;
            sol = solvePitch(Math.hypot(ax - ex, az - ez), py - ey);
            if (sol == null) return null;
            t = Math.min(sol[1], 40);
        }
        double dx = ax - ex;
        double dz = az - ez;
        double h = Math.hypot(dx, dz);
        if (h < 1e-6) return null;
        double cos = Math.cos(sol[0]);
        return new Aim(dx / h * cos, Math.sin(sol[0]), dz / h * cos, sol[1]);
    }
}
