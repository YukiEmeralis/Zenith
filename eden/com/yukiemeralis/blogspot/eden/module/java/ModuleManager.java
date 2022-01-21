package com.yukiemeralis.blogspot.eden.module.java;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import com.yukiemeralis.blogspot.eden.Eden;
import com.yukiemeralis.blogspot.eden.command.CommandManager;
import com.yukiemeralis.blogspot.eden.command.EdenCommand;
import com.yukiemeralis.blogspot.eden.module.EdenModule;
import com.yukiemeralis.blogspot.eden.module.EdenModule.LoadBefore;
import com.yukiemeralis.blogspot.eden.module.EdenModule.ModInfo;
import com.yukiemeralis.blogspot.eden.module.EdenModule.EdenConfig;
import com.yukiemeralis.blogspot.eden.module.event.*;
import com.yukiemeralis.blogspot.eden.module.java.enums.CallerToken;
import com.yukiemeralis.blogspot.eden.module.java.enums.PreventUnload;
import com.yukiemeralis.blogspot.eden.utils.DataUtils;
import com.yukiemeralis.blogspot.eden.utils.FileUtils;
import com.yukiemeralis.blogspot.eden.utils.Option;
import com.yukiemeralis.blogspot.eden.utils.PrintUtils;
import com.yukiemeralis.blogspot.eden.utils.Result;
import com.yukiemeralis.blogspot.eden.utils.PrintUtils.InfoType;
import com.yukiemeralis.blogspot.eden.utils.Result.UndefinedResultException;

/**
 * Handler for all tasks related to Eden's modules.</p>
 * 
 * This is divided up into a few services:</p>
 * - Copying internal modules from Eden's plugin file</p>
 * - Gathering modules from a file, and providing access to their classes post-runtime</p>
 * - Enabling and disabling modules, and registering/unregistering their commands and listeners</p>
 * - Obtaining modules of different statuses in different areas</p>
 */
public class ModuleManager 
{
    private final Map<String, Class<?>> class_cache = new HashMap<>();
	private final Map<String, ModuleClassLoader> loader_cache = new LinkedHashMap<>();

	private final Map<String, String> module_references = new HashMap<>();

	private List<EdenModule> enabled_modules = new ArrayList<>();
	private List<EdenModule> disabled_modules = new ArrayList<>();

	private final static File mods_folder = FileUtils.ensureFolder("./plugins/Eden/mods");

	// ##########################################################
	// #################### Module gathering ####################
	// ##########################################################

	/**
	 * Performs the full startup process.
	 */
	public void performFullLoad()
	{
		PrintUtils.logVerbose("-----[ Preload ]-----", InfoType.INFO);

		PrintUtils.logVerbose("Copying internal modules...", InfoType.INFO);
		copyInternalModules();
		
		PrintUtils.logVerbose("Loading modules and their classes into memory...", InfoType.INFO);
		loadAllModules();

		// Handle missing dependencies
		Iterator<Entry<String, ModuleClassLoader>> loaderEntries = loader_cache.entrySet().iterator();
		while (loaderEntries.hasNext())
		{
			Entry<String, ModuleClassLoader> entry = loaderEntries.next();
			ModuleClassLoader loader = entry.getValue();
			if (!loader.getModuleClass().isAnnotationPresent(LoadBefore.class))
				continue;

			String modname = loader.getModuleClass().getAnnotation(ModInfo.class).modName();
			boolean valid = true;

			for (String str : loader.getModuleClass().getAnnotation(LoadBefore.class).loadBefore())
			{
				if (!loader_cache.containsKey(str))
				{
					PrintUtils.log("(Missing dependency \")[" + str + "](\" for module \"){" + modname + "}(\"!)", InfoType.ERROR);
					valid = false;
					continue;
				}
			}

			if (!valid)
			{
				PrintUtils.logVerbose("Not loading module " + modname + ", as it is missing dependencies.", InfoType.ERROR);
				loaderEntries.remove();
			}
		}

		PrintUtils.logVerbose("Instantiating modules and registering their commands and listeners to their modules...", InfoType.INFO);

		// Finalize by instantiating the modules, commands, and listeners, and registering them to their modules
		for (ModuleClassLoader loader : loader_cache.values())
		{
			try {
				loader.finalizeLoading();
				disabled_modules.add(loader.getModule());
				
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | IOException | NullPointerException e) {
				PrintUtils.printPrettyStacktrace(e);
			}
		}

		// Register all reliant modules
		for (EdenModule module : disabled_modules)
			if (module.getClass().isAnnotationPresent(LoadBefore.class))
				for (String modName : module.getClass().getAnnotation(LoadBefore.class).loadBefore())
				{
					getDisabledModuleByName(modName).addReliantModule(module);
				}
		PrintUtils.logVerbose("Finished preload!", InfoType.INFO);
	}

