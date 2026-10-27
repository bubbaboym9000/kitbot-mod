import dev.kitbot.core.Ballistics;

/**
 * Plain-Java check of the aiming maths (no JUnit needed):
 *   javac -d out src/main/java/dev/kitbot/core/Ballistics.java tests/BallisticsCheck.java
 *   java -cp out BallisticsCheck
 * Fires simulated arrows with an independent 3D integrator and checks they pass through the target.
 */
public class BallisticsCheck {
    interface TargetPath { double[] at(double tick); }

    static double closestApproach(double[] eye, Ballistics.Aim a, TargetPath target) {
        double[] vel = {a.dirX() * 3.0, a.dirY() * 3.0, a.dirZ() * 3.0};
        double[] pos = eye.clone();
        double best = Double.MAX_VALUE;
        for (int t = 1; t <= 200; t++) {
            for (int s = 1; s <= 20; s++) { // arrows move ~3 blocks per tick: sample continuously
                double f = s / 20.0;
                double[] tp = target.at(t - 1 + f);
                double d = Math.sqrt(sq(pos[0] + vel[0] * f - tp[0]) + sq(pos[1] + vel[1] * f - tp[1]) + sq(pos[2] + vel[2] * f - tp[2]));
                best = Math.min(best, d);
            }
            for (int i = 0; i < 3; i++) pos[i] += vel[i];
            vel[0] *= 0.99; vel[1] = vel[1] * 0.99 - 0.05; vel[2] *= 0.99;
        }
        return best;
    }

    static double sq(double v) { return v * v; }

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  -> " + detail));
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        double[] eye = {0, 65.62, 0};
        double[][] spots = {{20, 0, 0}, {0, 0, -30}, {-25, 3, 10}, {12, -5, -12}, {35, 2, 20}, {5, 0, 5}, {-8, 8, 0}};
        for (double[] s : spots) {
            double[] p = {s[0], eye[1] + s[1], s[2]};
            Ballistics.Aim a = Ballistics.aim(eye[0], eye[1], eye[2], p[0], p[1], p[2], 0, 0);
            if (a == null) { check("static " + java.util.Arrays.toString(s), false, "no solution"); continue; }
            double miss = closestApproach(eye, a, t -> p);
            check("static target " + java.util.Arrays.toString(s) + " miss=" + String.format("%.2f", miss), miss < 0.25, "missed by " + miss);
        }

        // target strafing sideways at 4 blocks/second (0.2 blocks/tick)
        double[] start = {25, 65, 0};
        double vz = 0.2;
        TargetPath moving = t -> new double[] {start[0], start[1], start[2] + vz * t};
        Ballistics.Aim led = Ballistics.aim(eye[0], eye[1], eye[2], start[0], start[1], start[2], 0, vz);
        Ballistics.Aim flat = Ballistics.aim(eye[0], eye[1], eye[2], start[0], start[1], start[2], 0, 0);
        double missLed = closestApproach(eye, led, moving);
        double missFlat = closestApproach(eye, flat, moving);
        check("leads a strafing target (miss " + String.format("%.2f", missLed) + " vs " + String.format("%.2f", missFlat) + " without lead)",
            missLed < 0.5 && missLed < missFlat, "led=" + missLed + " flat=" + missFlat);

        check("out of range returns null", Ballistics.aim(0, 65, 0, 400, 65, 0, 0, 0) == null, "got a solution");

        Ballistics.Aim d = Ballistics.aim(0, 65, 0, 0, 65, -10, 0, 0);
        double len = Math.sqrt(sq(d.dirX()) + sq(d.dirY()) + sq(d.dirZ()));
        check("direction is a unit vector pointing at -z", Math.abs(len - 1) < 1e-9 && d.dirZ() < 0 && Math.abs(d.dirX()) < 1e-9, "len=" + len);

        System.out.println(failures == 0 ? "all checks passed" : failures + " check(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
