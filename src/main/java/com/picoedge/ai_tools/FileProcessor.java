package com.picoedge.ai_tools;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FileProcessor {
    private final String basePath;
    private final Map<String, String> EXTENSION_TO_LANGUAGE = new HashMap<>();
    private final List<String> filePaths = new ArrayList<>();
    private final Set<String> includeExtensions;
    private final Set<String> excludeExtensions;
    private final String prefixText;
    private final String suffixText;
    private final String diffPrefixText;
    private final String diffSuffixText;
    private final boolean disableFileTree; // Added to control file tree output
    private static final double BINARY_THRESHOLD = 0.1;
    static final String[] DEFAULT_ENV = loadDefaultEnv();

    private static String[] loadDefaultEnv() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        FileProcessor.class.getResourceAsStream("/plugin.env"),
                        StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            StringBuilder multiLine = new StringBuilder();
            String currentKey = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    if (currentKey != null) {
                        multiLine.append("\\n").append(line.trim());
                    }
                    continue;
                }
                if (currentKey != null && multiLine.length() > 0) {
                    lines.add(currentKey + "=" + multiLine);
                    multiLine.setLength(0);
                }
                if (line.contains("=")) {
                    currentKey = line.substring(0, line.indexOf('=')).trim();
                    String value = line.substring(line.indexOf('=') + 1).trim();
                    if (!value.isEmpty()) {
                        multiLine.append(value);
                    } else {
                        lines.add(line);
                    }
                }
            }
            if (currentKey != null && multiLine.length() > 0) {
                lines.add(currentKey + "=" + multiLine);
            }
            return lines.toArray(new String[0]);
        } catch (IOException | NullPointerException e) {
            return new String[] {
                    "COM_PICOEDGE_AI_TOOLS_INCLUDE_EXTENSIONS=ts,tsx,js,jsx,json,mjs,cjs,css,scss,html,md,yaml,yml,gradle,java,kt,xml,sh,env,astro",
                    "COM_PICOEDGE_AI_TOOLS_EXCLUDE_EXTENSIONS=avif,jpg,jpeg,png,gif,webp,svg,ico,bmp,tiff,swo,swp,bak,tmp,log,zip,tar,gz,bin,exe,o,a,so,dylib,class",
                    "COM_PICOEDGE_AI_TOOLS_PREFIX_TEXT=\\n\\n## Input Files and Response Standards\\n\\nBe sure to include concise comments where appropriate detailing key architectural/design points in portions of code which might become ambiguous without the context of the full application.\\n\\nIf file(s) are needed but not included below or in your recent memory please list them out stating they are needed to continue and end your response briefly.\\n\\nExisting relevant files in the same format as expected for your output, inclusive of file hierarchy of included files at the top:\\n\\n",
                    "COM_PICOEDGE_AI_TOOLS_SUFFIX_TEXT=",
                    "COM_PICOEDGE_AI_TOOLS_DIFF_PREFIX_TEXT=\\n\\n## Diff Files in Patch-Package Format\\n\\nThe following files represent diffs in patch-package format, showing changes against the latest .old version or as new files if no previous version exists. Please provide output in the same diff format, inclusive of file hierarchy of included files at the top:\\n\\n",
                    "COM_PICOEDGE_AI_TOOLS_DIFF_SUFFIX_TEXT=",
                    "COM_PICOEDGE_AI_TOOLS_TOUCHED=COM_PICOEDGE_AI_TOOLS_INCLUDE_EXTENSIONS,COM_PICOEDGE_AI_TOOLS_EXCLUDE_EXTENSIONS,COM_PICOEDGE_AI_TOOLS_PREFIX_TEXT,COM_PICOEDGE_AI_TOOLS_SUFFIX_TEXT,COM_PICOEDGE_AI_TOOLS_DIFF_PREFIX_TEXT,COM_PICOEDGE_AI_TOOLS_DIFF_SUFFIX_TEXT,COM_PICOEDGE_AI_TOOLS_DISABLE_FILE_TREE,COM_PICOEDGE_AI_TOOLS_LOGGER_MAX_LOGS,COM_PICOEDGE_AI_TOOLS_LOGGER_WS_URL,COM_PICOEDGE_AI_TOOLS_LOGGER_USE_LOCAL_SERVER",
                    "COM_PICOEDGE_AI_TOOLS_DISABLE_FILE_TREE=false",
                    "COM_PICOEDGE_AI_TOOLS_LOGGER_MAX_LOGS=10000",
                    "COM_PICOEDGE_AI_TOOLS_LOGGER_WS_URL=ws://localhost:1065/ws",
                    "COM_PICOEDGE_AI_TOOLS_LOGGER_USE_LOCAL_SERVER=true"
            };
        }
    }

    public FileProcessor(String basePath, Properties envProps) {
        this.basePath = basePath;
        EXTENSION_TO_LANGUAGE.put("java", "java");
        EXTENSION_TO_LANGUAGE.put("kt", "kotlin");
        EXTENSION_TO_LANGUAGE.put("xml", "xml");
        EXTENSION_TO_LANGUAGE.put("gradle", "groovy");
        EXTENSION_TO_LANGUAGE.put("properties", "properties");
        EXTENSION_TO_LANGUAGE.put("json", "json");
        EXTENSION_TO_LANGUAGE.put("html", "html");
        EXTENSION_TO_LANGUAGE.put("css", "css");
        EXTENSION_TO_LANGUAGE.put("js", "javascript");
        EXTENSION_TO_LANGUAGE.put("py", "python");
        EXTENSION_TO_LANGUAGE.put("sql", "sql");
        EXTENSION_TO_LANGUAGE.put("md", "markdown");
        EXTENSION_TO_LANGUAGE.put("astro", "astro");
        EXTENSION_TO_LANGUAGE.put("ts", "typescript");
        EXTENSION_TO_LANGUAGE.put("tsx", "typescript");
        EXTENSION_TO_LANGUAGE.put("jsx", "javascript");
        EXTENSION_TO_LANGUAGE.put("mjs", "javascript");
        EXTENSION_TO_LANGUAGE.put("cjs", "javascript");
        EXTENSION_TO_LANGUAGE.put("scss", "scss");
        EXTENSION_TO_LANGUAGE.put("yaml", "yaml");
        EXTENSION_TO_LANGUAGE.put("yml", "yaml");
        EXTENSION_TO_LANGUAGE.put("sh", "bash");
        EXTENSION_TO_LANGUAGE.put("env", "properties");

        Set<String> defaultInclude = new HashSet<>(Arrays.asList(
                "ts", "tsx", "js", "jsx", "json", "mjs", "cjs", "css", "scss", "html",
                "md", "yaml", "yml", "gradle", "java", "kt", "xml", "sh", "env", "astro"
        ));
        Set<String> defaultExclude = new HashSet<>(Arrays.asList(
                "avif", "jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "bmp", "tiff",
                "swo", "swp", "bak", "tmp", "log", "zip", "tar", "gz", "bin", "exe",
                "o", "a", "so", "dylib", "class"
        ));

        String includeExt = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_INCLUDE_EXTENSIONS");
        String excludeExt = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_EXCLUDE_EXTENSIONS");
        String prefix = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_PREFIX_TEXT", "");
        String suffix = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_SUFFIX_TEXT", "");
        String diffPrefix = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_DIFF_PREFIX_TEXT", "");
        String diffSuffix = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_DIFF_SUFFIX_TEXT", "");
        String disableFileTreeStr = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_DISABLE_FILE_TREE", "false");

        if (StringUtils.isNotBlank(includeExt)) {
            includeExtensions = new HashSet<>(Arrays.asList(includeExt.toLowerCase().split("\\s*,\\s*")));
        } else {
            includeExtensions = defaultInclude;
        }

        if (StringUtils.isNotBlank(excludeExt)) {
            excludeExtensions = new HashSet<>(Arrays.asList(excludeExt.toLowerCase().split("\\s*,\\s*")));
        } else {
            excludeExtensions = defaultExclude;
        }

        this.prefixText = prefix;
        this.suffixText = suffix;
        this.diffPrefixText = diffPrefix;
        this.diffSuffixText = diffSuffix;
        this.disableFileTree = Boolean.parseBoolean(disableFileTreeStr); // Initialize from .env
    }

    public String getPrefixText() {
        return prefixText;
    }

    public String getSuffixText() {
        return suffixText;
    }

    public String getDiffPrefixText() {
        return diffPrefixText;
    }

    public String getDiffSuffixText() {
        return diffSuffixText;
    }

    public String processFiles(VirtualFile[] files) {
        filePaths.clear();
        StringBuilder result = new StringBuilder();
        Arrays.stream(files).forEach(file -> processFile(file, result));
        String tree = disableFileTree ? "" : buildAsciiTree(); // Skip tree if disabled
        String output = tree + result.toString();
        return output.isEmpty() ? "" : output;
    }

    public String processFilesForDiff(VirtualFile[] files) {
        filePaths.clear();
        StringBuilder result = new StringBuilder();
        Arrays.stream(files).forEach(file -> processFileForDiff(file, result));
        String tree = disableFileTree ? "" : buildAsciiTree(); // Skip tree if disabled
        String output = tree + result.toString();
        return output.isEmpty() ? "" : output;
    }

    private void processFile(VirtualFile file, StringBuilder result) {
        if (file.isDirectory()) {
            VirtualFile[] children = file.getChildren();
            for (VirtualFile child : children) {
                processFile(child, result);
            }
        } else if (file.isValid() && !file.isDirectory() && isTextFile(file)) {
            appendFileContents(file, result);
        }
    }

    private void processFileForDiff(VirtualFile file, StringBuilder result) {
        if (file.isDirectory()) {
            VirtualFile[] children = file.getChildren();
            for (VirtualFile child : children) {
                processFileForDiff(child, result);
            }
        } else if (file.isValid() && !file.isDirectory() && isTextFile(file)) {
            appendFileDiff(file, result);
        }
    }

    private boolean isTextFile(VirtualFile file) {
        String extension = file.getExtension();
        if (extension == null) {
            return false;
        }
        extension = extension.toLowerCase();

        if (excludeExtensions.contains(extension)) {
            return false;
        }

        if (!includeExtensions.isEmpty()) {
            return includeExtensions.contains(extension);
        }

        if (extension.equals("astro")) {
            try {
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                return !isBinaryContent(content);
            } catch (IOException e) {
                return false;
            }
        }

        return includeExtensions.contains(extension);
    }

    private boolean isBinaryContent(String content) {
        if (content == null || content.isEmpty()) {
            return true;
        }

        int totalChars = content.length();
        int nonAsciiChars = 0;

        for (int i = 0; i < totalChars; i++) {
            char c = content.charAt(i);
            if ((c < 0x20 || c > 0x7E) && (c < 0xA0 || c > 0xFF)) {
                nonAsciiChars++;
            }
        }

        return ((double) nonAsciiChars / totalChars) > BINARY_THRESHOLD;
    }

    private void appendFileContents(VirtualFile file, StringBuilder result) {
        try {
            String relativePath = getRelativePath(file);
            filePaths.add(relativePath);
            String language = getLanguageFromExtension(file.getExtension());
            String contents = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);

            result.append("#### ").append(relativePath).append("\n");
            result.append("```").append(language).append("\n");
            result.append(contents);
            if (!contents.endsWith("\n")) {
                result.append("\n");
            }
            result.append("```\n\n");
        } catch (IOException e) {
            // Skip files that can't be read
        }
    }

    private void appendFileDiff(VirtualFile file, StringBuilder result) {
        try {
            String relativePath = getRelativePath(file);
            filePaths.add(relativePath);
            String language = getLanguageFromExtension(file.getExtension());
            String currentContent = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);

            // Find the latest version to compare against
            VirtualFile parentDir = file.getParent();
            String fileName = file.getName();
            VirtualFile baseFile = parentDir.findChild(fileName + ".latest");
            int maxOldNumber = -1;
            if (baseFile == null) {
                // Check for highest-numbered .old file
                for (VirtualFile child : parentDir.getChildren()) {
                    String name = child.getName();
                    if (name.startsWith(fileName + ".") && name.endsWith(".old")) {
                        try {
                            String numberStr = name.substring(fileName.length() + 1, name.length() - 4);
                            int number = Integer.parseInt(numberStr);
                            if (number > maxOldNumber) {
                                maxOldNumber = number;
                                baseFile = child;
                            }
                        } catch (NumberFormatException ignored) {
                            // Skip invalid .old files
                        }
                    }
                }
            }

            String diffContent;
            if (baseFile != null) {
                // Generate diff against base file (.latest or highest .old)
                String baseContent = new String(baseFile.contentsToByteArray(), StandardCharsets.UTF_8);
                diffContent = generateDiff(relativePath, baseContent, currentContent);
            } else {
                // Generate diff as new file
                diffContent = generateNewFileDiff(relativePath, currentContent);
            }

            result.append("#### ").append(relativePath).append("\n");
            result.append("```").append(language.isEmpty() ? ":diff:index 8c97fbd..5eea78c 100644" : language + ":diff:index 8c97fbd..5eea78c 100644").append("\n");
            result.append(diffContent);
            if (!diffContent.endsWith("\n")) {
                result.append("\n");
            }
            result.append("```\n\n");
        } catch (IOException e) {
            // Skip files that can't be read
        }
    }

    private String generateDiff(String fileName, String oldContent, String newContent) {
        StringBuilder diff = new StringBuilder();
        diff.append("diff --git a/").append(fileName).append(" b/").append(fileName).append("\n");
        diff.append("index 8c97fbd..5eea78c 100644\n");
        diff.append("--- a/").append(fileName).append("\n");
        diff.append("+++ b/").append(fileName).append("\n");

        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        List<DiffHunk> hunks = computeMinimalDiffHunks(oldLines, newLines);
        for (DiffHunk hunk : hunks) {
            diff.append("@@ -").append(hunk.oldStart);
            if (hunk.oldCount > 0) {
                diff.append(",").append(hunk.oldCount);
            }
            diff.append(" +").append(hunk.newStart);
            if (hunk.newCount > 0) {
                diff.append(",").append(hunk.newCount);
            }
            diff.append(" @@\n");
            for (String line : hunk.lines) {
                diff.append(line).append("\n");
            }
        }

        return hunks.isEmpty() ? "" : diff.toString();
    }

    private String generateNewFileDiff(String fileName, String content) {
        StringBuilder diff = new StringBuilder();
        diff.append("diff --git a/").append(fileName).append(" b/").append(fileName).append("\n");
        diff.append("new file mode 100644\n");
        diff.append("index 0000000..5eea78c\n");
        diff.append("--- /dev/null\n");
        diff.append("+++ b/").append(fileName).append("\n");
        diff.append("@@ -0,0 +1,").append(content.split("\n", -1).length).append(" @@\n");
        for (String line : content.split("\n", -1)) {
            diff.append("+").append(line).append("\n");
        }
        return diff.toString();
    }

    private List<DiffHunk> computeMinimalDiffHunks(String[] oldLines, String[] newLines) {
        List<DiffHunk> hunks = new ArrayList<>();
        int i = 0;
        int j = 0;
        int oldLineNum = 1;
        int newLineNum = 1;

        while (i < oldLines.length || j < newLines.length) {
            List<String> hunkLines = new ArrayList<>();
            int oldHunkStart = oldLineNum;
            int newHunkStart = newLineNum;
            int oldHunkCount = 0;
            int newHunkCount = 0;

            // Skip matching lines
            while (i < oldLines.length && j < newLines.length &&
                    Objects.equals(oldLines[i], newLines[j])) {
                i++;
                j++;
                oldLineNum++;
                newLineNum++;
            }

            // Collect differences for the hunk
            while (i < oldLines.length || j < newLines.length) {
                String oldLine = i < oldLines.length ? oldLines[i] : null;
                String newLine = j < newLines.length ? newLines[j] : null;

                // Check for matching lines to end the hunk
                if (oldLine != null && newLine != null && Objects.equals(oldLine, newLine)) {
                    if (!hunkLines.isEmpty()) {
                        // Only add the hunk if changes were collected
                        hunks.add(new DiffHunk(oldHunkStart, oldHunkCount, newHunkStart, newHunkCount, hunkLines));
                    }
                    break;
                }

                // Add removed line
                if (oldLine != null) {
                    hunkLines.add("-" + oldLine);
                    oldHunkCount++;
                    i++;
                    oldLineNum++;
                }
                // Add added line
                if (newLine != null) {
                    hunkLines.add("+" + (newLine.isEmpty() ? "" : newLine));
                    newHunkCount++;
                    j++;
                    newLineNum++;
                }

                // End hunk if we've collected changes and the next lines match or we reach the end
                if (!hunkLines.isEmpty() &&
                        (i >= oldLines.length || j >= newLines.length ||
                                Objects.equals(oldLines[i], newLines[j]))) {
                    hunks.add(new DiffHunk(oldHunkStart, oldHunkCount, newHunkStart, newHunkCount, hunkLines));
                    break;
                }
            }

            // Add final hunk if we reached the end with changes
            if (!hunkLines.isEmpty() && (i >= oldLines.length || j >= newLines.length)) {
                hunks.add(new DiffHunk(oldHunkStart, oldHunkCount, newHunkStart, newHunkCount, hunkLines));
            }
        }

        return hunks;
    }

    private String getRelativePath(VirtualFile file) {
        String path = file.getPath();
        if (path.startsWith(basePath)) {
            path = StringUtils.removeStart(path, basePath);
            path = path.startsWith("/") ? path.substring(1) : path;
        }
        return path;
    }

    private String getLanguageFromExtension(String extension) {
        if (extension == null) {
            return "";
        }
        return EXTENSION_TO_LANGUAGE.getOrDefault(extension.toLowerCase(), "");
    }

    private String buildAsciiTree() {
        if (filePaths.isEmpty()) {
            return "";
        }

        Map<String, List<String>> directoryMap = new TreeMap<>();
        String commonPrefix = findCommonPrefix(filePaths);

        for (String path : filePaths) {
            String relativePath = path.startsWith(commonPrefix) ? path.substring(commonPrefix.length()) : path;
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            int lastSlash = relativePath.lastIndexOf('/');
            String dir = lastSlash >= 0 ? relativePath.substring(0, lastSlash) : "";
            String fileName = lastSlash >= 0 ? relativePath.substring(lastSlash + 1) : relativePath;
            directoryMap.computeIfAbsent(dir, k -> new ArrayList<>()).add(fileName);
        }

        StringBuilder tree = new StringBuilder();
        tree.append("```\n");
        tree.append(commonPrefix.isEmpty() ? "." : commonPrefix).append("\n");

        directoryMap.forEach((dir, files) -> {
            if (!dir.isEmpty()) {
                String[] parts = dir.split("/");
                for (int i = 0; i < parts.length; i++) {
                    tree.append("│   ".repeat(i)).append("├── ").append(parts[i]).append("\n");
                }
            }
            Collections.sort(files);
            for (int i = 0; i < files.size(); i++) {
                String prefix = dir.isEmpty() ? "" : "│   ".repeat(dir.split("/").length);
                tree.append(prefix).append(i == files.size() - 1 && directoryMap.keySet().stream().allMatch(k -> k.equals(dir) || !k.startsWith(dir)) ? "└── " : "├── ").append(files.get(i)).append("\n");
            }
        });

        tree.append("```\n\n");
        return tree.toString();
    }

    private String findCommonPrefix(List<String> paths) {
        if (paths.isEmpty()) {
            return "";
        }
        if (paths.size() == 1) {
            int lastSlash = paths.get(0).lastIndexOf('/');
            return lastSlash >= 0 ? paths.get(0).substring(0, lastSlash) : "";
        }

        String first = paths.get(0);
        int minLen = paths.stream().mapToInt(String::length).min().orElse(0);
        int i = 0;
        while (i < minLen) {
            char currentChar = first.charAt(i);
            boolean allMatch = true;
            for (String path : paths) {
                if (path.charAt(i) != currentChar) {
                    allMatch = false;
                    break;
                }
            }
            if (!allMatch) {
                break;
            }
            i++;
        }
        String prefix = first.substring(0, i);
        int lastSlash = prefix.lastIndexOf('/');
        return lastSlash >= 0 ? prefix.substring(0, lastSlash) : "";
    }

    private static class DiffHunk {
        int oldStart;
        int oldCount;
        int newStart;
        int newCount;
        List<String> lines;

        DiffHunk(int oldStart, int oldCount, int newStart, int newCount, List<String> lines) {
            this.oldStart = oldStart;
            this.oldCount = oldCount;
            this.newStart = newStart;
            this.newCount = newCount;
            this.lines = lines;
        }
    }
}