	private void copyInternalModules()
	{
		if (Eden.getEdenConfig().get("flyingSolo").equals("true"))
		{
			PrintUtils.log("{Eden is now flying solo. Base modules will not be automatically installed.}");
			return;
		}
		
		List<File> internalMods = getInternalModules();

		for (File f : internalMods)
		{
			File modFile = new File("./plugins/Eden/mods/" + f.getName());

			if (!modFile.exists())
			{
				PrintUtils.logVerbose("Missing internal module \"" + modFile.getName() + "\". Copying...", InfoType.WARN);
				FileUtils.copy(f, modFile);
			}
		}

		// Clear temp folder
		File temp = new File("./plugins/Eden/mods/temp/");

		if (temp.listFiles() == null) // Nothing was copied
		{
			temp.delete();
			return;
		}

		for (File f : temp.listFiles())
			f.delete();

		temp.delete();
	}

	private void loadAllModules()
	{
		// Obtain all module classes
		for (File f : mods_folder.listFiles())
		{
			if (!f.getName().endsWith(".jar"))
				continue;

			PrintUtils.logVerbose("Attempting to obtain module from \"" + f.getAbsolutePath() + "\"", InfoType.INFO);
			loadModule(f);
		}

		// Cache all classes in order of dependency
		for (ModuleClassLoader mcl : loader_cache.values())
		{
			precacheModuleClasses(mcl);
		}
	}

	private ModuleClassLoader loadModule(File f)
	{
		ModuleClassLoader mcl;

		try {
			mcl = new ModuleClassLoader(this.getClass().getClassLoader(), this, f);
		} catch (MalformedURLException e) {
			PrintUtils.printPrettyStacktrace(e);
			return null;
		}

		if (mcl.getModuleClass() == null)
		{
			PrintUtils.log("(Failed to load file \")[" + f.getName() + "](\"! Reason: invalid module class.)", InfoType.ERROR);
			return null;
		}

		String modName = mcl.getModuleClass().getAnnotation(ModInfo.class).modName();
		
		module_references.put(modName, f.getAbsolutePath());
		loader_cache.put(modName, mcl);

		return mcl;
	}

	private List<ModuleClassLoader> preloadedModules = new ArrayList<>();
	private void precacheModuleClasses(ModuleClassLoader mcl)
	{
		if (preloadedModules.contains(mcl))
			return;

		if (mcl.getModuleClass().isAnnotationPresent(LoadBefore.class))
		{
			// Go over loadbefores with recursion
			for (String lb : mcl.getModuleClass().getAnnotation(LoadBefore.class).loadBefore())
			{
				if (!loader_cache.containsKey(lb))
				{
					PrintUtils.logVerbose("Missing dependency \"" + lb + "\". Will complain about it later.", InfoType.ERROR);
					continue;
				}

				precacheModuleClasses(loader_cache.get(lb));
			}
		}

		mcl.cacheCommandsAndEvents();
		preloadedModules.add(mcl);
	}

