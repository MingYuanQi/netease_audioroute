package com.netease.nertc.audiocall.utils;

public class ArrayUtils {
    public static final int[] EMPTY_INT_ARRAY = new int[0];
    public static int[] toPrimitive(final Integer[] array) {
        if (array == null) {
            return null;
        } else if (array.length == 0) {
            return EMPTY_INT_ARRAY;
        } else {
            int[] result = new int[array.length];

            for(int i = 0; i < array.length; ++i) {
                result[i] = array[i];
            }

            return result;
        }
    }

}
