package com.picoedge.ai_tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class LogPaneToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        LogPane logPane = new LogPane(project);
        System.out.println("[LogPaneToolWindowFactory] Created LogPane instance for project: " + project.getName());
        try {
            Content content = ContentFactory.getInstance().createContent(logPane.getContent(), "", false);
            System.out.println("[LogPaneToolWindowFactory] Created content for tool window: " + toolWindow.getId());
            toolWindow.getContentManager().addContent(content);
            System.out.println("[LogPaneToolWindowFactory] Added content to tool window: " + toolWindow.getId());
        } catch (Exception e) {
            System.err.println("[LogPaneToolWindowFactory] Failed to create tool window content: " + e.getMessage());
            e.printStackTrace();
        }
    }
}