	/**
	 * Copies all internal (core) modules from inside the jar
	 * @return
	 */
	private static List<File> getInternalModules()
	{
		File edenFile = DataUtils.getEdenJar();
		List<File> moduleFiles = new ArrayList<>();

		File modsFolder = new File("./plugins/Eden/mods/");

		try {
			JarFile jarFile = new JarFile(edenFile);
			Enumeration<JarEntry> iter = jarFile.entries();

			JarEntry jarEntry = null;
			List<JarEntry> moduleEntries = new ArrayList<>();

			while (iter.hasMoreElements())
			{
				jarEntry = iter.nextElement();

				if (!jarEntry.getName().startsWith("internalModules") || jarEntry.isDirectory() || !jarEntry.getName().endsWith(".jar"))
					continue;

				if (new File(modsFolder.getAbsolutePath() + "/" + jarEntry.getName().substring(16)).exists())
					continue;

				moduleEntries.add(jarEntry);
			}

			// Create some files from the collected entries
			moduleEntries.forEach(entry -> {
				try {
					File file = FileUtils.inputStreamToFile(jarFile.getInputStream(entry), entry.getName().replace("internalModules/", ""));

					moduleFiles.add(file);
				} catch (IOException e) {
					PrintUtils.printPrettyStacktrace(e);
				}
			});

			jarFile.close();
		} catch (IOException e) {
			PrintUtils.printPrettyStacktrace(e);
		}
		
		return moduleFiles;
	}

	/**
	 * Loads a single module from a filepath.
	 * @param filepath The filepath leading to the module file.
	 * @return A result of either an EdenModule or a String describing the error.
	 */
	public Result<EdenModule, String> loadSingleModule(String filepath)
	{
		Result<EdenModule, String> result = new Result<>(EdenModule.class, String.class);
		
		if (!new File(filepath).exists())
		{
			try { result.err("File does not exist."); } catch (UndefinedResultException e) {}
			return result;
		}

		ModuleClassLoader mcl = loadModule(new File(filepath));

		if (mcl == null)
		{
			try { result.err("File could not be opened."); } catch (UndefinedResultException e) {}
			return result;
		}

		precacheModuleClasses(mcl);

		if (mcl.getModuleClass().isAnnotationPresent(LoadBefore.class))
			if (!isModuleListPresent(mcl.getModuleClass().getAnnotation(LoadBefore.class).loadBefore()))
			{
				try { result.err("Module dependencies are missing."); } catch (UndefinedResultException e) {}
				return result;
			}

		try {
			mcl.finalizeLoading();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | IOException e) {
			PrintUtils.printPrettyStacktrace(e);
			try { result.err("Module has invalid classes. Failed with error: " + e.getClass().getSimpleName()); } catch (UndefinedResultException e_) {}
			return result;
		}

		disabled_modules.add(mcl.getModule());

		// Handle reliance
		if (mcl.getModuleClass().isAnnotationPresent(LoadBefore.class))
		{
			for (String modName : mcl.getModuleClass().getAnnotation(LoadBefore.class).loadBefore())
			{
				loader_cache.get(modName).getModule().addReliantModule(mcl.getModule());
			}
		}

		Eden.getInstance().getServer().getPluginManager().callEvent(new ModuleLoadEvent(mcl.getModule(), mcl.getName()));
		Eden.getInstance().getServer().getPluginManager().callEvent(new ModuleDisableEvent(mcl.getModule(), filepath));

		try { result.ok(mcl.getModule()); } catch (UndefinedResultException e) { }
		return result;
	}
	// ########################################################################
	// #################### Enabling, disabling, unloading ####################
	// ########################################################################

	/**
	 * Attempts to enable all gathered modules.
	 */
	public void enableAllModules()
	{
		PrintUtils.logVerbose("-----[ Enabling modules ]-----", InfoType.INFO);

		// Core loads first, as long as we aren't flying solo
		if (!Eden.getEdenConfig().get("flyingSolo").equals("true"))
			enableModule(getDisabledModuleByName("Eden"));

		// Load other modules
		new ArrayList<>(disabled_modules).forEach(this::enableModule); // Construct a new list to avoid a ConcurrentModificationException
		disabled_modules.removeAll(enabled_modules);

		// Sort enabled modules alphabetically
		Collections.sort(enabled_modules, new Comparator<EdenModule>() {
			@Override
			public int compare(EdenModule mod1, EdenModule mod2) 
			{
				return mod1.getName().compareTo(mod2.getName());
			}
		});

		PrintUtils.log("Enabled [" + enabled_modules.size() + "]/[" + getAllModules().size() + "] module\\(s\\)!", InfoType.INFO);
	}

