package org.cishell.reference.app.service.fileloader;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;

import org.cishell.app.service.fileloader.FileLoadException;
import org.cishell.app.service.fileloader.FileLoadListener;
import org.cishell.app.service.fileloader.FileLoaderService;
import org.cishell.framework.CIShellContext;
import org.cishell.framework.algorithm.AlgorithmExecutionException;
import org.cishell.framework.algorithm.AlgorithmFactory;
import org.cishell.framework.algorithm.ProgressMonitor;
import org.cishell.framework.data.Data;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

public class FileLoaderServiceImpl implements FileLoaderService, ManagedService {
	public static final String LOAD_DIRECTORY_PREFERENCE_KEY = "loadDir";
	public static String defaultLoadDirectory = "";

	private Dictionary preferences = new Hashtable();
	private Collection<FileLoadListener> listeners = new HashSet<FileLoadListener>();

	public void registerListener(FileLoadListener listener) {
		this.listeners.add(listener);
	}

	public void unregisterListener(FileLoadListener listener) {
		if (this.listeners.contains(listener)) {
			this.listeners.remove(listener);
		}
	}

	public File[] getFilesToLoadFromUser(boolean selectSingleFile, String[] filterExtensions)
			throws FileLoadException {
		IWorkbenchWindow window = getFirstWorkbenchWindow();
		Display display = PlatformUI.getWorkbench().getDisplay();

		return getFilesToLoadFromUserInternal(window, display, selectSingleFile, filterExtensions);
	}

	public Data[] loadFilesFromUserSelection(
			BundleContext bundleContext,
			CIShellContext ciShellContext,
			LogService logger,
			ProgressMonitor progressMonitor,
			boolean selectSingleFile) throws FileLoadException {
		if ("".equals(defaultLoadDirectory)) {
			defaultLoadDirectory = determineDefaultLoadDirectory();
		}

		File[] files = getFilesToLoadFromUser(selectSingleFile, null);

		if (files != null) {
			return loadFiles(bundleContext, ciShellContext, logger, progressMonitor, files);
		} else {
			return null;
		}
	}

	public Data[] loadFiles(
			BundleContext bundleContext,
			CIShellContext ciShellContext,
			LogService logger,
			ProgressMonitor progressMonitor,
			File[] files) throws FileLoadException {
		Data[] loadedFileData =
			loadFilesInternal(bundleContext, ciShellContext, logger, progressMonitor, files);

		for (File file : files) {
			for (FileLoadListener listener : this.listeners) {
				listener.fileLoaded(file);
			}
		}

		return loadedFileData;
	}

	public Data[] loadFile(
			BundleContext bundleContext,
			CIShellContext ciShellContext,
			LogService logger,
			ProgressMonitor progressMonitor,
			File file) throws FileLoadException {
		return loadFiles(
			bundleContext, ciShellContext, logger, progressMonitor, new File[] { file });
	}

	public Data[] loadFileOfType(
			BundleContext bundleContext,
			CIShellContext ciShellContext,
			LogService logger,
			ProgressMonitor progressMonitor,
			File file,
			String fileExtension,
			String mimeType) throws FileLoadException {
		try {
			String format =
				"(& " +
					"(type=validator)" +
					"(| (in_data=file-ext:%1$s) (also_validates=%1$s))" +
					"(out_data=%2$s))";
			String validatorsQuery = String.format(format, fileExtension, mimeType);
			ServiceReference[] supportingValidators = bundleContext.getAllServiceReferences(
				AlgorithmFactory.class.getName(), validatorsQuery);
			
			if (supportingValidators == null) {
				throw new FileLoadException(String.format(
					"The file %s cannot be loaded as type %s.", file.getName(), mimeType));
			} else {
				AlgorithmFactory validator =
					(AlgorithmFactory) bundleContext.getService(supportingValidators[0]);

				return loadFileOfType(
					bundleContext, ciShellContext, logger, progressMonitor, file, validator);
			}
		} catch (InvalidSyntaxException e) {
			e.printStackTrace();

			throw new FileLoadException(e.getMessage(), e);
		}
	}

	public Data[] loadFileOfType(
			BundleContext bundleContext,
			CIShellContext ciShellContext,
			LogService logger,
			ProgressMonitor progressMonitor,
			File file,
			AlgorithmFactory validator) throws FileLoadException {
		try {
			Data[] loadedFileData = loadFileInternal(
				bundleContext, ciShellContext, logger, progressMonitor, file, validator);

			for (FileLoadListener listener : this.listeners) {
				listener.fileLoaded(file);
			}

			return loadedFileData;
		} catch (AlgorithmExecutionException e) {
			throw new FileLoadException(e.getMessage(), e);
		}
	}

	public void updated(Dictionary preferences) throws ConfigurationException {
		if (preferences != null) {
			this.preferences = preferences;
		}
	}

	private String determineDefaultLoadDirectory() {
		if (this.preferences != null) {
			Object directoryPreference = preferences.get(LOAD_DIRECTORY_PREFERENCE_KEY);

			if (directoryPreference != null) {
				return directoryPreference.toString();
			} else {
				return "";
			}
		} else {
			return "";
		}
	}

