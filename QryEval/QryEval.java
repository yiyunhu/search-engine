/*
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.10.
 *  
 *  Compatible with Lucene 8.1.1.
 */
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.index.*;

/**
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  //  --------------- Methods ---------------------------------------

  /**
   *  @param args The only argument is the parameter file name.
   *  @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);

    //  Open the index and initialize the retrieval model.

    Idx.open (parameters.get ("indexPath"));
    RetrievalModel model = null;
    if (parameters.containsKey("retrievalAlgorithm")) {
      model = initializeRetrievalModel (parameters);
    }

    if (parameters.get("retrievalAlgorithm").equals("ltr")) {
      ((RetrievalModelLTR)model).trainQuery(parameters);

    } else {
      processQueryFile(parameters.get("trecEvalOutputLength"), parameters.get("queryFilePath"), parameters.get("trecEvalOutputPath"), model, parameters);
    }

    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }

  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @return The initialized retrieval model
   *  @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    } else if (modelString.equals("rankedboolean")) {
      model = new RetrievalModelRankedBoolean();
    } else if (modelString.equals("bm25")) {
      double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
      double b = Double.parseDouble(parameters.get("BM25:b"));
      double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
      model = new RetrievalModelBM25(k_1, b, k_3);
    } else if (modelString.equals("indri")) {
      double mu = Double.parseDouble(parameters.get("Indri:mu"));
      double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
      model = new RetrievalModelIndri(mu, lambda);
    } else if (modelString.equals("ltr")) {
      String queryFilePath = parameters.get("queryFilePath");
      String trainingQrelsFile = parameters.get("ltr:trainingQrelsFile");
      String trainingQueryFile = parameters.get("ltr:trainingQueryFile");
      String trainingFeatureVectorsFile = parameters.get("ltr:trainingFeatureVectorsFile");
      String modelFile = parameters.get("ltr:modelFile");
      String testingFeatureVectorsFile = parameters.get("ltr:testingFeatureVectorsFile");
      String testingDocumentScores = parameters.get("ltr:testingDocumentScores");
      String featureDisable = parameters.get("ltr:featureDisable");
      String toolkit = parameters.get("ltr:toolkit");
      String svmRankParamC = parameters.get("ltr:svmRankParamC");
      String svmRankLearnPath = parameters.get("ltr:svmRankLearnPath");
      String svmRankClassifyPath = parameters.get("ltr:svmRankClassifyPath");
      String rankLibModel = parameters.get("ltr:RankLib:model");
      String rankLibMetric = parameters.get("ltr:RankLib:metric2t");
      model = new RetrievalModelLTR(parameters,
                                    queryFilePath,
                                    trainingQrelsFile,
                                    trainingQueryFile,
                                    trainingFeatureVectorsFile,
                                    modelFile,
                                    testingFeatureVectorsFile,
                                    testingDocumentScores,
                                    featureDisable,
                                    svmRankParamC,
                                    svmRankLearnPath,
                                    svmRankClassifyPath,
                                    rankLibMetric,
                                    rankLibModel,
                                    toolkit);
    }

    else {

      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }
      
    return model;
  }

  /**
   * Print a message indicating the amount of memory used. The caller can
   * indicate whether garbage collection should be performed, which slows the
   * program but reduces memory usage.
   * 
   * @param gc 
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";
    Qry q = QryParser.getQuery (qString);

    // Show the query that is evaluated
    
    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList results = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          results.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }
      // sort the result
      results.sort();
      return results;
    } else
      return null;
  }

  /**
   *  Process the query file.
   *  @param queryFilePath Path to the query file
   *  @param model A retrieval model that will guide matching and scoring
   *  @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(String outputLength, String queryFilePath, String outputFile,
                               RetrievalModel model, Map<String, String> parameters)
          throws Exception {

    BufferedReader input = null;

    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));

      //  Each pass of the loop processes one query.

      while ((qLine = input.readLine()) != null) {

        printMemoryUsage(false);
        System.out.println("Query " + qLine);
	String[] pair = qLine.split(":");

	if (pair.length != 2) {
          throw new IllegalArgumentException
            ("Syntax error:  Each line must contain one ':'.");
	}

	String qid = pair[0];
	String query = pair[1];
        ScoreList results = null;
        boolean pseudoRF = false;
        boolean diversity = false;
        boolean hasInitialRanking = false;
        // check if there's pseudo relevance feedback or diversity
        if (parameters.containsKey("prf")) {
          pseudoRF = true;
        } else if (parameters.containsKey("diversity")) {
          diversity = true;
        }

        if (!pseudoRF && !diversity) {
          // no psurdo relevance feedback, process query as normal
          results = processQuery(query, model);
        } else if (pseudoRF) {
          if (parameters.containsKey("prf:initialRankingFile")) {
            results = readInitialRankingFile(parameters.get("prf:initialRankingFile"), qid);
          } else {
            results = processQuery(query, model);
          }
          int numDocs = Integer.parseInt(parameters.get("prf:numDocs"));
          int numTerms = Integer.parseInt(parameters.get("prf:numTerms"));
          double indriMu = Double.parseDouble(parameters.get("prf:Indri:mu"));
          double indriOrigWeight = Double.parseDouble(parameters.get("prf:Indri:origWeight"));
          String expansionQueryFile = parameters.get("prf:expansionQueryFile");

          PseudoRelevanceFeedback prf = new PseudoRelevanceFeedback(numDocs, numTerms, indriMu, indriOrigWeight);
          // create an expansion query Q(learned)
          String learnedQuery = prf.createLearnedQuery(results);
          // get Q(original)
          query = model.defaultQrySopName() + "(" + query + ")";
          // combine Q(original) and Q(learned) to create Q(expanded)
          String expandedQuery = prf.createExpandedQuery(query, learnedQuery);
          // retrieve a new set of documents
          results = processQuery(expandedQuery, model);
          // output learned query to file
          prf.printResults(null, expansionQueryFile, learnedQuery, Integer.parseInt(qid));


        } else if (diversity && parameters.get("diversity").toLowerCase().equals("true")) {
          if (parameters.containsKey("diversity:initialRankingFile")) {
            hasInitialRanking = true;
          }
          int maxInputRankingsLength = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
          int maxResultRankingLength = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
          double lambda = Double.parseDouble(parameters.get("diversity:lambda"));
          String algo = null;

          // check if diversity algorithm is xQuAD or PM2
          if (parameters.get("diversity:algorithm").equals("xQuAD")) {
            algo = "xQuAD";
          } else if (parameters.get("diversity:algorithm").equals("PM2")) {
            algo = "PM2";
          }

          Diversification df = new Diversification(algo, maxInputRankingsLength, maxResultRankingLength, lambda);

          String intentsFile = parameters.get("diversity:intentsFile");
          List<String> allIntents = Diversification.readIntents(intentsFile, Integer.parseInt(qid));


          List<Map<Integer, Double>> documentRanking = null;

          if (hasInitialRanking) {
            // read relevance-based document rankings for query q
            // from the the diversity:initialRankingFile file;
            // read relevance-based document rankings for query intents q.i
            // from the diversity:initialRankingFile file;
            String initialRankingFile = parameters.get("diversity:initialRankingFile");
            documentRanking = readDiversityInitialRankingFile(initialRankingFile, Integer.parseInt(qid), maxInputRankingsLength);
          } else {
            // read query q from the query file
            // use query q to retrieve documents;
            ScoreList s = processQuery(query, model);
            documentRanking = Diversification.processQuery(s, allIntents, model, maxInputRankingsLength);
          }
          // produce a diversified ranking
          results = df.produceDiversifiedRanking(documentRanking);
          results.sort();
        }

        // print results
        if (results != null) {
          printResults(outputLength, outputFile, qid, results);
          System.out.println();
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }

  /**
   * Print the query results.
   * Outputs in the format specified in the homework page, which is:
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static String DUMMY = "10 Q0 dummy 1 0 hw5\n";
  static void printResults(String outputLength, String outputFile, String queryName, ScoreList result) throws IOException {

    PrintWriter pw = new PrintWriter(new FileOutputStream(new File(outputFile), true));
    if (result.size() < 1) {
      pw.print(DUMMY);
    }

    // make sure output length does not exceed our parameter setting
    int printLength = Math.min(Integer.parseInt(outputLength), result.size());
    for (int i = 0; i < printLength; i++) {
      pw.format("%s Q0 %s %d %.12f hw5\n", queryName, Idx.getExternalDocid(result.getDocid(i)), i + 1, result.getDocidScore(i));
    }
    pw.close();

    // for console printout
//    System.out.println(queryName + ":  ");
//    if (result.size() < 1) {
//      System.out.println(DUMMY);
//    } else {
//      for (int i = 0; i < result.size(); i++) {
//        System.out.println("\t" + i + ":  " + Idx.getExternalDocid(result.getDocid(i)) + ", "
//            + result.getDocidScore(i));
//      }
//    }
  }

  /**
   *  Read the specified parameter file, and confirm that the required
   *  parameters are present.  The parameters are returned in a
   *  HashMap.  The caller (or its minions) are responsible for processing
   *  them.
   *  @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();
    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    //  Store (all) key/value parameters in a hashmap.

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    //  Confirm that some of the essential parameters are present.
    //  This list is not complete.  It is just intended to catch silly
    //  errors.

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }


  /**
   * Read a document ranking in trec_eval input format from the prf:initialRankingFile.
   * @param filename
   * @param id
   * @return scorelist for the initial ranking file
   */
  private static ScoreList readInitialRankingFile(String filename, String id) throws Exception {
    ScoreList s = new ScoreList();
    File initialRankingFile = new File (filename);

    if ( !initialRankingFile.canRead ()) {
      throw new IllegalArgumentException
              ("Can't read " + filename);
    }

    //  Store (all) key/value parameters in a hashmap.
    Scanner scan = new Scanner(initialRankingFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split (" ");

      if (id.equals(pair[0])) {
        int docid = Idx.getInternalDocid(pair[2]);
        double score = Double.parseDouble(pair[4]);
        s.add(docid, score);
      }
    } while (scan.hasNext());

    scan.close();
    return s;
  }


  /**
   * Read a document ranking in trec_eval input format from the diversity:initialRankingFile.
   * @param filename
   * @param id
   * @return scorelist for the initial ranking file in diversification
   */
  private static List<Map<Integer, Double>> readDiversityInitialRankingFile(String filename, int id, int maxInputRankingsLength) throws Exception {
    List<ScoreList> initialScores = new ArrayList<>();
    File initialRankingFile = new File (filename);

    if ( !initialRankingFile.canRead ()) {
      throw new IllegalArgumentException
              ("Can't read " + filename);
    }

    //  Store (all) key/value parameters in a hashmap.
    Scanner scan = new Scanner(initialRankingFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split (" ");
      String query = pair[0];

      int docid = Idx.getInternalDocid(pair[2]);
      double score = Double.parseDouble(pair[4]);
      int qid = -1;
      int intent = 0;
      if (query.contains(".")) {
        String[] diffIntent = query.split("\\.");
        qid = Integer.parseInt(diffIntent[0]);
        intent = Integer.parseInt(diffIntent[1]);
      } else {
        qid = Integer.parseInt(query);

      }
      if (qid == id) {
        if (intent <= initialScores.size() - 1) {
          ScoreList s = initialScores.get(intent);
          s.add(docid, score);
        } else {
          ScoreList s = new ScoreList();
          s.add(docid, score);
          initialScores.add(s);
        }
      }
    } while (scan.hasNext());

    scan.close();

    // process scoreList
    List<Map<Integer, Double>> allDocRankings = new ArrayList<>();
    allDocRankings = processScoreList(initialScores, maxInputRankingsLength);

    return allDocRankings;
  }


  /**
   * Process scorelist into a <Integer,Double> map.
   * @param initialScores
   * @param maxInputRankingsLength
   * @return a list of map
   */
  private static List<Map<Integer, Double>> processScoreList(List<ScoreList> initialScores, int maxInputRankingsLength) {
    List<Map<Integer, Double>> allDocRankings = new ArrayList<>();
    int initialScoreSize = initialScores.get(0).size();
    int updateSize = Math.min(maxInputRankingsLength, initialScoreSize);
    for (ScoreList sl: initialScores) {
      ScoreList curr = sl;
      Map<Integer, Double> map = new LinkedHashMap<>();
      for (int i = 0; i < updateSize; i++) {
        map.put(curr.getDocid(i), curr.getDocidScore(i));
      }
      allDocRankings.add(map);
    }
    return allDocRankings;
  }
}