	/**
	 * Loads a module, calling it's onEnable() and registering its commands and listeners.
	 * @param module The module to load.
	 */
	public void enableModule(EdenModule module)
	{
		if (module == null)
		{
			// Can't load a module that doesn't exist
			PrintUtils.log("(Attempted to load a null module! Aborting...)");
			return;
		}

		if (module.getName() == null)
		{
			PrintUtils.log("(Module failed to load. Keeping as an unloaded module.)", InfoType.ERROR);
			return;
		}

		PrintUtils.logVerbose("Checking if module \"" + module.getName() + "\" has already been loaded...", InfoType.INFO);
		if (enabled_modules.contains(module)) // Don't load twice
		{
			PrintUtils.logVerbose("It has! Skipping...", InfoType.INFO);
			return;
		}

		PrintUtils.logVerbose("Registering event(s)...", InfoType.INFO);
		// Register
		for (Listener l : module.getListeners())
			Eden.getInstance().getServer().getPluginManager().registerEvents(l, Eden.getInstance());

		PrintUtils.logVerbose("Done! Registering command(s)...", InfoType.INFO);

		for (EdenCommand c : module.getCommands())
			CommandManager.registerCommand(c.getName(), c);

		switch (Eden.getNMSVersion())
		{
			case "v1_16_R3":
				((org.bukkit.craftbukkit.v1_16_R3.CraftServer) Bukkit.getServer()).syncCommands();
				break;
			case "v1_17_R1":
				((org.bukkit.craftbukkit.v1_17_R1.CraftServer) Bukkit.getServer()).syncCommands();
				break;
		}

		if (module.getClass().isAnnotationPresent(EdenConfig.class))
		{
			PrintUtils.logVerbose("Found module config", InfoType.INFO);
			module.loadConfig();
		}

		PrintUtils.logVerbose("All registered! Enabling " + module.getName() + " " + module.getVersion() + "...", InfoType.INFO);

		// Enable
		try {
			module.onEnable();
			module.setEnabled();
		} catch (Exception e) {
			PrintUtils.log("(Failed to enable module! Stacktrace is below...)", InfoType.ERROR);

			PrintUtils.printPrettyStacktrace(e);

			if (!disabled_modules.contains(module))
				disabled_modules.add(module);
			return;
		}

		disabled_modules.remove(module);
		enabled_modules.add(module);

		Eden.getInstance().getServer().getPluginManager().callEvent(new ModuleEnableEvent(module, this.module_references.get(module.getName())));
	}

	/**
	 * Disables a module, unregistering its commands and listeners.
	 * @param name The expected name of a module.
	 * @param caller The token of the requestee.
	 * @return Whether or not disabling was successful.
	 */
	public boolean disableModule(String name, CallerToken caller)
	{
		EdenModule module = getEnabledModuleByName(name);

		if (module == null)
			return false; // Can't disable a module that doesn't exist

		// Check for a required caller token
		if (module.getClass().isAnnotationPresent(PreventUnload.class))
		{
			CallerToken intendedCaller = module.getClass().getAnnotation(PreventUnload.class).value();

			if (!CallerToken.isEqualToOrHigher(caller, intendedCaller))
			{
				PrintUtils.log("(An attempt was made to disable \"" + name + "\" but this module's @PreventDisable tag prevented it! Expected token: " + intendedCaller.name() + ", given: " + caller.name() + ")", InfoType.WARN);
				return false;
			}	
		}

		// Start disabling
		PrintUtils.log("Disabling [" + module.getName() + "]", InfoType.INFO);

		List<EdenModule> disabledMods = new ArrayList<>();
		// Disable reliant modules
		if (!CallerToken.isEqualToOrHigher(caller, CallerToken.EDEN))
			for (EdenModule mod : module.getReliantModules())
			{
				PrintUtils.sendMessage("Reliant module: " + mod.getName());
				if (!disableModule(mod.getName(), caller)) // If we can't disable a reliant module, reload all disabled modules and abort
				{
					PrintUtils.log("(Failed to unload module \"" + module.getName() + "\"'s dependencies. Aborting disable.)", InfoType.ERROR);
					disabledMods.forEach(mod_ -> { 
						enableModule(mod_);
						mod_.setEnabled();
					});
				}

				mod.removeReliantModule(module);
				disabledMods.add(mod);
				mod.setDisabled();
			}

		if (module.getClass().isAnnotationPresent(EdenConfig.class))
		{
			try {
				PrintUtils.logVerbose("Saving config...", InfoType.INFO);
				module.saveConfig();
			} catch (Exception e) {
				PrintUtils.log("(Failed to save config! Stacktrace is below...)", InfoType.ERROR);
				PrintUtils.printPrettyStacktrace(e);
			}
		}

		try {
			module.onDisable();

			if (!Eden.isBeingDisabled())
			{
				// Disable events
				module.getListeners().forEach(event -> {
					if (event.getClass().isAnnotationPresent(PreventUnload.class))
						if (!CallerToken.isEqualToOrHigher(caller, event.getClass().getAnnotation(PreventUnload.class).value()))
							return;
							
					HandlerList.unregisterAll(event);
				});
				
				// Disable commands
				module.getCommands().forEach(command -> {
					if (command.getClass().isAnnotationPresent(PreventUnload.class))
						if (!CallerToken.isEqualToOrHigher(caller, command.getClass().getAnnotation(PreventUnload.class).value()))
							return;

					CommandManager.unregisterCommand(command.getName());
				});

				enabled_modules.remove(module);
				disabled_modules.add(module);

				Eden.getInstance().getServer().getPluginManager().callEvent(new ModuleDisableEvent(module, this.module_references.get(module.getName())));

				return true;
			}
		} catch (Exception e) {
			PrintUtils.log("(Failed to disable module! Stacktrace is below...)", InfoType.ERROR);
			PrintUtils.printPrettyStacktrace(e);
		}

		return false;
	}

