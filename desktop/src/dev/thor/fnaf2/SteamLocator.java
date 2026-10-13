package dev.thor.fnaf2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only discovery of FNaF 2 in Steam's registered libraries. Never runs the game. */
final class SteamLocator {
    static final String EXE = "FiveNightsatFreddys2.exe";
    static List<Path> findInstalled() {
        Set<Path> roots = new LinkedHashSet<>();
        for (String key : List.of("HKCU\\Software\\Valve\\Steam",
                "HKLM\\SOFTWARE\\Valve\\Steam", "HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam")) {
            add(roots, registryPath(key, key.startsWith("HKCU") ? "SteamPath" : "InstallPath"));
        }
        for (String env : List.of("ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA")) {
            String base = System.getenv(env);
            if (base != null) add(roots, Path.of(base, "Steam").toString());
        }
        return findInRoots(roots);
    }

    private static String registryPath(String key, String value) {
        Process process = null;
        try {
            String windows = System.getenv("SystemRoot");
            if (windows == null) return null;
            process = new ProcessBuilder(Path.of(windows, "System32", "reg.exe").toString(),
                    "query", key, "/v", value).redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) return null;
            // Registry output uses the Windows console encoding; paths may contain Unicode.
            java.nio.charset.Charset encoding = java.nio.charset.Charset.forName(
                    System.getProperty("native.encoding", java.nio.charset.Charset.defaultCharset().name()));
            String output = new String(process.getInputStream().readNBytes(32768), encoding);
            for (String line : output.split("\\R")) {
                String[] fields = line.trim().split("\\s+", 3);
                if (fields.length == 3 && fields[0].equalsIgnoreCase(value)
                        && fields[1].equals("REG_SZ")) return fields[2];
            }
        } catch (IOException | RuntimeException ignored) {
            // Standard locations and manual browsing remain available.
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); }
        return null;
    }

    static List<Path> findInRoots(Collection<Path> roots) {
        Set<Path> libraries = new LinkedHashSet<>(roots);
        for (Path root : roots) {
            for (String relative : List.of("steamapps/libraryfolders.vdf", "config/libraryfolders.vdf")) {
                Map<String,Object> folders = section(read(root.resolve(relative)), "libraryfolders");
                for (Map.Entry<String,Object> entry : folders.entrySet()) {
                    if (!entry.getKey().matches("[0-9]+")) continue;
                    Object value = entry.getValue();
                    add(libraries, value instanceof String ? (String)value : string(section(folders, entry.getKey()), "path"));
                }
            }
        }
        Set<Path> found = new LinkedHashSet<>();
        for (Path library : libraries) {
            if (Thread.currentThread().isInterrupted()) break;
            Path common = library.resolve("steamapps/common").normalize();
            Map<String,Object> manifest = section(read(library.resolve("steamapps/appmanifest_332800.acf")), "appstate");
            String folder = string(manifest, "installdir");
            if ("332800".equals(string(manifest, "appid")) && folder != null) {
                try {
                    Path install = common.resolve(folder).normalize();
                    if (install.startsWith(common) && !install.equals(common)) candidate(found, install.resolve(EXE));
                } catch (InvalidPathException ignored) { }
            }
            // Works with an offline/copied Steam installation whose manifest is missing.
            candidate(found, common.resolve("Five Nights at Freddy's 2").resolve(EXE));
        }
        return new ArrayList<>(found);
    }

    private static void candidate(Set<Path> found, Path path) {
        try { if (Files.isRegularFile(path) && Files.isReadable(path) && Files.size(path) > 0) found.add(path.toRealPath()); }
        catch (IOException | SecurityException ignored) { }
    }
    private static void add(Set<Path> paths, String value) {
        if (value == null || value.isBlank()) return;
        try { Path p = Path.of(value); if (p.isAbsolute()) paths.add(p.toAbsolutePath().normalize()); }
        catch (InvalidPathException ignored) { }
    }
    private static String string(Map<String,Object> map, String key) { Object v=map.get(key);return v instanceof String?(String)v:null; }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> section(Map<String,Object> map, String key) {
        Object value=map.get(key);return value instanceof Map?(Map<String,Object>)value:Collections.emptyMap();
    }
    private static Map<String,Object> read(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path)>2*1024*1024) return Collections.emptyMap();
            return new Vdf(Files.readString(path,StandardCharsets.UTF_8)).map(false,0);
        } catch (IOException | IllegalArgumentException | SecurityException e) { return Collections.emptyMap(); }
    }

    /** Small KeyValues reader: escaped paths, nested objects, comments, old and new library layouts. */
    private static final class Vdf {
        final String source; int offset;
        Vdf(String text) { source=text.startsWith("\uFEFF")?text.substring(1):text; }
        Map<String,Object> map(boolean nested,int depth) {
            if(depth>32)throw new IllegalArgumentException("VDF nesting");
            Map<String,Object> result=new LinkedHashMap<>();
            while(true) {
                String key=token();
                if(key==null){if(nested)throw new IllegalArgumentException("Unclosed object");return result;}
                if(key.equals("}")){if(!nested)throw new IllegalArgumentException("Unexpected close");return result;}
                if(key.equals("{"))throw new IllegalArgumentException("Missing key");
                String value=token();if(value==null||value.equals("}"))throw new IllegalArgumentException("Missing value");
                result.put(key.toLowerCase(Locale.ROOT),value.equals("{")?map(true,depth+1):value);
            }
        }
        String token() {
            while(offset<source.length()) {
                if(Character.isWhitespace(source.charAt(offset))){offset++;continue;}
                if(source.startsWith("//",offset)){while(offset<source.length()&&source.charAt(offset)!='\n')offset++;continue;}
                break;
            }
            if(offset==source.length())return null;
            char c=source.charAt(offset++);
            if(c=='{'||c=='}')return String.valueOf(c);
            StringBuilder b=new StringBuilder();
            if(c=='"') {
                while(offset<source.length()) {
                    c=source.charAt(offset++);if(c=='"')return b.toString();
                    if(c=='\\'&&offset<source.length()&&(source.charAt(offset)=='\\'||source.charAt(offset)=='"'))c=source.charAt(offset++);
                    b.append(c);
                }
                throw new IllegalArgumentException("Unclosed string");
            }
            b.append(c);while(offset<source.length()&&!Character.isWhitespace(source.charAt(offset))&&source.charAt(offset)!='{'&&source.charAt(offset)!='}')b.append(source.charAt(offset++));
            return b.toString();
        }
    }
}
