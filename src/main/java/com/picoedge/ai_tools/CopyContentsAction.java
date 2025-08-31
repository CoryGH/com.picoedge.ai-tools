package com.picoedge.ai_tools;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class CopyContentsAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (project == null || files == null || files.length == 0) {
            CopyPasteManager.getInstance().setContents(new StringSelection(""));
            return;
        }

        String basePath = project.getBasePath();
        if (basePath == null) {
            basePath = "";
        }
        final String finalBasePath = basePath;

        // Load or initialize .env file
        Properties envProps = new Properties();
        VirtualFile envFileForRead = null;
        VirtualFile projectDir = null;
        try {
            projectDir = VfsUtil.findFileByIoFile(new java.io.File(finalBasePath), true);
            if (projectDir != null) {
                envFileForRead = projectDir.findChild(".env");
                if (envFileForRead != null && envFileForRead.exists()) {
                    envProps.load(new java.io.StringReader(new String(envFileForRead.contentsToByteArray(), StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException ex) {
            // .env file not found or invalid
        }

        // Check if .env needs initialization or update
        boolean hasPluginVars = envProps.stringPropertyNames().stream().anyMatch(name -> name.startsWith("COM_PICOEDGE_AI_TOOLS_"));
        final VirtualFile finalEnvFile = envFileForRead;
        final VirtualFile finalProjectDir = projectDir;
        if (finalProjectDir != null && (finalEnvFile == null || (!hasPluginVars && !envProps.containsKey("COM_PICOEDGE_AI_TOOLS_TOUCHED")))) {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    VirtualFile envFileToWrite = finalEnvFile;
                    if (envFileToWrite == null) {
                        // Create new .env file
                        envFileToWrite = finalProjectDir.createChildData(this, ".env");
                        envFileToWrite.setBinaryContent(String.join("\n", FileProcessor.DEFAULT_ENV).getBytes(StandardCharsets.UTF_8));
                    } else {
                        // Append defaults to existing .env
                        String existingContent = new String(envFileToWrite.contentsToByteArray(), StandardCharsets.UTF_8);
                        String newContent = existingContent + (existingContent.endsWith("\n") ? "" : "\n") + String.join("\n", FileProcessor.DEFAULT_ENV) + "\n";
                        envFileToWrite.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException ex) {
                    // Fail silently, proceed with defaults
                }
            });
            // Reload properties after update
            try {
                VirtualFile updatedEnvFile = finalProjectDir.findChild(".env");
                if (updatedEnvFile != null && updatedEnvFile.exists()) {
                    envProps.clear();
                    envProps.load(new java.io.StringReader(new String(updatedEnvFile.contentsToByteArray(), StandardCharsets.UTF_8)));
                }
            } catch (IOException ex) {
                // Use defaults
            }
        }

        FileProcessor processor = new FileProcessor(finalBasePath, envProps);
        String result = processor.processFiles(files);
        String output = processor.getPrefixText() + (result.isEmpty() ? "" : result) + processor.getSuffixText();

        CopyPasteManager.getInstance().setContents(new StringSelection(output));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        e.getPresentation().setEnabledAndVisible(files != null && files.length > 0);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}