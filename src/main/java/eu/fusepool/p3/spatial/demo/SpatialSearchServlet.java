package eu.fusepool.p3.spatial.demo;

import java.io.IOException;
import java.util.Iterator;

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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SpatialSearchServlet extends HttpServlet{
    
    private static final UriRef geo_long = new UriRef("http://www.w3.org/2003/01/geo/wgs84_pos#long");
    private static final UriRef geo_lat = new UriRef("http://www.w3.org/2003/01/geo/wgs84_pos#lat");
    private static final UriRef schema_event = new UriRef("http://schema.org/event");
    private static final UriRef schema_location = new UriRef("http://schema.org/location");
    private static final UriRef schema_startDate = new UriRef("http://schema.org/startDate");
    private static final UriRef xsd_double = new UriRef("http://www.w3.org/2001/XMLSchema#double");
    private static final UriRef xsd_date = new UriRef("http://www.w3.org/2001/XMLSchema#date");
    private static final UriRef schema_circle = new UriRef("http://schema.org/circle");
    private static final UriRef schema_containedIn = new UriRef("http://schema.org/containedIn");
    private static final UriRef schema_geo = new UriRef("http://schema.org/geo");
    
    String graph = null;
    SpatialDataEnhancer enhancer = null;
    
    public void init() throws ServletException {
        if (getServletContext().getAttribute("graph") != null) {
            graph = getServletContext().getAttribute("graph").toString();            
        }
        else {
            throw new ServletException("The data graph must be provided at the start of the application");
        }
        
        try {
            enhancer = new SpatialDataEnhancer();
            enhancer.loadKnowledgeBase(enhancer.getDataset(), graph, graph);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String coordinates = request.getParameter("coordinates");
        double radius = Double.parseDouble(request.getParameter("radius"));
        String date = request.getParameter("date");
        
        System.out.println("Coordinates: " + coordinates + ", radius (m): " + radius + ", date: " + date);
        
        
        WGS84Point position = getPosition(getCoordinates(coordinates), date);
        TripleCollection rdfQuery = getRdfQuery(getCoordinates(coordinates), radius, date);
        
        JSONObject pois = null;
        try {
            pois = enhancer.enhanceJson(graph, rdfQuery);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        System.out.println(pois.toString());
        
        response.getWriter().println(pois);
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
    }
    
    private String prepareGeoJson(double [] coordinates, String date) throws JSONException {
        JSONObject collection = new JSONObject();
        collection.put("type", "FeatureCollection");
        JSONArray featureList = new JSONArray();
        JSONObject point = new JSONObject();
        point.put("type", "Point");
        JSONArray coord = new JSONArray("[" + (coordinates[1] + 0.001) + ", " + (coordinates[0]+0.001) + "]");
        point.put("coordinates", coord);
        
        JSONObject properties = new JSONObject();
        properties.put("name", "Trento");
        properties.put("start", date);
        
        JSONObject feature = new JSONObject();
        feature.put("type", "Feature");
        feature.put("properties", properties);
        feature.put("geometry", point);
        
        featureList.put(feature);
        collection.put("features", featureList);
        return collection.toString();
    }
    
    private WGS84Point getPosition(double [] coordinates, String startDate) {
        WGS84Point point = new WGS84Point();
        point.setLat(coordinates[0]);
        point.setLong(coordinates[1]);
        point.setStartDate(startDate);
        return point;
    }
    
    private TripleCollection getRdfQuery(double [] coordinates, double radius, String startDate) {
        TripleCollection rdf = new SimpleMGraph();
        String lat = String.valueOf(coordinates[0]);
        String lon = String.valueOf(coordinates[1]);
        String rad = String.valueOf(radius);
        UriRef positionUri = new UriRef("urn:uuid:fusepoolp3:mylocation");
        UriRef circleUri = new UriRef("urn:uuid:fusepoolp3:mycircle");
        rdf.add(new TripleImpl(positionUri, geo_lat, new TypedLiteralImpl(String.valueOf(coordinates[0]), xsd_double)));
        rdf.add(new TripleImpl(positionUri, geo_long, new TypedLiteralImpl(String.valueOf(coordinates[1]), xsd_double)));
        rdf.add(new TripleImpl(positionUri, schema_geo, circleUri));
        rdf.add(new TripleImpl(circleUri, schema_circle, new PlainLiteralImpl(lat + " " + lon + " " + rad) ));
        if (startDate != null && ! "".equals(startDate)) {
            UriRef eventUri = new UriRef("urn:uuid:fusepoolp3:myevent");
            rdf.add(new TripleImpl(eventUri, schema_startDate, new TypedLiteralImpl(startDate, xsd_date)));
            rdf.add(new TripleImpl(eventUri, schema_location, positionUri));
        }
        return rdf;
    }
    
    private double [] getCoordinates(String latlong) {
        String [] coordsStr =  latlong.substring(7, latlong.length() - 1).split(",");
        double [] coordinates = {Double.parseDouble(coordsStr[0]), Double.parseDouble(coordsStr[1])};
        return coordinates;
    }
}
