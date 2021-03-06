package com.almende.eve.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

import org.yaml.snakeyaml.Yaml;

public class YamlConfig {
	private Logger logger = Logger.getLogger(this.getClass().getSimpleName());
	private Map<String, Object> config = null;
	
	public YamlConfig() {}

	/**
	 * Load the configuration file by filename (absolute path)
	 * Default filename is /WEB-INF/eve.yaml
	 * @param filename
	 * @return
	 * @throws FileNotFoundException 
	 */
	public YamlConfig(String filename) throws FileNotFoundException {
		load(filename);
	}
	
	/**
	 * Load the configuration file from input stream
	 * @param filename
	 * @return
	 * @throws FileNotFoundException 
	 */
	public YamlConfig(InputStream inputStream) {
		load(inputStream);
	}

	/**
	 * Load the configuration from a map
	 * @param map
	 * @return
	 */
	public YamlConfig(Map<String, Object> config) {
		this.config = config;
	}
	
	/**
	 * Load the configuration file by filename (absolute path)
	 * Default filename is /WEB-INF/eve.yaml
	 * @param filename
	 * @return
	 * @throws FileNotFoundException 
	 */
	public final void load(String filename) throws FileNotFoundException{
		File file = new File(filename);
		logger.info("Loading configuration file " + file.getAbsoluteFile() + "...");

		FileInputStream in = new FileInputStream(filename);
		load(in);
	}
	
	/**
	 * Load the configuration file from input stream
	 * @param filename
	 * @return
	 * @throws FileNotFoundException 
	 */
	@SuppressWarnings("unchecked")
	public final void load(InputStream inputStream) {
		Yaml yaml = new Yaml();
		config = yaml.loadAs(inputStream, Map.class);
	}
	
	/**
	 * Get the full configuration
	 * returns null if no configuration file is loaded
	 * @return
	 */
	public Map<String, Object> get() {
		return config;		
	}
	
	/**
	 * retrieve a (nested) parameter from the config
	 * the parameter name can be a simple name like config.get("url"), 
	 * or nested parameter like config.get("servlet", "config", "url")
	 * null is returned when the parameter is not found, or when no 
	 * configuration file is loaded.
	 * @param params    One or multiple (nested) parameters
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(String ... params) {
		if (config == null) {
			return null;
		}
		
		Map<String, Object> c = config;
		for (int i = 0; i < params.length - 1; i++) {
			String key = params[i];
			// FIXME: check instance
			c = (Map<String, Object>) c.get(key); 
			if (c == null) {
				return null;
			}
		}
		
		// FIXME: check instance
		return (T) c.get(params[params.length - 1]);
	}
}
