package com.picoedge.ai_tools;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PasteDiffAction extends AnAction {
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "####\\s*(.+?)\\s*\\n```(?:(\\w*):)?diff:index\\s*\\w+\\.\\.\\w+\\s*\\d+\\s*\\n([\\s\\S]*?)\\n```\\s*(?:\\n|$)",
            Pattern.MULTILINE
    );
    private static final Pattern TREE_PATTERN = Pattern.compile(
            "```\\n(.*?)\\n```\\n\\n",
            Pattern.DOTALL
    );
    private static final Pattern DIFF_HEADER = Pattern.compile(
            "diff --git a/(.+?) b/\\1\\n(?:new file mode \\d+\\n)?index \\w+\\.\\.\\w+.*\\n--- (?:a/(.+?)|/dev/null)\\n\\+\\+\\+ b/(.+?)\\n([\\s\\S]*)",
            Pattern.MULTILINE
    );
    private static final Pattern HUNK_HEADER = Pattern.compile(
            "@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*"
    );

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(file != null && file.isDirectory());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile directory = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || directory == null || !directory.isDirectory()) {
            Messages.showMessageDialog(project, "Please select a directory.", "Error", Messages.getErrorIcon());
            return;
        }

        String clipboardContent = getClipboardContent();
        if (clipboardContent == null) {
            Messages.showMessageDialog(project, "Clipboard is empty or inaccessible.", "Error", Messages.getErrorIcon());
            return;
        }

        String sourceRoot = extractSourceRoot(clipboardContent);
        List<FileEntry> files = parseClipboardContent(clipboardContent);
        if (files.isEmpty()) {
            Messages.showMessageDialog(project, "No valid diff files found in clipboard content.", "Error", Messages.getErrorIcon());
            return;
        }

        PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(directory);
        if (psiDirectory == null) {
            Messages.showMessageDialog(project, "Could not access the selected directory.", "Error", Messages.getErrorIcon());
            return;
        }

        String projectBasePath = project.getBasePath();
        boolean isProjectRoot = projectBasePath != null && directory.getPath().equals(projectBasePath);
        List<String> errors = new ArrayList<>();

        WriteCommandAction.runWriteCommandAction(project, () -> {
            for (FileEntry fileEntry : files) {
                String relativePath = isProjectRoot ? fileEntry.filePath : remapPath(fileEntry.filePath, sourceRoot);
                if (StringUtils.isBlank(relativePath)) {
                    errors.add("Invalid file path: " + fileEntry.filePath);
                    continue;
                }
                try {
                    applyDiff(psiDirectory, relativePath, fileEntry.content);
                } catch (IOException ex) {
                    errors.add("Failed to apply diff for file: " + relativePath + " (" + ex.getMessage() + ")");
                }
            }
        });

        if (errors.isEmpty()) {
            Messages.showMessageDialog(project, "Diffs have been applied successfully.", "Success", Messages.getInformationIcon());
        } else {
            Messages.showMessageDialog(project, "Some diffs could not be applied:\n" + String.join("\n", errors), "Error", Messages.getErrorIcon());
        }
    }

    private String getClipboardContent() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return (String) clipboard.getData(DataFlavor.stringFlavor);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractSourceRoot(String content) {
        Matcher matcher = TREE_PATTERN.matcher(content);
        if (matcher.find()) {
            String tree = matcher.group(1);
            String[] lines = tree.split("\n");
            if (lines.length > 0) {
                String root = lines[0].trim();
                return StringUtils.isBlank(root) ? "" : root;
            }
        }
        return "";
    }

    private List<FileEntry> parseClipboardContent(String content) {
        List<FileEntry> files = new ArrayList<>();
        Matcher matcher = FILE_PATTERN.matcher(content);
        while (matcher.find()) {
            String filePath = matcher.group(1).trim();
            String fileContent = matcher.group(3);
            if (!StringUtils.isBlank(filePath)) {
                files.add(new FileEntry(filePath, fileContent));
            }
        }
        return files;
    }

    private String remapPath(String originalPath, String sourceRoot) {
        if (StringUtils.isBlank(originalPath)) {
            return "";
        }

        String normalizedSourceRoot = StringUtils.isBlank(sourceRoot) ? "" : sourceRoot.replaceAll("^/+|/+$", "");
        String normalizedOriginalPath = originalPath.replaceAll("^/+|/+$", "");

        String relativePath = normalizedOriginalPath;
        if (!normalizedSourceRoot.isEmpty() && normalizedOriginalPath.startsWith(normalizedSourceRoot + "/")) {
            relativePath = normalizedOriginalPath.substring(normalizedSourceRoot.length() + 1);
        }

        if (StringUtils.isBlank(relativePath)) {
            return "";
        }

        return relativePath.replaceAll("/+", "/");
    }

    private void applyDiff(PsiDirectory baseDirectory, String relativePath, String diffContent) throws IOException {
        if (StringUtils.isBlank(relativePath)) {
            throw new IOException("Invalid file path");
        }

        String[] pathParts = relativePath.split("/");
        if (pathParts.length == 0 || StringUtils.isBlank(pathParts[pathParts.length - 1])) {
            throw new IOException("Invalid file name in path: " + relativePath);
        }

        VirtualFile baseDir = baseDirectory.getVirtualFile();
        VirtualFile currentDir = baseDir;

        // Create subdirectories if needed
        for (int i = 0; i < pathParts.length - 1; i++) {
            String subDirName = pathParts[i];
            if (StringUtils.isBlank(subDirName)) {
                continue;
            }
            VirtualFile subDir = currentDir.findChild(subDirName);
            if (subDir == null) {
                subDir = currentDir.createChildDirectory(this, subDirName);
            }
            currentDir = subDir;
        }

        String fileName = pathParts[pathParts.length - 1];
        VirtualFile existingFile = currentDir.findChild(fileName);

        // Parse diff content
        Matcher matcher = DIFF_HEADER.matcher(diffContent);
        if (!matcher.find()) {
            throw new IOException("Invalid diff format for: " + relativePath);
        }

        String diffBody = matcher.group(4);
        String oldContent = "";
        VirtualFile baseFile = currentDir.findChild(fileName + ".latest");
        int maxOldNumber = -1;
        if (baseFile == null) {
            // Check for highest-numbered .old file
            for (VirtualFile child : currentDir.getChildren()) {
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

        if (baseFile != null) {
            oldContent = new String(baseFile.contentsToByteArray(), StandardCharsets.UTF_8);
        }

        // Apply diff
        String newContent = applyPatch(oldContent, diffBody);

        // Handle existing file by renaming to next .old number
        final int nextOldNumber = maxOldNumber + 1;
        if (existingFile != null) {
            final String oldFileName = String.format("%s.%03d.old", fileName, nextOldNumber);
            WriteCommandAction.runWriteCommandAction(baseDirectory.getProject(), () -> {
                try {
                    existingFile.rename(null, oldFileName);
                } catch (IOException ex) {
                    Messages.showMessageDialog(baseDirectory.getProject(), "Failed to rename file: " + fileName, "Error", Messages.getErrorIcon());
                }
            });
        }

        // Create new file
        final VirtualFile finalNewFile = currentDir.createChildData(this, fileName);
        WriteCommandAction.runWriteCommandAction(baseDirectory.getProject(), () -> {
            try {
                finalNewFile.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                Messages.showMessageDialog(baseDirectory.getProject(), "Failed to write file: " + fileName, "Error", Messages.getErrorIcon());
            }
        });

        // Update or create .latest file
        final VirtualFile existingLatestFile = currentDir.findChild(fileName + ".latest");
        if (existingLatestFile != null) {
            final String oldLatestName = String.format("%s.%03d.old", fileName, nextOldNumber + 1);
            WriteCommandAction.runWriteCommandAction(baseDirectory.getProject(), () -> {
                try {
                    existingLatestFile.rename(null, oldLatestName);
                } catch (IOException ex) {
                    Messages.showMessageDialog(baseDirectory.getProject(), "Failed to rename latest file: " + fileName, "Error", Messages.getErrorIcon());
                }
            });
        }

        // Create new .latest file
        final VirtualFile newLatestFile = currentDir.createChildData(this, fileName + ".latest");
        WriteCommandAction.runWriteCommandAction(baseDirectory.getProject(), () -> {
            try {
                newLatestFile.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                Messages.showMessageDialog(baseDirectory.getProject(), "Failed to write latest file: " + fileName, "Error", Messages.getErrorIcon());
            }
        });
    }

    private String applyPatch(String oldContent, String diffBody) {
        List<String> resultLines = oldContent.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(oldContent.split("\n", -1)));
        String[] diffLines = diffBody.split("\n", -1);
        int currentLine = 0;

        for (int i = 0; i < diffLines.length; i++) {
            String line = diffLines[i];
            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.matches()) {
                int oldStart = Integer.parseInt(hunkMatcher.group(1)) - 1;
                String oldCountStr = hunkMatcher.group(2);
                int oldCount = oldCountStr != null ? Integer.parseInt(oldCountStr) : 0;
                int newStart = Integer.parseInt(hunkMatcher.group(3)) - 1;
                String newCountStr = hunkMatcher.group(4);
                int newCount = newCountStr != null ? Integer.parseInt(newCountStr) : 0;
                currentLine = newStart;

                // Ensure the resultLines list has enough lines to handle the new start position
                while (resultLines.size() <= newStart) {
                    resultLines.add("");
                }

                // Remove lines as specified in the diff
                if (oldCount > 0) {
                    for (int j = 0; j < oldCount && oldStart < resultLines.size(); j++) {
                        resultLines.remove(oldStart);
                    }
                }
            } else if (line.startsWith("-")) {
                // Remove line from old content
                String removeLine = line.substring(1);
                if (currentLine < resultLines.size() && Objects.equals(removeLine, resultLines.get(currentLine))) {
                    resultLines.remove(currentLine);
                } else {
                    System.out.println("Warning: Expected to remove line '" + removeLine + "' at " + (currentLine + 1) + ", found '" +
                            (currentLine < resultLines.size() ? resultLines.get(currentLine) : "EOF") + "'");
                }
            } else if (line.startsWith("+")) {
                // Add line to new content
                String newLine = line.substring(1);
                if (currentLine <= resultLines.size()) {
                    resultLines.add(currentLine, newLine);
                } else {
                    resultLines.add(newLine);
                }
                currentLine++;
            }
        }

        // Remove trailing empty lines to match expected content
        while (!resultLines.isEmpty() && resultLines.get(resultLines.size() - 1).isEmpty() && resultLines.size() > 1) {
            resultLines.remove(resultLines.size() - 1);
        }

        return String.join("\n", resultLines);
    }

    private static class FileEntry {
        String filePath;
        String content;

        FileEntry(String filePath, String content) {
            this.filePath = filePath;
            this.content = content;
        }
    }
}