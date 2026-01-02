package com.example.myapplication;

import android.graphics.Color;

public class DamageClasses {
    public static final String[] CLASSES = {
            "plate"
    };

    public static final int[] COLORS = {
            Color.RED      // car
    };

    public static int getColor(String className) {
        for (int i = 0; i < CLASSES.length; i++) {
            if (CLASSES[i].equals(className)) {
                return COLORS[i];
            }
        }
        return Color.GRAY;
    }
}