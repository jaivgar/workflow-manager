package se.ltu.workflow.manager.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Properties;
import java.util.ServiceConfigurationError;

/**
 * Class to handle the extraction of Properties from the configuration files
 * <p>
 * From the 4.0 version of Arrowhead core systems
 * 
 *
 */
public class TypeSafeProperties extends Properties {

    private static final long serialVersionUID = 1L;
    
    private static final String APP_PROP = "application.properties";
    private static final String APP_PROP_DIR = "config" + File.separator + "application.properties";
    private static final String DEFAULT_PROP = "default.properties";
    private static final String DEFAULT_PROP_DIR = "config" + File.separator + "default.properties";
    
    /**
     * Retrieves a property as an integer, performing proper type checks
     * 
     * @param key  The name of the property searched for
     * @param defaultValue  The default value in case the property is not found
     * @return  The integer value of the property
     */
    public int getIntProperty(String key, int defaultValue) {
        String val = getProperty(key);
        try {
            return (val == null) ? defaultValue : Integer.valueOf(val);
        } catch (NumberFormatException e) {
            System.out
            .println(val + " is not a valid number! Please fix the \"" + key + "\" property! "
                    + "Using default value (" + defaultValue + ") instead!");
            return defaultValue;
        }
    }

    /**
     * Retrieves a property as a boolean, performing proper type checks
     * 
     * @param key  The name of the property searched for
     * @param defaultValue  The default value in case the property is not found
     * @return  The boolean value of the property
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String val = getProperty(key);
        return (val == null) ? defaultValue : Boolean.valueOf(val);
    }

    //NOTE add more data types later if needed
    
    /**
     * Retrieve the properties from the file specified in the parameters
     * 
     * @param fileName  The name of the file containing the properties, it should also include 
     * the file extension as input
     * @return The persistent set of properties read from the file
     */
    public static TypeSafeProperties getProp(String fileName) {
        TypeSafeProperties prop = new TypeSafeProperties();
        try {
            File file;
            // Check if file in working directory
            if (new File(fileName).exists()) {
                file = new File(fileName);
            // Check if file inside "config" folder
            } else {
                file = new File("config" + File.separator + fileName);
            }
            FileInputStream inputStream = new FileInputStream(file);
            prop.load(inputStream);
        } catch (FileNotFoundException ex) {
            throw new ServiceConfigurationError(
                    fileName + " file not found, make sure you have the correct working directory "
                            + "set! (directory where the appplication.properties folder can be found)", ex);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return prop;
    }
    
    /**
     * Retrieves the properties from the common property files. 
     * <p>
     * It will first search for the usual "application.properties" filename, if is not found in 
     * current working directory it will go into a "config" folder and search for that filename 
     * again.
     * <p>
     * If the first approach failed, now it will look for a "default.properties" filename, and if
     * that fails it will do as in the previous case and look inside "config" folder.
     * 
     * @return The persistent set of properties read from the file
     */
    public static TypeSafeProperties getProp() {
        TypeSafeProperties prop = new TypeSafeProperties();

        try {

            // Check for a default properties file
            if (Files.isReadable(Paths.get(DEFAULT_PROP))) {
                prop.load(new FileInputStream(DEFAULT_PROP));
            } else if (Files.isReadable(Paths.get(DEFAULT_PROP_DIR))) {
                prop.load(new FileInputStream(DEFAULT_PROP_DIR));
            }
            
            // Check for the usual name of properties file (Will override defaults!)
            if (Files.isReadable(Paths.get(APP_PROP))) {
                prop.load(new FileInputStream(APP_PROP));
            } else if (Files.isReadable(Paths.get(APP_PROP_DIR))) {
                prop.load(new FileInputStream(APP_PROP_DIR));
            }
            
        } catch (IOException e) {
            throw new AssertionError("File loading failed...", e);
        }
        
        if (prop.isEmpty()) {
            throw new RuntimeException("No properties file found in working directory (" + System.getProperty("user.dir") + ")");
        }

        //If MySQL based JDBC URLs are used, we append the system default time zone to the URL
        //This is for a bug fix with certain MySQL JDBC driver versions: https://github.com/arrowhead-f/core-java/issues/30
        String timeZoneQueryParam = "serverTimezone=" + ZoneId.systemDefault().getId();

        String dbAddress = prop.getProperty("db_address");
        if (dbAddress != null && dbAddress.contains("mysql")) {
            if (dbAddress.contains("?")) {
                dbAddress = dbAddress + "&" + timeZoneQueryParam;
            } else {
                dbAddress = dbAddress + "?" + timeZoneQueryParam;
            }
            prop.setProperty("db_address", dbAddress);
        }

        String logAddress = prop.getProperty("log4j.appender.DB.URL");
        if (logAddress != null && logAddress.contains("mysql")) {
            if (logAddress.contains("?")) {
                logAddress = logAddress + "&" + timeZoneQueryParam;
            } else {
                logAddress = logAddress + "?" + timeZoneQueryParam;
            }
            prop.setProperty("log4j.appender.DB.URL", logAddress);
        }

        return prop;
    }



}
