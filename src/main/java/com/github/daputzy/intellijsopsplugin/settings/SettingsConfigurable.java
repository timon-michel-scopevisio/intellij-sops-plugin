package com.github.daputzy.intellijsopsplugin.settings;

import java.util.Objects;

import com.intellij.openapi.options.Configurable;
import javax.swing.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class SettingsConfigurable implements Configurable {

	private SettingsComponent settingsComponent;

	@Override
	@Nls(capitalization = Nls.Capitalization.Title)
	public String getDisplayName() {
		return "Simple Sops Settings";
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return settingsComponent.getPreferredFocusedComponent();
	}

	@Override
	public @Nullable JComponent createComponent() {
		settingsComponent = new SettingsComponent();

		return settingsComponent.getPanel();
	}

	@Override
	public boolean isModified() {
		final SettingsState settings = SettingsState.getInstance();

		return !Objects.equals(settings.sopsEnvironment, settingsComponent.getSopsEnvironment()) ||
			!Objects.equals(settings.sopsExecutable, settingsComponent.getSopsExecutable()) ||
			!Objects.equals(settings.sopsUseWSL, settingsComponent.getSopsUseWSL()) ||
			!Objects.equals(settings.sopsWslDistributionName, settingsComponent.getSopsWslDistributionName()) ||
			!Objects.equals(settings.sopsFilesReadOnly, settingsComponent.getSopsFilesReadOnly());
	}

	@Override
	public void apply() {
		final SettingsState settings = SettingsState.getInstance();

		settings.sopsEnvironment = settingsComponent.getSopsEnvironment();
		settings.sopsExecutable = settingsComponent.getSopsExecutable();
		settings.sopsUseWSL = settingsComponent.getSopsUseWSL();
		settings.sopsWslDistributionName = settingsComponent.getSopsWslDistributionName();
		settings.sopsFilesReadOnly = settingsComponent.getSopsFilesReadOnly();
	}

	@Override
	public void reset() {
		final SettingsState settings = SettingsState.getInstance();

		settingsComponent.setSopsEnvironment(settings.sopsEnvironment);
		settingsComponent.setSopsExecutable(settings.sopsExecutable);
		settingsComponent.setSopsUseWSL(settings.sopsUseWSL);
		settingsComponent.setSopsWslDistributionName(settings.sopsWslDistributionName);
		settingsComponent.setSopsFilesReadOnly(settings.sopsFilesReadOnly);
	}

	@Override
	public void disposeUIResources() {
		settingsComponent = null;
	}
}
