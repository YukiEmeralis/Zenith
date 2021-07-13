package com.yukiemeralis.blogspot.zenith.module.java;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.bukkit.event.Listener;

import com.yukiemeralis.blogspot.zenith.command.ZenithCommand;
import com.yukiemeralis.blogspot.zenith.module.ZenithModule;
import com.yukiemeralis.blogspot.zenith.module.ZenithModule.ModInfo;
import com.yukiemeralis.blogspot.zenith.module.java.annotations.HideFromCollector;
import com.yukiemeralis.blogspot.zenith.module.java.annotations.Unimplemented;
import com.yukiemeralis.blogspot.zenith.utils.DataUtils;
import com.yukiemeralis.blogspot.zenith.utils.PrintUtils;
import com.yukiemeralis.blogspot.zenith.utils.PrintUtils.InfoType;

@SuppressWarnings("unused")
public class ModuleClassLoader extends URLClassLoader
{
    private File file;
	private Class<? extends ZenithModule> moduleClass;
	private ZenithModule module;
    private ModuleManager loader;
	
	private final Map<String, Class<?>> interior_classes = new HashMap<>();
	private final Map<String, Class<?>> unimplemented_classes = new HashMap<>();

	private final List<String> dependency_entries = new ArrayList<>();

	private JarFile jar = null;

	// Loading buffers
	private final List<Class<? extends ZenithCommand>> commandClasses = new ArrayList<>();
	private final List<Class<? extends Listener>> listenerClasses = new ArrayList<>();
	
	public ModuleClassLoader(ClassLoader parent, ModuleManager loader, File file) throws MalformedURLException, NullPointerException
	{
		super(new URL[] { file.toURI().toURL() }, parent);
		
		this.loader = loader;
		this.file = file;

		try {
			jar = new JarFile(file);
		} catch (IOException e) {
			throw new NullPointerException("Failed to access module file.");
		}
	}

	@SuppressWarnings("unchecked")
	private Class<? extends ZenithModule> locateModuleClass()
	{
		Class<? extends ZenithModule> clazz = null;

		Enumeration<JarEntry> entries = jar.entries();
		JarEntry entry;
		while (entries.hasMoreElements())
		{
			entry = entries.nextElement();

			if (!entry.getName().endsWith(".class") || entry.isDirectory())
				continue;

			try {
				Class<?> buffer = Class.forName(entry.getName().replace("/", ".").replace(".class", ""), false, this);
				
				if (!ZenithModule.class.isAssignableFrom(buffer))
					continue;

				if (!buffer.isAnnotationPresent(ModInfo.class))
				{
					PrintUtils.log("Module class \"" + clazz.getPackageName() + "\" does not specify any module information!", InfoType.ERROR);
					PrintUtils.log("If you are a developer seeing this message, please attach an @ModInfo annotation to your module class.", InfoType.ERROR);
					PrintUtils.log("If you are a server owner seeing this message, please either update this module, update Zenith, or contact this module's maintainer.", InfoType.ERROR);
					PrintUtils.log("Offending file: " + file.getName(), InfoType.ERROR);
					return null;
				}

				clazz = (Class<? extends ZenithModule>) buffer;
			} catch (ClassNotFoundException | NoClassDefFoundError e) { continue; }
		}

		if (clazz != null) 
		{
			this.moduleClass = clazz;
			interior_classes.put(clazz.getPackageName(), clazz);
		}

		return clazz;
	}

	void cacheCommandsAndEvents()
	{
		commandClasses.addAll(findSubclasses(true, ZenithCommand.class));
		listenerClasses.addAll(findSubclasses(true, Listener.class));
	}

