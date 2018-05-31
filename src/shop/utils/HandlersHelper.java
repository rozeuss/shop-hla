package shop.utils;

import java.util.HashMap;
import java.util.Map;

public class HandlersHelper {

    private static Map<String, Integer> interactionClassMapping = new HashMap<>();
    private static Map<String, Integer> objectClassMapping = new HashMap<>();

    public static void addInteractionClassHandler(String interactionName, Integer handle) {
        interactionClassMapping.put(interactionName, handle);
    }

    public static int getInteractionHandleByName(String name) {
        return interactionClassMapping.get(name);
    }

    public static void addObjectClassHandler(String objectName, Integer handle) {
        objectClassMapping.put(objectName, handle);
    }

    public static int getObjectClassByName(String name) {
        return objectClassMapping.get(name);
    }

}
