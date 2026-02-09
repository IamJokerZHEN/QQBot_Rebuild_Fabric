package org.minecralogy.qqbot;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import org.minecralogy.qqbot.websocket.Package;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

public class Utils {
    private static final HashMap<String, ChatFormatting> mapping = new HashMap<>();

    private static final Gson gson = new Gson();
    private static final Base64.Decoder decoder = Base64.getDecoder();
    private static final Base64.Encoder encoder = Base64.getEncoder();
    private static final Type type = new TypeToken<HashMap<String, Object>>() {}.getType();

    public Utils() {
        mapping.put("black", ChatFormatting.BLACK);
        mapping.put("dark_blue", ChatFormatting.DARK_BLUE);
        mapping.put("dark_green", ChatFormatting.DARK_GREEN);
        mapping.put("dark_aqua", ChatFormatting.DARK_AQUA);
        mapping.put("dark_red", ChatFormatting.DARK_RED);
        mapping.put("dark_purple", ChatFormatting.DARK_PURPLE);
        mapping.put("gold", ChatFormatting.GOLD);
        mapping.put("gray", ChatFormatting.GRAY);
        mapping.put("dark_gray", ChatFormatting.DARK_GRAY);
        mapping.put("blue", ChatFormatting.BLUE);
        mapping.put("green", ChatFormatting.GREEN);
        mapping.put("aqua", ChatFormatting.AQUA);
        mapping.put("red", ChatFormatting.RED);
        mapping.put("light_purple", ChatFormatting.LIGHT_PURPLE);
        mapping.put("yellow", ChatFormatting.YELLOW);
        mapping.put("white", ChatFormatting.WHITE);
    }

    public static String encode(HashMap<String, ?> originalMap) {
        String string = gson.toJson(originalMap);
        return encoder.encodeToString(string.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String original) {
        byte[] stringBytes = decoder.decode(original.getBytes(StandardCharsets.UTF_8));
        String decodeString = new String(stringBytes, StandardCharsets.UTF_8);
        return decodeString;
    }

    public String toStringMessage(List<LinkedTreeMap<String, String>> original) {
        StringBuilder message = new StringBuilder();
        for (LinkedTreeMap<String, String> section : original) {
            message.append(mapping.getOrDefault(section.get("color"), ChatFormatting.GRAY)).append(section.get("text"));
        }
        return message.toString();
    }
}