	/**
	 * Attempts to disable an enabled module. Runs with caller token PLAYER.
	 * @param name The expected name of a module.
	 */
	public void disableModule(String name)
	{
		disableModule(name, CallerToken.PLAYER);
	}

	/**
	 * Removes an EdenModule from memory. Modules being unloaded are expected to already be disabled.
	 * @param name The name of the module to remove.
	 * @param caller The token for the caller.
	 * @see {@link ModuleManager#disableModule(String, CallerToken)}
	 */
	public void removeModuleFromMemory(String name, CallerToken caller)
	{
		EdenModule module = getDisabledModuleByName(name);

		if (module == null)
			return; // Can't unload a module that doesn't exist

		// Check for a required caller token
		if (module.getClass().isAnnotationPresent(PreventUnload.class))
		{
			CallerToken intendedCaller = module.getClass().getAnnotation(PreventUnload.class).value();

			if (!CallerToken.isEqualToOrHigher(caller, intendedCaller))
			{
				PrintUtils.log("(An attempt was made to disable \"" + name + "\" but this module's @PreventDisable tag prevented it! Expected token: " + intendedCaller.name() + ", given: " + caller.name() + ")", InfoType.WARN);
				return;
			}	
		}

		// Disable events
		module.getListeners().forEach(HandlerList::unregisterAll);
		
		// Disable commands
		module.getCommands().forEach(command -> {
			CommandManager.unregisterCommand(command.getName());
		});

		class_cache.clear();

		Eden.getInstance().getServer().getPluginManager().callEvent(new ModuleUnloadEvent(name, module, this.module_references.get(name)));

		disabled_modules.remove(module);
		enabled_modules.remove(module);
		loader_cache.remove(name);
	}

	// #############################################################
	// #################### Getters and setters ####################
	// #############################################################
	
	/**
	 * Obtain a class instance from a loaded module class cache.
	 * @param name The fully-qualified classname to get
	 * @return The class under the given classname
	 */
	public Class<?> getCachedClass(String name)
	{
		Class<?> class_ = class_cache.get(name);
		
		if (class_ != null)
			return class_;

		// Otherwise look to see if it's in the global loader cache
		for (String current : loader_cache.keySet())
		{
			ModuleClassLoader loader = loader_cache.get(current);
			
			try {
				class_ = loader.findClass(name, false);
			} catch (ClassNotFoundException e) {}
			
			if (class_ != null)
			{
				return class_;
			}
		}
		
		return null;
	}

