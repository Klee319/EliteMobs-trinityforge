package com.magmaguy.elitemobs.trinityforge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runs the JDK's own {@code javap -p -c -constants} against a single compiled class and slices the
 * output per method, so a test can assert "method X's OWN bytecode calls Y" rather than "method Y's
 * name appears somewhere in the class" (the blind spot {@link TrinityForgeGateWiringTest} documents:
 * the constant-pool substring check it uses cannot tell which method(s) in a class actually call a
 * gate, only whether the class references it from ANY method — see that class' javadoc, corrected
 * 2026-08-01 after two mutations proved it wrong).
 * <p>
 * Used for the two 2026-08-01 mutations that removed/weakened a gate at a SPECIFIC call site while
 * leaving another call site (in the same class) to the same policy method intact:
 * {@code CurrencyCustomLootEntry#directDrop} (the gate was deleted, {@code #locationDrop} kept it) and
 * {@code LootTables#generatePlayerLoot} (the {@code bonus_coins.yml} argument was replaced with a
 * literal {@code false}, the call to {@code shouldRunCurrencyShower} itself was untouched). Neither can
 * be driven behaviourally from a unit test without a live Bukkit server (a spawned elite, a player, and
 * — for the currency path — a working economy/database backend), which is exactly why the production
 * code could get away with silently regressing there in the first place.
 */
final class Javap {

    private Javap() {
    }

    /** Disassembles {@code clazz} via the running JVM's own {@code javap}. */
    static String disassemble(Class<?> clazz) throws IOException, InterruptedException {
        String resource = clazz.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (InputStream in = clazz.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("compiled class not found on the test classpath: " + resource);
            }
            bytes = in.readAllBytes();
        }
        Path dir = Files.createTempDirectory("javap-");
        String simpleName = clazz.getName().substring(clazz.getName().lastIndexOf('.') + 1);
        Path classFile = dir.resolve(simpleName + ".class");
        Files.write(classFile, bytes);

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path javapPath = Paths.get(System.getProperty("java.home"), "bin", windows ? "javap.exe" : "javap");

        ProcessBuilder pb = new ProcessBuilder(javapPath.toString(), "-p", "-c", "-constants", classFile.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("javap exited " + exit + " disassembling " + clazz.getName()
                    + ":\n" + output);
        }
        return output;
    }

    /**
     * Slices the text belonging to ONE method: from its own (2-space-indented) declaration line up to
     * (but not including) the next 2-space-indented member declaration, or end of file.
     *
     * @param methodSignaturePart a substring unique to the target method's declaration line, e.g.
     *                            {@code "void directDrop("}
     */
    static String sliceMethod(String disassembly, String methodSignaturePart) {
        String[] lines = disassembly.split("\n", -1);
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (isTopLevelMemberLine(lines[i]) && lines[i].contains(methodSignaturePart)) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            throw new AssertionError("method declaration not found in javap output (looked for a line "
                    + "containing: " + methodSignaturePart + ")\n--- full disassembly ---\n" + disassembly);
        }
        StringBuilder block = new StringBuilder(lines[start]).append('\n');
        for (int i = start + 1; i < lines.length; i++) {
            if (isTopLevelMemberLine(lines[i])) break;
            block.append(lines[i]).append('\n');
        }
        return block.toString();
    }

    /**
     * javap indents every class-body member (fields and methods) by exactly two spaces and everything
     * inside a method body (the {@code Code:} block, exception tables, ...) by four or more — so "two
     * spaces then a non-space" reliably marks the start of the next member.
     */
    private static boolean isTopLevelMemberLine(String line) {
        return line.length() > 2 && line.charAt(0) == ' ' && line.charAt(1) == ' ' && line.charAt(2) != ' ';
    }
}
