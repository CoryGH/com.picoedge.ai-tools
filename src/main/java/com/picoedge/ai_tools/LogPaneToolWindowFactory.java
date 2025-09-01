package com.picoedge.ai_tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Properties;

// Factory class to create the logger tool window in IntelliJ-based IDEs
public class LogPaneToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Create the LogPane UI component
        LogPane logPane = new LogPane(project);
        // Create content for the tool window
        Content content = ContentFactory.getInstance().createContent(logPane.getContent(), "", false);
        // Add content to the tool window
        toolWindow.getContentManager().addContent(content);
    }
}