	/**
	 * Finds the module a class is associated with. <p>
	 * <b>Results:</b><p>
	 * SOME - A module was found. Unwrap to obtain module. If null, class was not found anywhere.<p>
	 * NONE - Class was found outside of a module. 
	 * @param clazz The class to search with.
	 */
	public Option<EdenModule> getHostModule(Class<?> clazz) 
	{
		Option<EdenModule> option = new Option<>(EdenModule.class);
		try {
			Class.forName(clazz.getName(), false, this.getClass().getClassLoader()); // Part of Eden, bukkit/spigot, or NMS
			return option; // Option is none
		} catch (ClassNotFoundException e) {}

		for (String current : loader_cache.keySet())
		{
			ModuleClassLoader loader = loader_cache.get(current);

			try {
				loader.findClass(clazz.getName(), false);
				option.some(loader.getModule()); // Class found
				return option;
			} catch (ClassNotFoundException e) {
				continue;
			}
		}

		option.some(null); // Class was not found anywhere
		return option;
	}

	/**
	 * Assigns a class to a name set in the class cache.
	 * @param name The fully-qualified name of the given class.
	 * @param class_ The class to assign.
	 */
	public void setClass(String name, Class<?> class_)
	{
		if (!class_cache.containsKey(name))
			class_cache.put(name, class_);
	}

	/**
	 * Checks if this module manager holds a reference (module name + module filepath) for a module.
	 * @param name The expected name of a module reference.
	 * @return Whether or not this module manager holds a reference to a module.
	 */
	public boolean hasReferenceTo(String name)
	{
		return module_references.containsKey(name);
	}

	/**
	 * Obtains a map of all module references.
	 * @return A map of all module references.
	 */
	public Map<String, String> getReferences()
	{
		return module_references;
	}

	/**
	 * Obtains a list of all modules.
	 * @return A map of all modules.
	 */
	public List<EdenModule> getAllModules()
	{
		return DataUtils.concatMultipleCollections(enabled_modules, disabled_modules);
	}

	/**
	 * Obtains a list of all enabled modules.
	 * @return A list of enabled modules.
	 */
	public List<EdenModule> getEnabledModules()
	{
		return enabled_modules;
	}

	/**
	 * Obtains a list of disabled modules.
	 * @return A list of disabled modules.
	 */
	public List<EdenModule> getDisabledModules()
	{
		return disabled_modules;
	}

	/**
	 * Checks if a module with a given name is enabled.
	 * @param name The expected module name.
	 * @return Whether or not a module with a given name is enabled or not. Returns false if the module cannot be found.
	 */
	public boolean isModulePresent(String name)
	{
		for (EdenModule mod : getAllModules())
		{
			if (mod.getName().equals(name))
				return true;
		}

		return false;
	}

	/**
	 * Checks if an array of modules, by name, are all present. Returns false if one or more modules are missing.
	 * @param names An array of expected module names.
	 * @return Whether or not a set of modules are enabled.
	 * @see {@link ModuleManager#isModulePresent(String)}
	 */
	public boolean isModuleListPresent(String... names)
	{
		for (String name : names)
		{
			if (!isModulePresent(name))
				return false;
		}

		return true;
	}

	/**
	 * Obtains an enabled module whose name matches the given name.
	 * @param name The name of a module.
	 * @return A module matching the given name. Returns null if no module matches the given name.
	 * @see {@link ModuleManager#getEnabledModules()}
	 */
	public EdenModule getEnabledModuleByName(String name)
	{
		for (EdenModule mod : enabled_modules)
		{
			if (mod.getName().equals(name))
				return mod;
		}

		return null;
	}

	/**
	 * Obtains a disabled module whose name matches the given name.
	 * @param name The name of a module.
	 * @return A module matching the given name. Returns null if no module matches the given name.
	 * @see {@link ModuleManager#getDisabledModules()}
	 */
	public EdenModule getDisabledModuleByName(String name)
	{
		for (EdenModule mod : disabled_modules)
		{
			if (mod.getName().equals(name))
				return mod;
		}

		return null;
	}

	/**
	 * Obtains a module by its name.
	 * @param name The name of a module.
	 * @return A module matching the given name. Returns null if no module matches the given name.
	 */
	public EdenModule getModuleByName(String name)
	{
		EdenModule mod = getEnabledModuleByName(name);

		if (mod != null)
			return mod;

		mod = getDisabledModuleByName(name);

		if (mod != null)
			return mod;
		return null;
	}
}