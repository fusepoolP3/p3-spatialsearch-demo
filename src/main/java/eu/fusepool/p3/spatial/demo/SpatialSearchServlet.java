package eu.fusepool.p3.spatial.demo;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.core.impl.TypedLiteralImpl;
import org.apache.clerezza.rdf.core.serializedform.Parser;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.json.JSONException;
import org.json.JSONObject;

public class SpatialSearchServlet extends HttpServlet{
    
    String graph = null;
    SpatialDataEnhancer enhancer = null;
    
    public void init() throws ServletException {
        
        try {
            enhancer = new SpatialDataEnhancer();
            
        } catch (IOException e) {
            
            e.printStackTrace();
        }
        
    }
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        
        String container = request.getParameter("container");
        String coordinates = request.getParameter("coordinates");
        double radius = Double.parseDouble(request.getParameter("radius"));
        String date = request.getParameter("date");
        
        System.out.println("Container: " + container + ", coordinates: " + coordinates + ", radius (m): " + radius + ", date: " + date);
        
        // Puts the input data into RDF
        TripleCollection rdfQuery = getRdfQuery(getCoordinates(coordinates), radius, date);
        
        // Search for poi or events. Result in GeoJson
        JSONObject pois = null;
        try {
            pois = enhancer.enhanceJson(getRdfSources(container), rdfQuery);
        } catch (JSONException e) {
            
            e.printStackTrace();
        }
        
        System.out.println(pois.toString());
        
        response.getWriter().println(pois);
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
    }
    /**
     * Puts the input information about position and start date into RDF.
     * @param coordinates
     * @param radius
     * @param startDate
     * @return
     */
    private TripleCollection getRdfQuery(double [] coordinates, double radius, String startDate) {
        TripleCollection rdf = new SimpleMGraph();
        String lat = String.valueOf(coordinates[0]);
        String lon = String.valueOf(coordinates[1]);
        String rad = String.valueOf(radius);
        UriRef positionUri = new UriRef("urn:uuid:fusepoolp3:mylocation");
        UriRef circleUri = new UriRef("urn:uuid:fusepoolp3:mycircle");
        rdf.add(new TripleImpl(positionUri, Vocabulary.geo_lat, new TypedLiteralImpl(String.valueOf(coordinates[0]), Vocabulary.xsd_double)));
        rdf.add(new TripleImpl(positionUri, Vocabulary.geo_long, new TypedLiteralImpl(String.valueOf(coordinates[1]), Vocabulary.xsd_double)));
        rdf.add(new TripleImpl(positionUri, Vocabulary.schema_geo, circleUri));
        rdf.add(new TripleImpl(circleUri, Vocabulary.schema_circle, new PlainLiteralImpl(lat + " " + lon + " " + rad) ));
        if (startDate != null && ! "".equals(startDate)) {
            UriRef eventUri = new UriRef("urn:uuid:fusepoolp3:myevent");
            rdf.add(new TripleImpl(eventUri, Vocabulary.schema_startDate, new TypedLiteralImpl(startDate, Vocabulary.xsd_date)));
            rdf.add(new TripleImpl(eventUri, Vocabulary.schema_location, positionUri));
        }
        return rdf;
    }
    
    private double [] getCoordinates(String latlong) {
        String [] coordsStr =  latlong.substring(7, latlong.length() - 1).split(",");
        double [] coordinates = {Double.parseDouble(coordsStr[0]), Double.parseDouble(coordsStr[1])};
        return coordinates;
    }
    /**
     * Retrieves the child of a container that are ldp:RDFSource. A container can contain ldp:RDFSource
     * or being a ldp:RDFSource itself.
     * @param container
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    private List<String> getRdfSources(String container) throws MalformedURLException, IOException {
        ArrayList<String> containerChild = new ArrayList<String>();
        URLConnection connection = new URL(container).openConnection();
        connection.addRequestProperty("Accept", "text/turtle");
        InputStream is = connection.getInputStream();
        Parser parser = Parser.getInstance();
        TripleCollection containerGraph = parser.parse(is, SupportedFormat.TURTLE);
        // is it a LDP container ?
        Iterator<Triple> childIter = containerGraph.filter(null, Vocabulary.ldp_contains, null);
        while (childIter.hasNext()) {
            UriRef childRef = (UriRef) childIter.next().getObject();
            URLConnection conn = new URL(childRef.getUnicodeString()).openConnection();
            conn.addRequestProperty("Accept", "text/turtle");
            InputStream isChild = conn.getInputStream();
            TripleCollection childRdf = parser.parse(isChild, SupportedFormat.TURTLE);
            Iterator<Triple> rdfSourceIter = childRdf.filter(null, Vocabulary.rdf_type, Vocabulary.ldp_rdfsource);
            while (rdfSourceIter.hasNext()) {
                UriRef rdfSourceRef = (UriRef) rdfSourceIter.next().getSubject();
                containerChild.add(rdfSourceRef.getUnicodeString());
            }
        }
        
        // or is it a RDF graph ?
        if (containerChild.size() == 0 ) {
            containerChild.add(container);
        }
        return containerChild;
    }
}
