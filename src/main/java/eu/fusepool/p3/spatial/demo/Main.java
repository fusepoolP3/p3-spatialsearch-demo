package eu.fusepool.p3.spatial.demo;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class Main {
        
    public static void main(String[] args) throws Exception {
        
        int port = 7100;
        if (args != null) {
         port = Integer.parseInt(args[0]);
        }
        
        Server server = new Server(port);
        System.out.println("Running on port " + port);
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");
        //webapp.setAttribute("graph", graph);
        webapp.setResourceBase("src/main/webapp");
        server.setHandler(webapp);
        server.start();
        server.join();
          
    }
      

}
