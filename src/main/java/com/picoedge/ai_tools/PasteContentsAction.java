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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PasteContentsAction extends AnAction {
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "####\\s*(.+?)\\s*\\n```(?:\\w+)?\\n([\\s\\S]*?)\\n```\\s*(?:\\n|$)",
            Pattern.MULTILINE
    );
    private static final Pattern TREE_PATTERN = Pattern.compile(
            "```\\n(.*?)\\n```\\n\\n",
            Pattern.DOTALL
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
            Messages.showMessageDialog(project, "No valid files found in clipboard content.", "Error", Messages.getErrorIcon());
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
                    createOrUpdateFile(psiDirectory, relativePath, fileEntry.content);
                } catch (IOException ex) {
                    errors.add("Failed to create file: " + relativePath + " (" + ex.getMessage() + ")");
                }
            }
        });

        if (errors.isEmpty()) {
            Messages.showMessageDialog(project, "Files have been created/updated successfully.", "Success", Messages.getInformationIcon());
        } else {
            Messages.showMessageDialog(project, "Some files could not be created:\n" + String.join("\n", errors), "Error", Messages.getErrorIcon());
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
            String fileContent = matcher.group(2);
            if (!StringUtils.isBlank(filePath)) {
                files.add(new FileEntry(filePath, fileContent));
            }
        }
        return files;
    }

    private String remapPath(String originalPath, String sourceRoot) {
        System.out.println("remapPath inputs: original=" + originalPath + ", sourceRoot=" + sourceRoot);

        if (StringUtils.isBlank(originalPath)) {
            System.out.println("remapPath output: '' (blank originalPath)");
            return "";
        }

        // Normalize paths to remove leading/trailing slashes
        String normalizedSourceRoot = StringUtils.isBlank(sourceRoot) ? "" : sourceRoot.replaceAll("^/+|/+$", "");
        String normalizedOriginalPath = originalPath.replaceAll("^/+|/+$", "");

        // Strip sourceRoot from originalPath to get the relative path
        String relativePath = normalizedOriginalPath;
        if (!normalizedSourceRoot.isEmpty() && normalizedOriginalPath.startsWith(normalizedSourceRoot + "/")) {
            relativePath = normalizedOriginalPath.substring(normalizedSourceRoot.length() + 1);
        }

        // Return the relative path for non-root pasting
        if (StringUtils.isBlank(relativePath)) {
            System.out.println("remapPath output: '' (empty relativePath)");
            return "";
        }

        System.out.println("remapPath output: " + relativePath);
        return relativePath.replaceAll("/+", "/");
    }

    private void createOrUpdateFile(PsiDirectory baseDirectory, String relativePath, String content) throws IOException {
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
                continue; // Skip empty segments
            }
            VirtualFile subDir = currentDir.findChild(subDirName);
            if (subDir == null) {
                subDir = currentDir.createChildDirectory(this, subDirName);
            }
            currentDir = subDir;
        }

        String fileName = pathParts[pathParts.length - 1];
        VirtualFile existingFile = currentDir.findChild(fileName);

        // Handle existing file by renaming
        if (existingFile != null) {
            renameExistingFile(currentDir, fileName, baseDirectory.getProject());
        }

        // Create new file
        VirtualFile newFile = currentDir.createChildData(this, fileName);
        newFile.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));

        // Create .latest file
        VirtualFile latestFile = currentDir.findChild(fileName + ".latest");
        if (latestFile != null) {
            renameExistingFile(currentDir, fileName + ".latest", baseDirectory.getProject());
        }
        latestFile = currentDir.createChildData(this, fileName + ".latest");
        latestFile.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
    }

    private void renameExistingFile(VirtualFile directory, String fileName, Project project) {
        int suffix = 0;
        String newName;
        do {
            newName = String.format("%s.%03d.old", fileName, suffix);
            suffix++;
        } while (directory.findChild(newName) != null);

        VirtualFile file = directory.findChild(fileName);
        if (file != null) {
            String finalNewName = newName;
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    file.rename(null, finalNewName);
                } catch (IOException ex) {
                    Messages.showMessageDialog(project, "Failed to rename file: " + fileName, "Error", Messages.getErrorIcon());
                }
            });
        }
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