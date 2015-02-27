package eu.fusepool.p3.spatial.demo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.TypedLiteral;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.core.impl.TypedLiteralImpl;
import org.apache.clerezza.rdf.ontologies.FOAF;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.apache.clerezza.rdf.ontologies.RDFS;
import org.apache.clerezza.rdf.ontologies.XSD;
import org.apache.jena.atlas.lib.StrUtils;
import org.apache.jena.atlas.logging.LogCtl;
import org.apache.jena.query.spatial.EntityDefinition;
import org.apache.jena.query.spatial.SpatialDatasetFactory;
import org.apache.jena.query.spatial.SpatialIndex;
import org.apache.jena.query.spatial.SpatialIndexLucene;
import org.apache.jena.query.spatial.SpatialQuery;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.DatasetFactory;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.tdb.TDBFactory;
/**
 * Search for points of interest or events around a given location and within a time frame 
 * @author luigi
 *
 */
public class SpatialDataEnhancer {
    
    File LUCENE_INDEX_DIR = null;
    File TDB_DIR = null;
    Dataset spatialDataset = null;
    
    private class Circle {
        double centerLong;
        double centerLat;
        double radius = 500.0; // default value for radius in meters
    }
    
    static {
        LogCtl.setLog4j();
    }
    static Logger log = LoggerFactory.getLogger("JenaSpatial");
    
    public SpatialDataEnhancer() throws IOException {
        LUCENE_INDEX_DIR = File.createTempFile("lucene-", "-index");
        TDB_DIR = File.createTempFile("jenatdb-", "-dataset");
        //spatialDataset = initInMemoryDatasetWithLuceneSpatialIndex(LUCENE_INDEX_DIR);
        spatialDataset = initTDBDatasetWithLuceneSpatialIndex(LUCENE_INDEX_DIR, TDB_DIR);
    }
    