	private Data[] loadFilesInternal(
			BundleContext bundleContext,
			CIShellContext ciShellContext,
			LogService logger,
			ProgressMonitor progressMonitor,
			File[] files) throws FileLoadException {
		IWorkbenchWindow window = getFirstWorkbenchWindow();
		Display display = PlatformUI.getWorkbench().getDisplay();

		if ((files != null) && (files.length != 0)) {
			Collection<Data> finalLabeledFileData = new ArrayList<Data>();

			for (File file : files) {
				try {
					AlgorithmFactory validator =
						getValidatorFromUser(bundleContext, window, display, file);

//					Data[] validatedFileData = validateFile(
//						bundleContext,
//						ciShellContext,
//						logger,
//						progressMonitor,
//						window,
//						display,
//						file,
//						validator);
//					Data[] labeledFileData = labelFileData(file, validatedFileData);
					Data[] labeledFileData = loadFileInternal(
						bundleContext,
						ciShellContext,
						logger,
						progressMonitor,
						file,
						validator);

					for (Data data : labeledFileData) {
						finalLabeledFileData.add(data);
					}
				} catch (Throwable e) {
					String format =
						"The chosen file is not compatible with this format.  " +
						"Check that your file is correctly formatted or try another validator.  " +
						"The reason is: %s";
					String logMessage = String.format(format, e.getMessage());
					logger.log(LogService.LOG_ERROR, logMessage, e);
				}
			}

			return finalLabeledFileData.toArray(new Data[0]);
		} else {
			return null;
		}
	}

	private Data[] loadFileInternal(
			BundleContext bundleContext,
			CIShellContext ciShellContext,
			LogService logger,
			ProgressMonitor progressMonitor,
			File file,
			AlgorithmFactory validator) throws AlgorithmExecutionException, FileLoadException {
		IWorkbenchWindow window = getFirstWorkbenchWindow();
		Display display = PlatformUI.getWorkbench().getDisplay();
		Data[] validatedFileData = validateFile(
			bundleContext,
			ciShellContext,
			logger,
			progressMonitor,
			window,
			display,
			file,
			validator);
		Data[] labeledFileData = labelFileData(file, validatedFileData);

		return labeledFileData;
	}

	private IWorkbenchWindow getFirstWorkbenchWindow() throws FileLoadException {
		final IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();

		if (windows.length == 0) {
			throw new FileLoadException(
				"Cannot obtain workbench window needed to open dialog.");
		} else {
			return windows[0];
		}
	}

	private File[] getFilesToLoadFromUserInternal(
			IWorkbenchWindow window,
			Display display,
			boolean selectSingleFile,
			String[] filterExtensions) {
		FileSelectorRunnable fileSelector =
			new FileSelectorRunnable(window, selectSingleFile, filterExtensions);

		if (Thread.currentThread() != display.getThread()) {
			display.syncExec(fileSelector);
		} else {
			fileSelector.run();
		}

		return fileSelector.getFiles();
	}

	private Data[] validateFile(
			BundleContext bundleContext,
			CIShellContext ciShellContext,
			LogService logger,
			ProgressMonitor progressMonitor,
			IWorkbenchWindow window,
			Display display,
			File file,
			AlgorithmFactory validator) throws AlgorithmExecutionException {
		if ((file == null) || (validator == null)) {
			String logMessage = "File loading canceled";
			logger.log(LogService.LOG_WARNING, logMessage);
		} else {
			try {
				System.err.println("file: " + file);
				System.err.println("validator: " + validator);
				System.err.println("progressMonitor: " + progressMonitor);
				System.err.println("ciShellContext: " + ciShellContext);
				System.err.println("logger: " + logger);
				return FileValidator.validateFile(
					file, validator, progressMonitor, ciShellContext, logger);
			} catch (AlgorithmExecutionException e) {
				if ((e.getCause() != null)
						&& (e.getCause() instanceof UnsupportedEncodingException)) {
					String format =
						"This file cannot be loaded; it uses the unsupported character " +
						"encoding %s.";
					String logMessage = String.format(format, e.getCause().getMessage());
					logger.log(LogService.LOG_ERROR, logMessage);
				} else {						
					throw e;
				}
			}
		}

		return new Data[0];
	}

	private Data[] labelFileData(File file, Data[] validatedFileData) {
		Data[] labeledFileData =
			PrettyLabeler.relabelWithFileNameHierarchy(validatedFileData, file);

		return labeledFileData;
	}

	private AlgorithmFactory getValidatorFromUser(
			BundleContext bundleContext, IWorkbenchWindow window, Display display, File file) {
		ValidatorSelectorRunnable validatorSelector =
			new ValidatorSelectorRunnable(window, bundleContext, file);

		if (Thread.currentThread() != display.getThread()) {
			display.syncExec(validatorSelector);
		} else {
			validatorSelector.run();
		}

		return validatorSelector.getValidator();
	}
}