	void finalizeLoading() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, IOException
	{
		// Put it all together
		module = moduleClass.getConstructor().newInstance();
		module.setInfo(moduleClass.getAnnotation(ModInfo.class));
		
		List<ZenithCommand> commands = new ArrayList<>();
		List<Listener> listeners = new ArrayList<>();

		for (Class<? extends ZenithCommand> clazz : commandClasses)
		{
			Constructor<? extends ZenithCommand> commandConstructor;
			ZenithCommand command;

			try {
				commandConstructor = clazz.getConstructor();
				command = commandConstructor.newInstance();
			} catch (NoSuchMethodException e) {
				try {
					commandConstructor = clazz.getConstructor(ZenithModule.class);
					command = commandConstructor.newInstance(module);
				} catch (NoSuchMethodException e_) {
					PrintUtils.log("Command class \"" + clazz.getPackageName() + "\" does not contain a valid constructor!", InfoType.ERROR);
					PrintUtils.log("If you are a developer seeing this message, ensure your class contains a no-arg constructor, or one with a ZenithModule.", InfoType.ERROR);
					PrintUtils.log("Alternatively, you may hide this command from automatic loading by annotating your class with an @HideFromCollector and adding it manually.", InfoType.ERROR);
					PrintUtils.log("If you are a server owner seeing this message, please update this module \"" + module.getName() + "\", update Zenith, or contact this module's maintainer.", InfoType.ERROR);
					continue;
				}
			}

			commands.add(command);
		}

		for (Class<? extends Listener> clazz : listenerClasses)
		{
			try {
				Listener listener = clazz.getConstructor().newInstance();
				listeners.add(listener);
			} catch (NoSuchMethodException e) {
				continue;
			}
		}

		module.addCommand(commands.toArray(new ZenithCommand[commands.size()]));
		module.addListener(listeners.toArray(new Listener[listeners.size()]));

		PrintUtils.logVerbose("Registered " + commands.size() + " command(s) and " + listeners.size() + " event(s) to " + module.getName() + ".", InfoType.INFO);

		jar.close();
	}
	
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException
	{
		return findClass(name, true);
	}

	public ZenithModule getModule()
	{
		return module;
	}

	public Set<String> getClasses()
	{
		return interior_classes.keySet();
	}

	/**
	 * Gets the module class inside the jar file associated with this module.
	 * @return
	 * @since 2.1.2
	 */
	public Class<? extends ZenithModule> getModuleClass()
	{
		if (this.moduleClass == null) {
			if (locateModuleClass() == null)
			{
				return null;
			}
		}

		return this.moduleClass;
	}

	@SuppressWarnings("unchecked")
	private <T> List<Class<? extends T>> findSubclasses(boolean allowHideFromCollector, Class<?>... filter)
	{
		List<Class<? extends T>> buffer = new ArrayList<>();

		Enumeration<JarEntry> entries = jar.entries();
		JarEntry entry;
		while (entries.hasMoreElements())
		{
			entry = entries.nextElement();
			//PrintUtils.log("Checking jar entry \"" + entry.getName() + "\".", InfoType.INFO);

			if (entry.isDirectory() || !entry.getName().endsWith(".class"))
				continue;

			Class<?> clazz;
			try {
				clazz = Class.forName(entry.getName().replace("/", ".").replace(".class", ""), false, this);
			} catch (ClassNotFoundException e) {
				continue;
			}

			// Check filter
			if (!isListAssignableFrom(clazz, filter))
				continue;

			// Handle annotations
			if (clazz.isAnnotationPresent(Unimplemented.class))
			{
				PrintUtils.logVerbose("Placing class \"" + clazz.getName() + "\" into unimplemented class map", InfoType.INFO);
				unimplemented_classes.put(clazz.getName(), clazz);
				continue;
			}

			if (clazz.isAnnotationPresent(HideFromCollector.class) && allowHideFromCollector)
				continue;

			buffer.add((Class<? extends T>) clazz);
		}

		return buffer;
	}

	private boolean isListAssignableFrom(Class<?> input, Class<?>... filter)
	{
		for (Class<?> clazz : filter)
			if (!clazz.isAssignableFrom(input))
				return false;

		return true;
	}
	
	Class<?> findClass(String name, boolean checkGlobal) throws ClassNotFoundException
	{
		if (unimplemented_classes.containsKey(name))
			throw new ClassNotFoundException(unimplemented_classes.get(name).getAnnotation(Unimplemented.class).value());

		Class<?> result = interior_classes.get(name);

		if (result == null) 
		{
			if (checkGlobal)
			{
				result = loader.getCachedClass(name);
			}

			if (result == null)
			{
				result = super.findClass(name);

				if (result != null)
				{
					loader.setClass(name, result);
				}
			}

			interior_classes.put(name, result);
		}

		return result;
	}
}