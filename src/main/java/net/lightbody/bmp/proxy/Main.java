package net.lightbody.bmp.proxy;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import net.lightbody.bmp.exception.JettyException;
import net.lightbody.bmp.proxy.guice.ConfigModule;
import net.lightbody.bmp.proxy.guice.JettyModule;
import net.lightbody.bmp.proxy.guice.RestModule;
import net.lightbody.bmp.proxy.util.StandardFormatter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.slf4j.LoggerFactory;

public class Main {
    private static final String LOGGING_PROPERTIES_FILENAME = "conf/bmp-logging.properties";
	private static final String VERSION_PROP = "/version.prop";
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Main.class);
    private static String VERSION = null;

    public static void main(String[] args) {
        configureJdkLogging();

        final Injector injector = Guice.createInjector(new ConfigModule(args), new JettyModule(), new RestModule());

        LOG.info("Starting BrowserMob Proxy version {}", getVersion());

        Server server = injector.getInstance(Server.class);
        ServletContextHandler handler = (ServletContextHandler)server.getHandler();
        handler.addEventListener(injector.getInstance(GuiceResteasyBootstrapServletContextListener.class));
        handler.addServlet(new ServletHolder(HttpServletDispatcher.class), "/*");
        server.setHandler(handler);        
        
        try {
			server.start();
		} catch (Exception e) {
			LOG.error("Failed to start Jetty server. Aborting.", e);
			
			throw new JettyException("Unable to start Jetty server", e);
		}

        try {
			server.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
    }

    public static String getVersion() {
        if (VERSION == null) {
            String version = "UNKNOWN/DEVELOPMENT";
            InputStream is = Main.class.getResourceAsStream(VERSION_PROP);

            if (is != null) {
                Properties props = new Properties();
                try {
					props.load(is);
					version = props.getProperty("version");
				} catch (IOException e) {
					Log.warn("Unable to load properties file in " + VERSION_PROP + "; version will not be set.", e);
				}
                
            }

            VERSION = version;
        }

        return VERSION;
    }

    /**
     * Configures JDK logging when running the proxy in stand-alone mode. By default, loads logging settings from a file called bmp-logging.properties.
     */
    static void configureJdkLogging() {
    	boolean useDefaultLogging = false;
    	
    	FileInputStream logFile;
		try {
			logFile = new FileInputStream(LOGGING_PROPERTIES_FILENAME);
			
	    	try {
				LogManager.getLogManager().readConfiguration(logFile);
			} catch (SecurityException e) {
				System.out.println("Unable to read " + LOGGING_PROPERTIES_FILENAME + ". Using default logging configuration.");
				useDefaultLogging = true;
			} catch (IOException e) {
				System.out.println("Unable to read " + LOGGING_PROPERTIES_FILENAME + ". Using default logging configuration.");
				useDefaultLogging = true;
			} finally {
				try {
					logFile.close();
				} catch (IOException e) {
					// safely ignore file-closing exceptions
				}
			}

		} catch (FileNotFoundException e) {
			System.out.println("Unable to find " + LOGGING_PROPERTIES_FILENAME + ". Using default logging configuration.");
			useDefaultLogging = true;
		}
    	
		// if we couldn't find/read the bmp-logging.properties file, configure a default logger
		if (useDefaultLogging) {
			configureDefaultLogger();
		}

        // tell commons-logging to use the JDK logging (otherwise it would default to log4j
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Jdk14Logger");
    }

	private static void configureDefaultLogger() {
		Logger logger = Logger.getLogger("");
        Handler[] handlers = logger.getHandlers();
        for (Handler handler : handlers) {
            logger.removeHandler(handler);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new StandardFormatter());
        
        handler.setLevel(Level.FINE);
        logger.addHandler(handler);
	}
}
 
