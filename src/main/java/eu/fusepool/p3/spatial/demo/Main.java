package eu.fusepool.p3.spatial.demo;

import java.io.File;
import java.net.URL;
import java.security.ProtectionDomain;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class Main {
        
    public static void main(String[] args) throws Exception {
        
        int port = 7302;
        /*
        if (args == null) {
         port = Integer.parseInt(args[0]);
        }
        */
        String webappDir = "src/main/webapp";
        ProtectionDomain domain = Main.class.getProtectionDomain();
        URL location = domain.getCodeSource().getLocation();
        Server server = new Server(port);
        System.out.println("Running on port " + port);
        WebAppContext context = new WebAppContext();
        context.setWar(location.toExternalForm());
        context.setDescriptor(webappDir + "WEB-INF/web.xml");
        context.setContextPath("/");
        context.setResourceBase(webappDir);
        server.setHandler(context);
        server.start();
        server.join();
          
    }
      

}
