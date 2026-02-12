package com.ishland.c2me.rewrites.chunk_serializer.common.utils;

import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;

public class NbtUtils {
    public static long @NotNull [] toLongs(byte @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    public static long @NotNull [] toLongs(short @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    public static long @NotNull [] toLongs(int @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
    public static long @NotNull [] toLongs(float @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (long) input[i];  // No flooring...
        }
        return output;
    }
    public static long @NotNull [] toLongs(double @NotNull [] input) {
        long[] output = new long[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (long) Math.floor(input[i]);  // Flooring...
        }
        return output;
    }


    public static double @NotNull [] toDoubles(byte @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }


    public static double @NotNull [] toDoubles(short @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    public static double @NotNull [] toDoubles(int @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    public static double @NotNull [] toDoubles(long @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }

    public static double @NotNull [] toDoubles(float @NotNull [] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        return output;
    }
}
