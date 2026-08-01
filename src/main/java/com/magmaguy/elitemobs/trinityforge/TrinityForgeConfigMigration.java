package com.magmaguy.elitemobs.trinityforge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Adds newly-shipped {@code trinityforge.yml} sections to a server's EXISTING config file.
 * <p>
 * <b>Why this exists.</b> {@code TrinityForgeIntegration#loadConfig} writes the shipped default only
 * when the file is absent ({@code if (!file.exists()) plugin.saveResource(...)}). On every server that
 * already had a {@code plugins/EliteMobs/trinityforge.yml}, a new section such as
 * {@code elite-drop-sources:} or {@code native-display-suppression:} therefore NEVER appears on disk:
 * the code-side default silently takes effect after a jar swap and the admin has no key to change it
 * back with. That is exactly how the 2026-08-01 defaults (every random drop source blocked, every
 * nametag hidden) would have reached live servers invisibly.
 * <p>
 * <b>What it does.</b> Any top-level key present in the jar's shipped {@code trinityforge.yml} but
 * absent from the on-disk file is appended verbatim — its documentation comments included — to the end
 * of the on-disk file. <b>Existing values are never read, rewritten or reordered</b>: the file is only
 * ever appended to, and only with keys the loaded {@code YamlConfiguration} says are missing, so an
 * admin's edits (and their own comments) cannot be clobbered and no duplicate key can be introduced.
 * <p>
 * Scope limit, stated honestly: only TOP-LEVEL keys are reconciled. A new child key added inside an
 * already-present section (e.g. a future {@code elite-drop-sources.something-new}) is not appended —
 * for those the per-key code default still applies silently. Ship genuinely new switches as their own
 * top-level section, or extend this class.
 */
public final class TrinityForgeConfigMigration {

    /** A line that starts a top-level mapping key: no indentation, {@code key:} at column 0. */
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z0-9_][A-Za-z0-9_.\\-]*):(\\s.*)?$");

    /** Banner written above the appended block so an admin can see where the additions came from. */
    static final String APPENDIX_HEADER =
            "# ---------------------------------------------------------------------------\n"
                    + "# 以下は EliteMobs の更新で新しく追加された設定項目です (自動追記)。\n"
                    + "# 追記されるのは「このファイルに存在しなかったキー」だけで、既存の値は書き換えません。\n"
                    + "# 値はいずれも jar 側の既定値なので、これまでの挙動を変えたくない場合はここを編集してください。\n"
                    + "# ---------------------------------------------------------------------------";

    private TrinityForgeConfigMigration() {
    }

    /**
     * Splits a {@code trinityforge.yml} text into {@code top-level key -> block}, where a block is the
     * key's own lines plus the contiguous run of comment lines directly above it (its documentation).
     * <p>
     * The walk-back stops at the first blank line, which is what keeps the file's top-of-file header
     * from being glued onto the first key, and what keeps one section's trailing comments from being
     * stolen by the next section. Order of the returned map follows the file.
     *
     * @param yamlText the shipped resource's text (this method is never pointed at a user's file)
     */
    static LinkedHashMap<String, String> splitTopLevelBlocks(String yamlText) {
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        if (yamlText == null || yamlText.isEmpty()) return blocks;
        String[] lines = yamlText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        // Pass 1: index every top-level key line, and the first line of its attached comment run.
        List<String> keys = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            java.util.regex.Matcher matcher = TOP_LEVEL_KEY.matcher(lines[i]);
            if (!matcher.matches()) continue;
            int start = i;
            while (start > 0 && lines[start - 1].startsWith("#")) start--;
            keys.add(matcher.group(1));
            starts.add(start);
        }

        // Pass 2: each block runs until the next block's start (or EOF), minus trailing blank lines.
        for (int k = 0; k < keys.size(); k++) {
            int from = starts.get(k);
            int to = (k + 1 < keys.size()) ? starts.get(k + 1) : lines.length;
            StringBuilder block = new StringBuilder();
            for (int i = from; i < to; i++) {
                if (block.length() > 0) block.append('\n');
                block.append(lines[i]);
            }
            String text = stripTrailingBlankLines(block.toString());
            // A duplicated top-level key in the shipped resource would be a bug in the resource itself;
            // keep the first so the appended text matches what the loader would have used.
            blocks.putIfAbsent(keys.get(k), text);
        }
        return blocks;
    }

    /**
     * Builds the text to append for every shipped key that the on-disk file does not have.
     *
     * @param shippedBlocks output of {@link #splitTopLevelBlocks(String)}
     * @param presentKeys   the on-disk file's top-level keys, as reported by the loaded
     *                      {@code YamlConfiguration} (authoritative — never a regex over the user's file,
     *                      so a mis-parse can never make us append a key that already exists)
     * @param addedKeysOut  receives the keys that were appended, in file order; may be {@code null}
     * @return the text to append, or an empty string when nothing is missing
     */
    static String buildAppendix(Map<String, String> shippedBlocks, Set<String> presentKeys,
                                List<String> addedKeysOut) {
        StringBuilder appendix = new StringBuilder();
        for (Map.Entry<String, String> entry : shippedBlocks.entrySet()) {
            if (presentKeys != null && presentKeys.contains(entry.getKey())) continue;
            if (appendix.length() > 0) appendix.append("\n\n");
            appendix.append(entry.getValue());
            if (addedKeysOut != null) addedKeysOut.add(entry.getKey());
        }
        if (appendix.length() == 0) return "";
        return APPENDIX_HEADER + "\n\n" + appendix + "\n";
    }

    /**
     * Appends every shipped top-level key missing from {@code file}. Never modifies existing content.
     *
     * @param file        the on-disk {@code trinityforge.yml}
     * @param presentKeys the on-disk file's top-level keys ({@code YamlConfiguration#getKeys(false)})
     * @param shippedText the jar resource's text
     * @return the keys appended, in file order; empty when the file was already complete
     * @throws IOException if the file cannot be appended to (the caller logs and carries on — a failed
     *                     migration must never abort {@code onEnable})
     */
    static List<String> appendMissingKeys(Path file, Set<String> presentKeys, String shippedText)
            throws IOException {
        List<String> added = new ArrayList<>();
        String appendix = buildAppendix(splitTopLevelBlocks(shippedText), presentKeys, added);
        if (appendix.isEmpty()) return added;
        String existing = Files.readString(file, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(stripTrailingBlankLines(existing));
        out.append("\n\n").append(appendix);
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        return added;
    }

    private static String stripTrailingBlankLines(String text) {
        int end = text.length();
        while (end > 0) {
            char c = text.charAt(end - 1);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') end--;
            else break;
        }
        return text.substring(0, end);
    }
}
