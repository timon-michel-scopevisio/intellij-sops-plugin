package com.github.daputzy.intellijsopsplugin.sops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.github.daputzy.intellijsopsplugin.settings.SettingsState;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ExecutionUtil {

	@Getter(lazy = true)
	private static final ExecutionUtil instance = new ExecutionUtil();

	/**
	 * decrypts given file
	 *
	 * @param project        project
	 * @param file           file
	 * @param successHandler called on success with decrypted content
	 */
	public void decrypt(final Project project, VirtualFile file, final Consumer<String> successHandler) {
		final GeneralCommandLine command = buildCommand(file.getParent().getPath());

		command.addParameter("-d");
		command.addParameter(file.getName());

		run(
			command,
			new ErrorNotificationProcessListener(project),
			new ProcessAdapter() {
				private final StringBuffer stdout = new StringBuffer();

				@Override
				public void processTerminated(@NotNull final ProcessEvent event) {
					if (event.getExitCode() == 0) {
						successHandler.accept(stdout.toString());
					}
				}

				@Override
				public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final Key outputType) {
					if (ProcessOutputType.isStdout(outputType) && event.getText() != null) {
						stdout.append(event.getText());
					}
				}
			}
		);
	}

	/**
	 * encrypts given file
	 *
	 * @param project        project
	 * @param file           file
	 * @param successHandler called on success
	 * @param failureHandler called on failure
	 */
	public void encrypt(
		final Project project,
		final VirtualFile file,
		final Runnable successHandler,
		final Runnable failureHandler
	) {
		final GeneralCommandLine command = buildCommand(file.getParent().getPath());

		command.addParameter("-e");
		command.addParameter("-i");
		command.addParameter(file.getName());

		run(
			command,
			new ErrorNotificationProcessListener(project),
			new ProcessAdapter() {
				@Override
				public void processTerminated(@NotNull final ProcessEvent event) {
					if (event.getExitCode() == 0) {
						successHandler.run();
					} else {
						failureHandler.run();
					}
				}
			}
		);
	}

	/**
	 * edits encrypted file with given content
	 *
	 * @param project        project
	 * @param file           file
	 * @param newContent     new content
	 * @param successHandler called on success
	 * @param failureHandler called on failure
	 */
	@SneakyThrows(IOException.class)
	public void edit(
		final Project project,
		final VirtualFile file,
		final String newContent,
		final Runnable successHandler,
		final Runnable failureHandler
	) {
		// create script temp file
		final String scriptSuffix = SystemUtils.IS_OS_WINDOWS ? ".cmd" : ".sh";
		final Path scriptFile = Files.createTempFile("sops-editor-script", scriptSuffix);

		// make sure temp file is cleaned on application exit
		FileUtils.forceDeleteOnExit(scriptFile.toFile());

		// make sure script is executable
		if (!scriptFile.toFile().setExecutable(true)) {
			throw new IllegalStateException("Could not make script file executable");
		}

		final String startIdentifier = "simple-sops-edit-ready" + RandomStringUtils.randomAlphanumeric(32);

		final List<String> scriptFileContent = SystemUtils.IS_OS_WINDOWS ?
			List.of("@powershell.exe -NoProfile -Command \"Write-Output '" + startIdentifier + "'; $stdin = [System.Console]::In; $inputContent = $stdin.ReadToEnd(); $inputContent | Out-File \\\"%1\\\"\"") :
			List.of(
				"#!/usr/bin/env sh",
				"set -eu",
				"printf '%s'".formatted(startIdentifier),
				"cat - > \"$1\""
			);

		Files.write(scriptFile, scriptFileContent, file.getCharset(), StandardOpenOption.APPEND);

		final GeneralCommandLine command = buildCommand(file.getParent().getPath());

		// escape twice for windows because of ENV variable parsing
		final String editorPath = scriptFile.toAbsolutePath().toString().replace("\\", "\\\\");

		command.withEnvironment("EDITOR", editorPath);
		command.addParameter(file.getName());

		run(
			command,
			new ErrorNotificationProcessListener(project),
			new ProcessAdapter() {
				private final AtomicBoolean failed = new AtomicBoolean(false);

				@Override
				public void processTerminated(@NotNull final ProcessEvent event) {
					// clean up the temp file
					FileUtils.deleteQuietly(scriptFile.toFile());

					if (event.getExitCode() == 0 && !failed.get()) {
						successHandler.run();
					} else {
						failureHandler.run();
					}
				}

				@Override
				@SneakyThrows(IOException.class)
				public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final Key outputType) {
					if (null != event.getText() && startIdentifier.equals(event.getText().trim())) {
						IOUtils.write(newContent, event.getProcessHandler().getProcessInput(), file.getCharset());
						event.getProcessHandler().getProcessInput().close();
					}

					if (ProcessOutputType.isStderr(outputType)) {
						failed.set(true);
						event.getProcessHandler().destroyProcess();
					}
				}
			}
		);
	}

	private void run(final GeneralCommandLine command, final ProcessListener... listener) {
		final OSProcessHandler processHandler;
		try {
			processHandler = new OSProcessHandler(command);
		} catch (final ExecutionException e) {
			throw new RuntimeException("Could not execute sops command", e);
		}

		Arrays.stream(listener).forEach(processHandler::addProcessListener);

		processHandler.startNotify();
	}

	private GeneralCommandLine buildCommand(final String cwd) {
		final GeneralCommandLine command = new GeneralCommandLine(SettingsState.getInstance().sopsExecutable)
			.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
			.withCharset(StandardCharsets.UTF_8)
			.withWorkDirectory(cwd);

		final String[] environmentString = SettingsState.getInstance().sopsEnvironment.split("\\s(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

		final List<String> environmentList = Arrays.stream(environmentString)
			.map(String::trim)
			.filter(Predicate.not(String::isBlank))
			.toList();

		command.withEnvironment(
			EnvironmentUtil.parseEnv(environmentList.toArray(String[]::new))
		);

		return command;
	}
}
