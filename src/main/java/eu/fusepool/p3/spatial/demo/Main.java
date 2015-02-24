package eu.fusepool.p3.spatial.demo;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class Main {
        
    public static void main(String[] args) throws Exception {
        String graph = null;
        if (args != null) {
         graph = args[0];
        }
        Server server = new Server(Integer.valueOf(8080));
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");
        webapp.setAttribute("graph", graph);
        webapp.setResourceBase("src/main/webapp");
        server.setHandler(webapp);
        server.start();
        server.join();
          
    }
      

}
