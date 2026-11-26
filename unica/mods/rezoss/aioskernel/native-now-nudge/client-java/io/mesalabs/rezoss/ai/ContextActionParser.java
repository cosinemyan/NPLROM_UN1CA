package io.mesalabs.rezoss.ai;

import java.util.*;
import java.util.regex.*;

/** Bounded English parameter extraction; never reads media or writes calendar data. */
public final class ContextActionParser {
    private ContextActionParser() {}
    public static final class Action {
        public final String type;
        public final Map<String,String> parameters = new LinkedHashMap<>();
        Action(String type) { this.type = type; }
    }
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern NEGATIVE = Pattern.compile(
        "\\b(?:don't|dont|do not|never|not|cancel|cancelled|canceled|ignore|forget|instead|no need)\\b", FLAGS);
    private static final Pattern PHOTO = Pattern.compile(
        "\\b(?:send|share|show|find|have|got)\\b.{0,60}?\\b(?:photos?|pictures?|pics|images?)\\s+(?:of|from)\\s+(.+)", FLAGS);
    private static final Pattern DATE = Pattern.compile(
        "\\b(?:today|tomorrow|(?:next |this )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|20[2-9][0-9]-[01][0-9]-[0-3][0-9])\\b", FLAGS);
    private static final Pattern TIME = Pattern.compile(
        "\\b(?:noon|midnight|(?:1[0-2]|0?[1-9])(?::[0-5][0-9])?\\s*(?:am|pm)|(?:[01]?[0-9]|2[0-3]):[0-5][0-9])\\b", FLAGS);
    private static final Pattern CREATE = Pattern.compile(
        "\\b(?:(?:add|create|schedule|book|arrange|plan)\\b|let['’]s\\s+(?:meet|have|do|go))\\s*(.*)", FLAGS);

    // Use the most recent nonempty turn only. Never resurrect an older request
    // after the user has replied, or treat a trailing empty prompt as content.
    private static String latest(String input, boolean chat, String user) {
        if (input == null || input.length() > 16384) return null;
        String[] lines = input.replace("<COL>", ":").split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; --i) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (!chat) return line;
            int colon = line.indexOf(':');
            if (colon <= 0 || colon > 80) return null;
            String speaker = line.substring(0, colon).trim();
            String body = line.substring(colon + 1).trim();
            if (body.isEmpty()) continue;
            if (speaker.equalsIgnoreCase(user == null ? "" : user.trim()) ||
                speaker.equalsIgnoreCase("<self>") || speaker.equalsIgnoreCase("Me")) return null;
            return body;
        }
        return null;
    }
    public static Action parse(String input, boolean chat, String user) {
        String text = latest(input, chat, user);
        if (text == null || text.length() > 1000 || NEGATIVE.matcher(text).find()) return null;
        Matcher photo = PHOTO.matcher(text);
        if (photo.find()) {
            String query = photo.group(1).replaceFirst("(?i)^(?:our|my|your|the|a|an)\\s+", "")
                .replaceFirst("(?i)[,.!?]*\\s*(?:please|thanks|thank you)\\s*[.!?]*$", "")
                .replaceFirst("[.!?,]+$", "").trim();
            // Pronouns, multiple clauses and instructions lack a stable search topic.
            if (query.length() < 3 || query.length() > 100 ||
                Pattern.compile("(?i)\\b(?:and|then|but|this|that|these|those|them|it)\\b").matcher(query).find()) return null;
            Action action = new Action("Share Photo");
            action.parameters.put("photo", query);
            return action;
        }
        Matcher day = DATE.matcher(text);
        if (!day.find()) return null;
        Matcher create = CREATE.matcher(text);
        Matcher time = TIME.matcher(text);
        if (create.find() && create.start(1) < day.start() && time.find() && time.start() > day.end()) {
            String between = text.substring(day.end(), time.start()).trim();
            if (!between.isEmpty() && !between.equalsIgnoreCase("at")) return null;
            String tail = text.substring(time.end()).trim();
            if (!tail.matches("(?i)[.!?,]*(?:\\s*please[.!?]*)?")) return null;
            String title = text.substring(create.start(1), day.start()).trim()
                .replaceFirst("(?i)^(?:a |an |the )?(?:calendar )?(?:event|appointment|meeting)\\s+(?:for|called|titled)\\s+", "")
                .replaceFirst("(?i)^(?:a |an |the )", "")
                .replaceFirst("(?i)\\s+(?:on|for|at)$", "").trim();
            if (title.length() < 3 || title.length() > 100 || title.contains("?")) return null;
            Action action = new Action("Create Calendar");
            action.parameters.put("title", title);
            action.parameters.put("start", day.group() + " at " + time.group());
            return action;
        }
        if (Pattern.compile("(?i)\\b(?:calendar|schedule|am i free|are you free|availability)\\b").matcher(text).find()
            && Pattern.compile("(?i)\\b(?:show|view|check|what|am i free|are you free)\\b").matcher(text).find()) {
            Action action = new Action("View Calendar");
            action.parameters.put("start", day.group());
            return action;
        }
        return null;
    }
}
