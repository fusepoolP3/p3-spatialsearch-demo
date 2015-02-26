package eu.fusepool.p3.spatial.demo;

import org.apache.clerezza.rdf.core.UriRef;

public class Vocabulary {
    
    // xsd
    public static final UriRef xsd_double = new UriRef("http://www.w3.org/2001/XMLSchema#double");
    public static final UriRef xsd_date = new UriRef("http://www.w3.org/2001/XMLSchema#date");
    
    // rdf
    public static final UriRef rdf_type = new UriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
    
    // wgs84
    public static final UriRef geo_long = new UriRef("http://www.w3.org/2003/01/geo/wgs84_pos#long");
    public static final UriRef geo_lat = new UriRef("http://www.w3.org/2003/01/geo/wgs84_pos#lat");
    
    // schema.org
    public static final UriRef schema_event = new UriRef("http://schema.org/event");
    public static final UriRef schema_location = new UriRef("http://schema.org/location");
    public static final UriRef schema_startDate = new UriRef("http://schema.org/startDate");
    public static final UriRef schema_circle = new UriRef("http://schema.org/circle");
    public static final UriRef schema_containedIn = new UriRef("http://schema.org/containedIn");
    public static final UriRef schema_geo = new UriRef("http://schema.org/geo");
    
    // ldp
    public static final UriRef ldp_rdfsource = new UriRef("http://www.w3.org/ns/ldp#RDFSource");
    public static final UriRef ldp_contains = new UriRef("http://www.w3.org/ns/ldp#contains");
    
    

}
