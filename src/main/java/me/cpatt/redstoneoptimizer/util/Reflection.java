package me.cpatt.redstoneoptimizer.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class Reflection {

    private Reflection() {
    }

    public static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException(name);
    }

    public static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && Arrays.equals(method.getParameterTypes(), parameters)) {
                return method;
            }
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && Arrays.equals(method.getParameterTypes(), parameters)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(name);
    }
}
