package com.example.vrsystemclient;

/**
 * Generic Quaternion
 * Written for maximum portability between desktop and Android
 * Not in performance critical sections
 *
 * copy from package com.example.android.rs.vr.engine.Quaternion;
 */
public class MDQuaternion {

    static private final float PI = 3.1415927f;
    static private final float radiansToDegrees = 180f / PI;
    static private float atan2 (float y, float x) {
        if (x == 0f) {
            if (y > 0f) return PI / 2;
            if (y == 0f) return 0f;
            return -PI / 2;
        }
        final float atan, z = y / x;
        if (Math.abs(z) < 1f) {
            atan = z / (1f + 0.28f * z * z);
            if (x < 0f) return atan + (y < 0f ? -PI : PI);
            return atan;
        }
        atan = PI / 2 - z / (z * z + 0.28f);
        return y < 0f ? atan - PI : atan;
    }

    static public float clamp (float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private final float[] q = new float[4]; // w,x,y,z,

    private void set(float w, float x, float y, float z) {
        this.q[0] = w;
        this.q[1] = x;
        this.q[2] = y;
        this.q[3] = z;
    }

    public void idt(){
        set(1, 0, 0, 0);
    }


    public MDQuaternion() {
        idt();
    }

    public void fromMatrix(float[] matrix) {
        setFromAxes(matrix[0], matrix[1], matrix[2],
                matrix[4], matrix[5], matrix[6],
                matrix[8], matrix[9], matrix[10]);
    }

    private void setFromAxes (float xx, float xy, float xz, float yx, float yy, float yz, float zx,
                              float zy, float zz) {
        float w,x,y,z;

        // the trace is the sum of the diagonal elements; see
        // http://mathworld.wolfram.com/MatrixTrace.html
        final float t = xx + yy + zz;

        // we protect the division by s by ensuring that s>=1
        if (t >= 0) { // |w| >= .5
            float s = (float) Math.sqrt(t + 1); // |s|>=1 ...
            w = 0.5f * s;
            s = 0.5f / s; // so this division isn't bad
            x = (zy - yz) * s;
            y = (xz - zx) * s;
            z = (yx - xy) * s;
        } else if ((xx > yy) && (xx > zz)) {
            float s = (float) Math.sqrt(1.0 + xx - yy - zz); // |s|>=1
            x = s * 0.5f; // |x| >= .5
            s = 0.5f / s;
            y = (yx + xy) * s;
            z = (xz + zx) * s;
            w = (zy - yz) * s;
        } else if (yy > zz) {
            float s = (float) Math.sqrt(1.0 + yy - xx - zz); // |s|>=1
            y = s * 0.5f; // |y| >= .5
            s = 0.5f / s;
            x = (yx + xy) * s;
            z = (zy + yz) * s;
            w = (xz - zx) * s;
        } else {
            float s = (float) Math.sqrt(1.0 + zz - xx - yy); // |s|>=1
            z = s * 0.5f; // |z| >= .5
            s = 0.5f / s;
            x = (xz + zx) * s;
            y = (zy + yz) * s;
            w = (yx - xy) * s;
        }

        set(w, x, y, z);
    }

    /** Get the pole of the gimbal lock, if any.
     * @return positive (+1) for north pole, negative (-1) for south pole, zero (0) when no gimbal lock */
    public int getGimbalPole () {
        float w = q[0];
        float x = q[1];
        float y = q[2];
        float z = q[3];

        final float t = y * x + z * w;
        return t > 0.499f ? 1 : (t < -0.499f ? -1 : 0);
    }

    /** Get the roll euler angle in radians, which is the rotation around the z axis. Requires that this quaternion is normalized.
     * @return the rotation around the z axis in radians (between -PI and +PI) */
    public float getRollRad () {
        float w = q[0];
        float x = q[1];
        float y = q[2];
        float z = q[3];

        final int pole = getGimbalPole();
        return pole == 0 ? atan2(2f * (w * z + y * x), 1f - 2f * (x * x + z * z)) : (float)pole * 2f
                * atan2(y, w);
    }

    /** Get the roll euler angle in degrees, which is the rotation around the z axis. Requires that this quaternion is normalized.
     * @return the rotation around the z axis in degrees (between -180 and +180) */
    public float getRoll () {
        return getRollRad() * radiansToDegrees;
    }

    /** Get the pitch euler angle in radians, which is the rotation around the x axis. Requires that this quaternion is normalized.
     * @return the rotation around the x axis in radians (between -(PI/2) and +(PI/2)) */
    public float getPitchRad () {
        float w = q[0];
        float x = q[1];
        float y = q[2];
        float z = q[3];

        final int pole = getGimbalPole();
        return pole == 0 ? (float) Math.asin(clamp(2f * (w * x - z * y), -1f, 1f)) : (float)pole * PI * 0.5f;
    }

    /** Get the pitch euler angle in degrees, which is the rotation around the x axis. Requires that this quaternion is normalized.
     * @return the rotation around the x axis in degrees (between -90 and +90) */
    public float getPitch () {
        return getPitchRad() * radiansToDegrees;
    }

    /** Get the yaw euler angle in radians, which is the rotation around the y axis. Requires that this quaternion is normalized.
     * @return the rotation around the y axis in radians (between -PI and +PI) */
    public float getYawRad () {
        float w = q[0];
        float x = q[1];
        float y = q[2];
        float z = q[3];

        return getGimbalPole() == 0 ? atan2(2f * (y * w + x * z), 1f - 2f * (y * y + x * x)) : 0f;
    }

    /** Get the yaw euler angle in degrees, which is the rotation around the y axis. Requires that this quaternion is normalized.
     * @return the rotation around the y axis in degrees (between -180 and +180) */
    public float getYaw () {
        return getYawRad() * radiansToDegrees;
    }
}
