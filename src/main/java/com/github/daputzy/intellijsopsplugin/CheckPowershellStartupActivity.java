package com.github.daputzy.intellijsopsplugin;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.notification.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

public class CheckPowershellStartupActivity implements ProjectActivity {

    private static final String PWSH_NOT_INSTALLED_ERROR =
        """
        <p>PowerShell >= 7 is required for this plugin.</p>
        <p>Please follow the installation instructions:</p>
        """;

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        if (!SystemUtils.IS_OS_WINDOWS) return null;

        final GeneralCommandLine command = new GeneralCommandLine("pwsh.exe")
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withCharset(StandardCharsets.UTF_8)
            .withParameters("-Version");

        final OSProcessHandler processHandler;
        try {
            processHandler = new OSProcessHandler(command);
            processHandler.startNotify();
        } catch (final ProcessNotCreatedException e) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("com.github.daputzy.intellijsopsplugin")
                .createNotification("Sops plugin ", PWSH_NOT_INSTALLED_ERROR, NotificationType.WARNING)
                    .addAction(new BrowseNotificationAction(
                        "Official installation instructions",
                        "https://learn.microsoft.com/de-de/powershell/scripting/install/installing-powershell-on-windows?view=powershell-7.4"
                    ))
                .setImportant(true)
                .notify(project);
        } catch (final ExecutionException e) {
            throw new IllegalStateException("Could not check if powershell is installed", e);
        }

        return null;
    }
}

