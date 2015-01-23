package eu.fusepool.p3.osm.demo;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class Main {
        
    public static void main(String[] args) throws Exception {
        Server server = new Server(Integer.valueOf(8080));
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/demo/");
        webapp.setResourceBase("src/main/webapp");
        server.setHandler(webapp);
        server.start();
        server.join();
          
    }
      

}