    /**
     * Takes a RDF data set to search for point of interest close to objects provided in a graph.  
     * @throws JSONException 
     * @throws ParseException 
     * @throws Exception 
     */
    public JSONObject enhanceJson(List<String> dataSetUrlList, TripleCollection dataToEnhance) throws JSONException {
        JSONObject resultJson = new JSONObject();
        if( dataToEnhance != null ){
            if( ! dataToEnhance.isEmpty() ) {
                
                //look for the knowledge base name in the triple store before fetching the data from the url.
                if (dataSetUrlList != null) {
                    Iterator<String> dataSetIter = dataSetUrlList.iterator();
                    while (dataSetIter.hasNext()) {
                        String dataSetUrl = dataSetIter.next();
                        if( ! isCachedGraph(spatialDataset, dataSetUrl) ){
                          loadKnowledgeBase(spatialDataset, dataSetUrl, dataSetUrl);
                        }
                        else {
                            log.info("Rdf data set " + dataSetUrl + " already in the triple store.");
                        }
                        WGS84Point point = getPoint(dataToEnhance);
                        double radius = getCircle(dataToEnhance).radius;
                        if(point.getStartDate() != null || point.getEndDate() != null){ 
                            resultJson = queryEventsNearbyJson(point, dataSetUrl, radius);
                        }
                        else {
                            resultJson = queryNearbyJson(point, dataSetUrl, radius);
                        }
                    }
                }
            }
            else {
                throw new IllegalArgumentException("An empty graph cannot be enhanced");
            }
        }
        else {
            throw new NullPointerException("A null object has been passed instead of a graph.");
        }
        return resultJson;
    }
    /**
     * Extracts one spatial point or event from the client data.
     * @param graph
     * @return
     * @throws ParseException 
     */
    public WGS84Point getPoint(TripleCollection graph) {
        WGS84Point point = new WGS84Point();   
        NonLiteral pointRef = graph.filter(null, Vocabulary.geo_lat, null).next().getSubject();
        String latitude = ( (TypedLiteral) graph.filter(pointRef, Vocabulary.geo_lat, null).next().getObject() ).getLexicalForm();
        String longitude = ( (TypedLiteral) graph.filter(pointRef, Vocabulary.geo_long, null).next().getObject() ).getLexicalForm();
        point.setUri(pointRef.toString());
        point.setLat(Double.valueOf(latitude));
        point.setLong(Double.valueOf(longitude));
        // look for events linked to places
        if(graph.filter(null, Vocabulary.schema_startDate, null).hasNext()){                    
            String startDate = ( (TypedLiteral) graph.filter(null, Vocabulary.schema_startDate, null).next().getObject()).getLexicalForm();
            point.setStartDate(startDate);
        }
        return point;
    }
    /**
     * Returns a circle object with the geo coordinate and radius of the circle centered around a position.
     * @param positionRef
     * @param graph
     * @return
     */
    public Circle getCircle(TripleCollection graph) {
        Circle circle = new Circle();
        if (graph.filter(null, Vocabulary.schema_circle, null).hasNext()) {
            String circleTxt = ((PlainLiteralImpl) graph.filter(null, Vocabulary.schema_circle, null).next().getObject()).getLexicalForm();
            String [] circleData = circleTxt.split(" ");
            circle.centerLat = Double.parseDouble(circleData[0]);
            circle.centerLong = Double.parseDouble(circleData[1]);
            if (circleData[2] != null && ! "".equals(circleData[2]) ) {
                circle.radius = Double.parseDouble(circleData[2]);
            }
        }
        return circle;
    }
    
    
    /**
     * Searches for points of interest within a circle of a given radius. 
     * The data used is stored in a named graph.
     * @param point
     * @param uri
     * @param radius
     * @return
     * @throws JSONException 
     */
    public JSONObject queryNearbyJson(WGS84Point point, String graphName, double radius) throws JSONException {
        JSONObject resultJson = new JSONObject();
        log.info("queryNearby()");
        long startTime = System.nanoTime();
        String pre = StrUtils.strjoinNL("PREFIX spatial: <http://jena.apache.org/spatial#>",
                "PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>",
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>",
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>");
        
        String qs = StrUtils.strjoinNL("SELECT * ",
                getFromNamedClauses(),
                "WHERE { ",
                "GRAPH ?g ",
                " { ?s spatial:nearby (" + point.getLat() + " " + point.getLong() + " " + radius + " 'm') ;",
                "      rdf:type ?type ; ",
                "      geo:lat ?lat ;" ,
                "      geo:long ?lon ; ",                
                "      rdfs:label ?label .", 
                "  } ",
                "}");

        System.out.println(pre + "\n" + qs);
        spatialDataset.begin(ReadWrite.READ);
     
        resultJson.put("type", "FeatureCollection");
        JSONArray featureList = new JSONArray();
        int poiCounter = 0;
        try {
            Query q = QueryFactory.create(pre + "\n" + qs);
            QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
            ResultSet results = qexec.execSelect() ;
            for ( ; results.hasNext() ; ) {
                QuerySolution solution = results.nextSolution() ;
                JSONObject pointObject = new JSONObject();
                pointObject.put("type", "Point");
                String poiUri = solution.getResource("s").getURI();
                String poiName = checkUriName(poiUri);
                String poiType = checkUriName(solution.getResource("type").getURI());
                String poiLabel = solution.getLiteral("label").getString();
                String poiLatitude = solution.getLiteral("lat").getString();
                String poiLongitude = solution.getLiteral("lon").getString();
                log.info("poi name: " + poiName + " label = " + poiLabel);
                UriRef poiRef = new UriRef(poiName);
                String positionUri = checkUriName(point.getUriName());
                JSONArray coord = new JSONArray("[" + poiLongitude + ", " + poiLatitude + "]");
                pointObject.put("coordinates", coord);
                JSONObject properties = new JSONObject();
                properties.put("name", poiLabel);
                JSONObject feature = new JSONObject();
                feature.put("type", "Feature");
                feature.put("geometry", pointObject);
                feature.put("properties", properties);
                featureList.put(feature);
                
                poiCounter++;
                
            }
            resultJson.put("features", featureList);
          
        } 
        finally {
            spatialDataset.end();
        }
        long finishTime = System.nanoTime();
        double time = (finishTime - startTime) / 1.0e6;
        log.info(String.format("FINISH - %.2fms", time));
        log.info(String.format("Found " + poiCounter + " points of interest."));
        return resultJson;

    }
    
    private String getFromNamedClauses() {
        String fromNamedClauses = "";
        spatialDataset.begin(ReadWrite.READ);
        try {
            Iterator<String> inames = getDataset().listNames();
            while(inames.hasNext()){
                fromNamedClauses += "FROM NAMED <" + inames.next() + "> \n";
            }
        }
        finally {
            spatialDataset.end();
        }
        return fromNamedClauses;
    }
    
    /**
     * Searches for events within a circle of a given radius, starting from a date or within a time frame. 
     * The data used is stored in a named graph.
     * @param point
     * @param uri
     * @param radius
     * @return
     */
    public JSONObject queryEventsNearbyJson(WGS84Point point, String graphName, double radius) throws JSONException {
        JSONObject resultJson = new JSONObject();
        log.info("queryNearby()");
        long startTime = System.nanoTime();
        String pre = StrUtils.strjoinNL("PREFIX spatial: <http://jena.apache.org/spatial#>",
                "PREFIX geo: <http://www.w3.org/2003/01/geo/wgs84_pos#>",
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>",
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>",
                "PREFIX schema: <http://schema.org/>",
                "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>");
        
        String qs = StrUtils.strjoinNL("SELECT * ",
                getFromNamedClauses(),
                "WHERE { ",
                "GRAPH ?g ",
                " { ?location spatial:nearby (" + point.getLat() + " " + point.getLong() + " " + radius + " 'm') .",
                "   ?location geo:lat ?lat ." ,
                "   ?location geo:long ?lon . ",
                "   ?location rdf:type ?type . ",
                "   ?location rdfs:label ?label .",
                "   ?event schema:location ?location .",
                "   ?event rdfs:label ?eventLabel .",
                "   ?event schema:startDate ?start .",
                "   ?event schema:endDate ?end .",
                "   FILTER(?start >= \"" + point.getStartDate() + "\"^^xsd:date ) ",
                " } ",
                "}");

        log.info(pre + "\n" + qs);
        spatialDataset.begin(ReadWrite.READ);
        int poiCounter = 0;
        JSONArray featureList = new JSONArray();
        try {
            Query q = QueryFactory.create(pre + "\n" + qs);
            QueryExecution qexec = QueryExecutionFactory.create(q, spatialDataset);
            ResultSet results = qexec.execSelect() ;
            for ( ; results.hasNext() ; ) {
                QuerySolution solution = results.nextSolution() ;
                JSONObject pointObject = new JSONObject();
                pointObject.put("type", "Point");
                String poiUri = solution.getResource("location").getURI();
                String poiName = checkUriName(poiUri);
                String poiLabel = solution.getLiteral("label").getString();
                String poiLatitude = solution.getLiteral("lat").getString();
                String poiLongitude = solution.getLiteral("lon").getString();
                String eventUri = solution.getResource("event").getURI();
                String eventLabel = solution.getLiteral("eventLabel").getString();
                String startDate = solution.getLiteral("start").getString();
                String endDate = solution.getLiteral("end").getString();
                log.info("poi name: " + poiName + " label = " + poiLabel);
                UriRef poiRef = new UriRef(poiName);                
                String positionUri = checkUriName(point.getUriName());
                JSONArray coord = new JSONArray("[" + poiLongitude + ", " + poiLatitude + "]");
                pointObject.put("coordinates", coord);
                JSONObject properties = new JSONObject();
                properties.put("name", eventLabel);
                properties.put("start", startDate);
                properties.put("end", endDate);                
                JSONObject feature = new JSONObject();
                feature.put("type", "Feature");
                feature.put("geometry", pointObject);
                feature.put("properties", properties);
                featureList.put(feature);
                
                UriRef eventRef = new UriRef(eventUri);
                
                poiCounter++;
                
            }
            
            resultJson.put("features", featureList);
          
        } 
        finally {
            spatialDataset.end();
        }
        long finishTime = System.nanoTime();
        double time = (finishTime - startTime) / 1.0e6;
        log.info(String.format("FINISH - %.2fms", time));
        log.info(String.format("Found " + poiCounter + " points of interest."));
        return resultJson;

    }
    
     /**
     * Extracts the name from the URI (removes '<' and '>' )
     * @param uri
     * @return
     */
    private String checkUriName(String uri){
        if(uri.startsWith("<")){
            uri = uri.substring(1);
        }
        if(uri.endsWith(">")){
            uri = uri.substring(0, uri.length() -1);
        }
        return uri;
    }
    
    private Dataset initInMemoryDatasetWithLuceneSpatialIndex(File indexDir) throws IOException {
        SpatialQuery.init();
        deleteOldFiles(indexDir);
        indexDir.mkdirs();
        return createDatasetByCode(indexDir);
    }

    private Dataset initTDBDatasetWithLuceneSpatialIndex(File indexDir, File TDBDir) throws IOException {
        SpatialQuery.init();
        deleteOldFiles(indexDir);
        deleteOldFiles(TDBDir);
        indexDir.mkdirs();
        TDBDir.mkdir();
        return createDatasetByCode(indexDir, TDBDir);
    }

    private void deleteOldFiles(File indexDir) {
        if (indexDir.exists()) {
            emptyAndDeleteDirectory(indexDir);
        }
    }
    
    private Dataset createDatasetByCode(File indexDir) throws IOException {
        // Base data
        Dataset ds1 = DatasetFactory.createMem();
        return joinDataset(ds1, indexDir);
    }

    private Dataset createDatasetByCode(File indexDir, File TDBDir) throws IOException {
        // Base data
        Dataset ds1 = TDBFactory.createDataset(TDBDir.getAbsolutePath());
        return joinDataset(ds1, indexDir);
    }

    private Dataset joinDataset(Dataset baseDataset, File indexDir) throws IOException {
        EntityDefinition entDef = new EntityDefinition("entityField", "geoField");

        // you need JTS lib in the classpath to run the examples
        //entDef.setSpatialContextFactory(SpatialQuery.JTS_SPATIAL_CONTEXT_FACTORY_CLASS);
        // set custom goe predicates
        
        entDef.addSpatialPredicatePair(ResourceFactory.createResource("http://schema.org/latitude"), ResourceFactory.createResource("http://schema.org/longitude"));
        /*
        entDef.addSpatialPredicatePair(ResourceFactory.createResource("http://localhost/jena_example/#latitude_2"), ResourceFactory.createResource("http://localhost/jena_example/#longitude_2"));
        entDef.addWKTPredicate(ResourceFactory.createResource("http://localhost/jena_example/#wkt_1"));
        entDef.addWKTPredicate(ResourceFactory.createResource("http://localhost/jena_example/#wkt_2"));
        */
        // Lucene, index in File system.
        Directory dir = FSDirectory.open(indexDir);

        // Join together into a dataset
        Dataset ds = SpatialDatasetFactory.createLucene(baseDataset, dir, entDef);

        return ds;
    }
    
    private void emptyAndDeleteDirectory(File dir) {
        File[] contents = dir.listFiles();
        if (contents != null) {
            for (File content : contents) {
                if (content.isDirectory()) {
                    emptyAndDeleteDirectory(content);
                } else {
                    content.delete();
                }
            }
        }
        dir.delete();
    }
    
    private void destroy(Dataset spatialDataset, File luceneIndex, File tdbDataset) {

        SpatialIndex index = (SpatialIndex) spatialDataset.getContext().get(SpatialQuery.spatialIndex);
        if (index instanceof SpatialIndexLucene) {
            deleteOldFiles(luceneIndex);
            deleteOldFiles(tdbDataset);
        }

    }
    /**
     * Load a knowledge base
     * @param spatialDataset
     * @param uri
     * @param url
     * @throws Exception
     */
    public void loadKnowledgeBase(Dataset spatialDataset, String url, String graphName)  {
        
        log.info("Start loading data from: " + url);
        long startTime = System.nanoTime();
        spatialDataset.begin(ReadWrite.WRITE);
        try {
            Model m = spatialDataset.getNamedModel(graphName);
            RDFDataMgr.read(m, url);
            spatialDataset.commit();
        } finally {
            spatialDataset.end();
        }
        
        long numberOfTriples = 0;
        
        spatialDataset.begin(ReadWrite.READ);
        try {
            Model m = spatialDataset.getNamedModel(graphName);
            numberOfTriples = m.size();
            spatialDataset.commit();
        } finally {
            spatialDataset.end();
        }

        long finishTime = System.nanoTime();
        double time = (finishTime - startTime) / 1.0e6;
        log.info(String.format("Finish loading " + numberOfTriples + " triples in graph " + graphName + " - %.2fms", time));
       
    }
    
    public Dataset getDataset() {
        return spatialDataset;
    }
    
    
    /**
     * Loads the data to be used as the application knowledge base. 
     * @throws Exception
     */
    public InputStream importKnowledgebase(String sourceDataUrl) throws Exception{
        
        URL sourceUrl = new URL(sourceDataUrl);
        URLConnection connection = sourceUrl.openConnection();
        return connection.getInputStream();
    }
    
    public boolean isCachedGraph(Dataset dataset, String graphName){
        boolean isCached = false;
        dataset.begin(ReadWrite.READ);
        try {
            Iterator<String> inames = getDataset().listNames();
            while(inames.hasNext()){
                if( graphName.equals( inames.next() )) {
                     isCached = true;  
                }
            }
        }
        finally {
            dataset.end();
        }
        return isCached;
    }

}
