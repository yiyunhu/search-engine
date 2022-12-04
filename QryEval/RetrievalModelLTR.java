import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class RetrievalModelLTR extends RetrievalModel {

        @Override
        public String defaultQrySopName() {
                return null;
        }

        /**
         * Use BM25 to get initial rankings.
         */
        private RetrievalModelBM25 BM25;
        /**
         * Indri score.
         */
        private RetrievalModelIndri indri;
        /**
         * The path to the testing queries.
         */
        private String queryFilePath;
        /**
         * A file of relevance judgments.
         * Column 1 is the query id.
         * Column 2 is ignored.
         * Column 3 is the document id.
         * Column 4 indicates the degree of relevance
         */
        private String trainingQrelsFile;
        /**
         * A file of training queries.
         */
        private String trainingQueryFile;
        /**
         * The file of feature vectors that your software writes for the training queries.
         */
        private String trainingFeatureVectorsFile;
        /**
         * The file where the learning toolkit saves the trained model.
         */
        private String modelFile;
        /**
         * The file of feature vectors that your software writes for the testing queries.
         */
        private String testingFeatureVectorsFile;
        /**
         * The file of document scores that the learning toolkit writes for the testing feature vectors.
         */
        private String testingDocumentScores;
        /**
         * List of features that are disabled.
         */
        private List<Integer> listDisable;
        /**
         * List of features that are abled.
         */
        private List<Integer> listAble;
        /**
         * The value of the c parameter for SVMrank. 0.001 is a good default.
         */
        private String svmRankParamC;
        /**
         * A path to the svm_rank_learn executable.
         */
        private String svmRankLearnPath;
        /**
         * A path to the svm_rank_classify executable.
         */
        private String svmRankClassifyPath;
        /**
         * Some RankLib algorithms (e.g., Coordinate Ascent) try to optimize for a specific metric.
         * The options are "MAP", "NDCG@k" (e.g., NDCG@10), and "P@K" (e.g., P@10).
         * ListNet ignores this parameter.
         */
        private String rankLibMetric;
        /**
         * The learning algorithm id.
         * This poject uses 4 (Coordinate Ascent) and 7 (ListNet).
         */
        private String rankLibModel;
        /**
         * SVM or RankLib
         */
        private String toolkit;

        // constructor
        public RetrievalModelLTR(Map<String, String> parameters,
                                 String queryFilePath,
                                 String trainingQrelsFile,
                                 String trainingQueryFile,
                                 String trainingFeatureVectorsFile,
                                 String modelFile,
                                 String testingFeatureVectorsFile,
                                 String testingDocumentScores,
                                 String featureDisableStr,
                                 String svmRankParamC,
                                 String svmRankLearnPath,
                                 String svmRankClassifyPath,
                                 String rankLibMetric,
                                 String rankLibModel,
                                 String toolkit) {

                BM25 = new RetrievalModelBM25(Double.parseDouble(parameters.get("BM25:k_1")), Double.parseDouble(parameters.get("BM25:b")), Double.parseDouble(parameters.get("BM25:k_3")));
                indri = new RetrievalModelIndri(Double.parseDouble(parameters.get("Indri:mu")), Double.parseDouble(parameters.get("Indri:lambda")));
                this.queryFilePath = queryFilePath;
                this.trainingQrelsFile = trainingQrelsFile;
                this.trainingQueryFile = trainingQueryFile;
                this.trainingFeatureVectorsFile = trainingFeatureVectorsFile;
                this.modelFile = modelFile;
                this.testingFeatureVectorsFile = testingFeatureVectorsFile;
                this.testingDocumentScores = testingDocumentScores;
                listDisable = new ArrayList<>();
                listAble = new ArrayList<>();
                this.svmRankParamC = svmRankParamC;
                this.svmRankLearnPath = svmRankLearnPath;
                this.svmRankClassifyPath = svmRankClassifyPath;
                this.rankLibMetric = rankLibMetric;
                this.rankLibModel = rankLibModel;
                this.toolkit = toolkit;

                if (featureDisableStr != null) {
                        String[] str = featureDisableStr.split(",");
                        for (int i = 0; i < str.length; i++) {
                                int curr = Integer.parseInt(str[i]);
                                listDisable.add(curr);
                        }
                }
                for (int feature = 1; feature <= 20; feature++) {
                        if (!listDisable.contains(feature)) {
                                listAble.add(feature);
                        }
                }
        }

        /**
         * Function to process trainingQrelsFile (relevance judgements).
         * @param trainingQrelsFileName
         * @return a map: key -> query id, value -> <docid, relevance>
         * @throws FileNotFoundException
         */
        public Map<Integer, Map<String, Integer>> processTrainingQrelsFile(String trainingQrelsFileName) throws FileNotFoundException {

                Map<Integer, Map<String, Integer>> trainingQrels = new LinkedHashMap<>();
                File trainingQrelsFile = new File (trainingQrelsFileName);

                if (! trainingQrelsFile.canRead ()) {
                        throw new IllegalArgumentException
                                ("Can't read " + trainingQrelsFileName);
                }

                //  Store (all) key/value parameters in a hashmap.
                Scanner scan = new Scanner(trainingQrelsFile);
                String line = null;
                do {
                        line = scan.nextLine();
                        String[] pair = line.split (" ");
                        int id = Integer.parseInt(pair[0]);
                        String externalId = pair[2];
                        int rel = Integer.parseInt(pair[3]);
                        Map<String, Integer> curr = new LinkedHashMap<>();

                        if (trainingQrels.containsKey(id)) {
                                curr = trainingQrels.get(id);
                        }
                        curr.put(externalId, rel);
                        trainingQrels.put(id, curr);
                } while (scan.hasNext());
                scan.close();
                return trainingQrels;
        }


        /**
         * Main process
         * @param parameters
         * @throws Exception
         */
        public void trainQuery(Map<String, String> parameters) throws Exception {
                // process trainingQrelsFile (relevance judgements)
                Map<Integer, Map<String, Integer>> trainingQrels = new LinkedHashMap<>();
                trainingQrels = processTrainingQrelsFile(trainingQrelsFile);
                int qid = -1;
                String query = null;

                // generate training data
                BufferedReader input = null;
                input = new BufferedReader(new FileReader(trainingQueryFile));
                boolean isSVMRank = false;
                boolean isRankLib = false;
                if (toolkit.equals("SVMRank")) {
                        isSVMRank = true;
                } else if (toolkit.equals("RankLib")) {
                        isRankLib = true;
                }

                String qLine = null;
                //  Each pass of the loop processes one query.
                while ((qLine = input.readLine()) != null) {
                        System.out.println("Query " + qLine);
                        String[] pair = qLine.split(":");

                        if (pair.length != 2) {
                                throw new IllegalArgumentException
                                        ("Syntax error:  Each line must contain one ':'.");
                        }
                        qid = Integer.parseInt(pair[0]);
                        query = pair[1];
                        // use QryParser.tokenizeString to stop & stem the query
                        String[] tokenizedString = QryParser.tokenizeString(query);

                        // for each document d in the relevance judgements for training query q,
                        // create an empty feature vector, read the PageRank and spam features from the index,
                        // fetch the term vector for d, calculate other features for <q, d>
                        Map<String, Integer> relJudgeMap = trainingQrels.get(qid);

                        Map<String, List<Double>> features = new HashMap<>();
                        // for later normalization used
                        Double[] maxFeatures = new Double[20];
                        Double[] minFeatures = new Double[20];
                        Arrays.fill(maxFeatures, Double.MIN_VALUE);
                        Arrays.fill(minFeatures, Double.MAX_VALUE);


                        List<String> allKeys = new ArrayList<>();
                        for (String key : relJudgeMap.keySet()) {
                                allKeys.add(key);
                        }

                        for (int i = 0; i < relJudgeMap.keySet().size(); i++) {
                                String externalId = allKeys.get(i);
                                int docid = Idx.getInternalDocid(externalId);
                                List<Double> featureVector = new ArrayList<>();
                                featureVector = combineFeatures(docid, tokenizedString);

                                // get the maximum and minimum values for feature
                                for (int cnt = 0; cnt < featureVector.size(); cnt++) {
                                        Double curr = featureVector.get(cnt);
                                        minFeatures[cnt] = Math.min(curr, minFeatures[cnt]);
                                        maxFeatures[cnt] = Math.max(curr, maxFeatures[cnt]);
                                }
                                features.put(externalId, featureVector);
                        }

                        if (isSVMRank) {
                                // // if toolkit is SVMrank, normalize the feature values for query q to [0..1]
                                // identify the maximum and minimum values for that feature,
                                // and then do standard [0..1] normalization
                                // If the min and max are the same value, set the feature value to 0.
                                SVMNormalization(maxFeatures, minFeatures, features);
                                // write the feature vectors to file
                                writeFeatureVectors(relJudgeMap, allKeys, qid, features, trainingFeatureVectorsFile);
                        } else if (isRankLib) {
                                writeFeatureVectors(relJudgeMap, allKeys, qid, features, trainingFeatureVectorsFile);
                        }
                }

                // commands for SVM and Ranklib
                String[] commandTrainSVM = {svmRankLearnPath, "-c", String.valueOf(svmRankParamC), trainingFeatureVectorsFile, modelFile};
                String[] commandTrainRankLib = {"-ranker", rankLibModel,
                                                "-train", trainingFeatureVectorsFile,
                                                "-save", modelFile};
                String[] commandTrainRankLibMetrics = {"-ranker", rankLibModel,
                                                        "-metric2t", rankLibMetric,
                                                        "-train", trainingFeatureVectorsFile,
                                                        "-save", modelFile};
                trainData(isSVMRank, isRankLib, commandTrainSVM, commandTrainRankLib, commandTrainRankLibMetrics);

                // Use BM25 to get initial rankings of length 100 for test queries

                input = new BufferedReader(new FileReader(queryFilePath));

                int queryNo = 0;
                //  Each pass of the loop processes one query.
                while ((qLine = input.readLine()) != null) {

                        System.out.println("Query " + qLine);
                        String[] pair = qLine.split(":");
                        qid = Integer.parseInt(pair[0]);
                        query = pair[1];

                        // use QryParser.tokenizeString to stop & stem the query
                        String[] tokenizedString = QryParser.tokenizeString(query);
                        ScoreList initialRanking = new ScoreList();
                        initialRanking = QryEval.processQuery(query, BM25);
                        ScoreList resList = new ScoreList();
                        int i = 0;
                        while (i < 100) {
                                resList.add(initialRanking.getDocid(i), initialRanking.getDocidScore(i));
                                i++;
                        }
                        Double[] maxFeatures = new Double[20];
                        Double[] minFeatures = new Double[20];
                        Arrays.fill(maxFeatures, -Double.MAX_VALUE);
                        Arrays.fill(minFeatures, Double.MAX_VALUE);

                        Map<String, List<Double>> features = new LinkedHashMap<>();

                        int j = 0;
                        while (j < 100) {
                                int docid = resList.getDocid(j);
                                List<Double> featureVector = new ArrayList<>();
                                featureVector = combineFeatures(docid, tokenizedString);

                                // get the maximum and minimum values for feature
                                for (int cnt = 0; cnt < featureVector.size(); cnt++) {
                                        Double curr = featureVector.get(cnt);
                                        minFeatures[cnt] = Math.min(curr, minFeatures[cnt]);
                                        maxFeatures[cnt] = Math.max(curr, maxFeatures[cnt]);
                                }
                                j++;
                                features.put(Idx.getExternalDocid(docid), featureVector);
                        }
                        if (isSVMRank) {
                                SVMNormalization(maxFeatures, minFeatures, features);
                        }

                        // write features to testingFeatureVectorsFile
                        writeTestFeatureVectors(qid, features, testingFeatureVectorsFile);

                        // commands for SVM and RankLib
                        String[] commandsSVM = {svmRankClassifyPath, testingFeatureVectorsFile, modelFile, testingDocumentScores};
                        String[] commandsRankLib = {"-rank", testingFeatureVectorsFile,
                                                    "-load", modelFile,
                                                    "-score", testingDocumentScores};
                        // re-rank test
                        rerankTest(isSVMRank, isRankLib, commandsSVM, commandsRankLib);
                        // read the new scores and use them to re-rank the initial ranking
                        resList = readNewScores(resList, isRankLib, testingDocumentScores, queryNo);
                        // sort the result
                        resList.sort();
                        // write the re-ranked result in trec_eval format
                        QryEval.printResults(parameters.get("trecEvalOutputLength"), parameters.get("trecEvalOutputPath"), qid + "", resList);
                        queryNo++;
                }
        }


        /**
         * Normalization.
         * Identify the maximum and minimum values for that feature,
         * and then do standard [0..1] normalization
         * If the min and max are the same value, set the feature value to 0.
         * @param maxFeatures
         * @param minFeatures
         * @param features
         */
        private void SVMNormalization(Double[] maxFeatures, Double[] minFeatures, Map<String, List<Double>> features) {
                for (String externalId: features.keySet()) {
                        List<Double> currF = new ArrayList<>();
                        currF = features.get(externalId);
                        for (int i = 0; i < currF.size(); i++) {
                                Double diff = maxFeatures[i] - minFeatures[i];
                                if (diff == 0.0) {
                                        currF.set(i, 0.0);
                                } else {
                                        double normalized = (currF.get(i) - minFeatures[i]) / diff;
                                        currF.set(i, normalized);
                                }
                        }
                }
        }

        /**
         * Write trained feature vectors to file.
         * @param relJudgeMap
         * @param qid
         * @param features
         * @throws FileNotFoundException
         */
        private void writeFeatureVectors(Map<String, Integer> relJudgeMap, List<String> allKeys, int qid, Map<String, List<Double>> features, String fileName) throws FileNotFoundException {
                PrintWriter pw = new PrintWriter(new FileOutputStream(new File(fileName), true));
                for (int i = 0; i < relJudgeMap.keySet().size(); i++) {
//                for (String externalId: features.keySet()) {
                        String externalId = allKeys.get(i);
                        int relJud = 0;
                        if (relJudgeMap != null) {
                                relJud = relJudgeMap.get(externalId);
                                if (relJud < 0) {
                                        relJud = 0;
                                }
                        }
                        List<Double> featureVectors = features.get(externalId);
                        StringBuilder sb = new StringBuilder();

                        for (int j = 0; j < featureVectors.size(); j++) {
                                if (featureVectors.get(j) != null) {
                                        // feature id should start with 1
                                        sb.append(listAble.get(j)).append(":").append(featureVectors.get(j)).append(" ");
                                }
                        }
                        // feature vector file output format: score, query id, feature id & feature value pair, external id
                        pw.format("%d qid:%d %s # %s\n", relJud, qid, sb, externalId);
                }
                pw.close();

        }


        /**
         * Write test features to file.
         * @param qid
         * @param features
         * @param fileName
         * @throws FileNotFoundException
         */
        private void writeTestFeatureVectors(int qid, Map<String, List<Double>> features, String fileName) throws FileNotFoundException {

                PrintWriter pw = new PrintWriter(new FileOutputStream(new File(fileName), true));
                for (String externalId: features.keySet()) {
                        List<Double> featureVectors = features.get(externalId);
                        StringBuilder sb = new StringBuilder();
                        for (int k = 0; k < featureVectors.size(); k++) {
                                if (featureVectors.get(k) != null) {
                                        // feature id should start with 1
                                        sb.append(listAble.get(k)).append(":").append(featureVectors.get(k)).append(" ");
                                }
                        }
                        // feature vector file output format: score, query id, feature id & feature value pair, external id
                        pw.format("%d qid:%d %s # %s\n", 0, qid, sb, externalId);
                }
                pw.close();
        }


        /**
         * Call train model.
         * @param isSVMRank if it is a SVMRank algorithm
         * @param isRankLib if it is a RankLib algorithm
         * @throws IOException
         * @throws InterruptedException
         */
        private void trainData(boolean isSVMRank, boolean isRankLib, String[] commandTrainSVM, String[] commandTrainRankLib, String[] commandTrainRankLibMetrics) throws IOException, InterruptedException {
                if (isSVMRank) {
                        runSVM(commandTrainSVM);
                } else if (isRankLib) {
                        if (rankLibMetric == null) {
                                ciir.umass.edu.eval.Evaluator.main (commandTrainRankLib);
                        } else {
                                ciir.umass.edu.eval.Evaluator.main (commandTrainRankLibMetrics);
                        }
                }
        }


        /**
         * General run SVM model.
         * @param commandSVM commands for SVM model
         * @throws IOException
         * @throws InterruptedException
         */
        private void runSVM(String[] commandSVM) throws IOException, InterruptedException {
                // call trained model
                // run command prompt commands from Java program
                Runtime rt = Runtime.getRuntime();
                Process proc = rt.exec(commandSVM);

                BufferedReader stdInput = new BufferedReader(new
                        InputStreamReader(proc.getInputStream()));

                BufferedReader stdError = new BufferedReader(new
                        InputStreamReader(proc.getErrorStream()));

                // Read the output from the command
//                                System.out.println("Here is the standard output of the command:\n");
                String s = null;
                while ((s = stdInput.readLine()) != null) {
                        System.out.println(s);
                }

                // Read any errors from the attempted command
                System.out.println("Here is the standard error of the command:\n");
                while ((s = stdError.readLine()) != null) {
                        System.out.println(s);
                }
                proc.waitFor();
                System.out.println("cmd finished");
        }


        /**
         * Rerank the test data.
         * @param isSVMRank if it is a SVMRank algorithm
         * @param isRankLib if it is a RankLib algorithm
         * @throws IOException
         * @throws InterruptedException
         */
        private void rerankTest(boolean isSVMRank, boolean isRankLib, String[] commandsSVM, String[] commandsRankLib) throws IOException, InterruptedException {
                // run command prompt commands from Java program
                if (isSVMRank) {
                        runSVM(commandsSVM);
                } else if (isRankLib) {
                        ciir.umass.edu.eval.Evaluator.main (commandsRankLib);
                }
        }


        /**
         * Read the new scores and use them to re-rank the initial ranking.
         * @param resList result score list
         * @param isRankLib whether it is a RankLib algorithm or not
         * @param filename testingDocumentScores
         * @param queryNo query number
         * @return result score list
         * @throws FileNotFoundException
         */
        private ScoreList readNewScores(ScoreList resList, boolean isRankLib, String filename, int queryNo) throws FileNotFoundException {
                File testResult = new File(filename);
                if (! testResult.canRead ()) {
                        throw new IllegalArgumentException
                                ("Can't read " + filename);
                }
                Scanner scan = new Scanner(testResult);
                String line = null;
                int index = 0;
                int total = 0;
                int start = 100 * queryNo;
                do {
                        line = scan.nextLine();
                        if (isRankLib) {
                                String[] words = line.split("\\s+");
                                line = words[2];
                        }
                        if (total < start) {
                                total++;
                        } else {
                                resList.setDocidScore(index, Double.parseDouble(line));
                                index++;
                                total++;
                        }
                } while (scan.hasNext() && index < 100);
                scan.close();
                return resList;
        }


        /**
         * feature 1: Spam score for d(read from index).
         * The score represents the document percentile in a spam quality ranking.
         * A document in the 1% percentile is probably spam.
         * A document in the 99% percentile is probably not spam.
         * @param docid document ID
         * @return spam score
         * @throws IOException
         */
        public int getSpamScore(int docid) throws IOException {
                return Integer.parseInt(Idx.getAttribute("spamScore", docid));
        }


        /**
         * feature 2: URL depth for d.
         * @param rawUrl raw URL
         * @return depth for d
         */
        public Double countUrlDepth(String rawUrl) {
                int res = 0;
                for (char c: rawUrl.toCharArray()) {
                        if (c == '/') {
                                res += 1;
                        }
                }
                return (double)(res);
        }


        /**
         * feature 3: FromWikipedia score.
         * @param rawUrl raw URL
         * @return score from wikipedia
         */
        public Double fromWikipediaScore(String rawUrl) {
                if (rawUrl.contains("wikipedia.org")) {
                        return 1.0;
                } else {
                        return 0.0;
                }
        }


        /**
         * feature 4: PageRank score for d (read from index).
         * @param docid document ID
         * @return score for PageRank
         * @throws IOException
         */
        public Double pageRankScore(int docid) throws IOException {
                float prScore = Float.parseFloat (Idx.getAttribute ("PageRank", docid));
                return (double)prScore;
        }


        /**
         * feature 5, 8, 11, 14: Calculate BM25 feature score.
         * @param field field
         * @param docid document ID
         * @param terms terms
         * @return feature score for BM25
         * @throws IOException
         */
        public Double BM25Feature(String field, int docid, String[] terms) throws IOException {

                double featureScore = 0.0;
                TermVector tv = new TermVector(docid, field);
                if (tv.positionsLength() == 0 || tv.stemsLength() == 0) {
                        return featureScore;
                }
                double k_1 = BM25.getK_1();
                double b = BM25.getB();
                long N = Idx.getNumDocs();
                long doclen = Idx.getFieldLength(field, docid);
                double avg_doclen = (double)Idx.getSumOfFieldLengths(field) / (double)Idx.getDocCount(field);

                for (int i = 0; i < terms.length; i++) {
                        int stemIndex = tv.indexOfStem(terms[i]);
                        if (stemIndex != -1) {
                                int df = tv.stemDf(stemIndex);
                                int tf = tv.stemFreq(stemIndex);
                                // calculate RSJ weight (idf)
                                double idf = Math.log(((double)N - (double)df + 0.5) / ((double)df + 0.5));
                                // calculate tf weight
                                double tfWeight = tf / (tf + k_1 * ((1 - b) + b * doclen / avg_doclen));
                                featureScore += (idf * tfWeight);
                        }
                }
                return featureScore;
        }


        /**
         * feature 6, 9, 12, 15: Calculate Indri feature score.
         * @param field field
         * @param docid document ID
         * @param terms terms
         * @return feature score for Indri
         * @throws IOException
         */
        public Double IndriFeature(String field, int docid, String[] terms) throws IOException {

                double featureScore = 1.0;
                double mu = indri.getMu();
                double lambda = indri.getLambda();
                double doclen = Idx.getFieldLength(field, docid);
                double lengthC = (double)Idx.getSumOfFieldLengths(field);
                TermVector tv = new TermVector(docid, field);

                if (tv.positionsLength() == 0 || tv.stemsLength() == 0) {
                        return 0.0;
                }

                boolean noMatchedTerms = true;
                for (int i = 0; i < terms.length; i++) {
                        if (tv.indexOfStem(terms[i]) == -1) {
                                continue;
                        } else {
                                noMatchedTerms = false;
                        }
                }
                // if there is no term matched, return 0
                if (noMatchedTerms) {
                        return 0.0;
                }

                for (int i = 0; i < terms.length; i++) {
                        int stemIndex = tv.indexOfStem(terms[i]);
                        double ctf = Idx.getTotalTermFreq(field, terms[i]);
                        double MLE = ctf / lengthC;
                        if (stemIndex != -1) {
                                int tf = tv.stemFreq(stemIndex);
                                // use two-stage smoothing to compute term weights
                                featureScore *= ((1.0 - lambda) * (tf + mu * MLE) / (mu + doclen) + lambda * MLE);
                        } else {
                                int tf = 0;
                                featureScore *= ((1.0 - lambda) * (tf + mu * MLE) / (mu + doclen) + lambda * MLE);
                        }
                }
                return Math.pow(featureScore, 1.0 / ((double)terms.length));
        }


        /**
         * feature 7, 10, 13, 16: Calculate Term Overlap (coordinate match) feature score.
         * Term overlap is defined as the count of query terms that match the document field.
         * @param field field
         * @param docid document id
         * @param terms terms
         * @return feature score for Term Overlap
         */
        public Double TermOverlapFeature(String field, int docid, String[] terms) throws IOException {
                double res = 0.0;
                TermVector tv = new TermVector(docid, field);
                if (tv.positionsLength() == 0 || tv.stemsLength() == 0) {
                        return 0.0;
                }
                for (String term: terms) {
                        if (tv.indexOfStem(term) != -1) {
                                res += 1;
                        }
                }
                return res;
        }


        /**
         * feature 17: Calculate Query Length.
         * @param terms terms
         * @return feature score for query length
         * @throws IOException
         */
        public Double queryLength(String[] terms) throws IOException {

                double featureScore = 0.0;
                if (terms.length != 0) {
                        featureScore = (double) terms.length;
                }
                return featureScore;
        }


        /**
         * feature 18: URL length for d.
         * @param rawUrl raw URL
         * @return length of URL
         */
        public Double urlLength(String rawUrl) {
                int res = 0;
                if (rawUrl.length() != 0) {
                        res = rawUrl.length();
                }
                return (double)(res);
        }


        /**
         * feature 19: Calculate RankedBoolean feature score
         * @param field field
         * @param docid document id
         * @param terms terms
         * @return RankedBoolean feature score
         * @throws IOException
         */
        public Double RankedBooleanFeature(String field, int docid, String[] terms) throws IOException {
                double featureScore = 0.0;
                TermVector tv = new TermVector(docid, field);
                if (tv.positionsLength() == 0 || tv.stemsLength() == 0) {
                        return featureScore;
                }
                for (int i = 0; i < terms.length; i++) {
                        int stemIndex = tv.indexOfStem(terms[i]);
                        if (stemIndex != -1) {
                                featureScore = (double)tv.stemFreq(stemIndex);
                        }
                }
                return featureScore;
        }


        /**
         * feature 20: Calculate number of inlinks
         * @param field field
         * @param docid document id
         * @param terms terms
         * @return number of inlinks feature score
         * @throws IOException
         */
        public Double numberInlinksFeature(String field, int docid, String[] terms) throws IOException {
                int cnt = 0;
                TermVector tv = new TermVector(docid, field);
                if (tv.positionsLength() == 0 || tv.stemsLength() == 0) {
                        return 0.0;
                }
                for (int i = 0; i < terms.length; i++) {
                        cnt = tv.positionsLength();
                }
                return (double)cnt;
        }


        /**
         * Combine all feature scores.
         * @param docid document ID
         * @param terms terms
         * @return all scores in a list
         * @throws IOException
         */
        private List<Double> combineFeatures(int docid, String[] terms) throws IOException {
                ArrayList<Double> features = new ArrayList<>();

                // feature 1: spam score
                if (!listDisable.contains(1)) {
                        double score = getSpamScore(docid);
                        features.add(score);
                }

                // feature 2: url depth
                if (!listDisable.contains(2)) {
                        double score = countUrlDepth(Idx.getAttribute("rawUrl", docid));
                        features.add(score);
                }

                // feature 3: wikipedia score
                if (!listDisable.contains(3)) {
                        double score = fromWikipediaScore(Idx.getAttribute("rawUrl", docid));
                        features.add(score);
                }

                // feature 4: pageRank score
                if (!listDisable.contains(4)) {
                        double score = pageRankScore(docid);
                        features.add(score);
                }

                // feature 5: BM25 score for <q, body>
                if (!listDisable.contains(5)) {
                        double score = BM25Feature("body", docid, terms);
                        features.add(score);
                }

                // feature 6: Indri score for <q, body>
                if (!listDisable.contains(6)) {
                        double score = IndriFeature("body", docid, terms);
                        features.add(score);
                }

                // feature 7: Term overlap score for <q, body>
                if (!listDisable.contains(7)) {
                        double score = TermOverlapFeature("body", docid, terms);
                        features.add(score);
                }

                // feature 8: BM25 score for <q, title>
                if (!listDisable.contains(8)) {
                        double score = BM25Feature("title", docid, terms);
                        features.add(score);
                }

                // feature 9: Indri score for <q, title>
                if (!listDisable.contains(9)) {
                        double score = IndriFeature("title", docid, terms);
                        features.add(score);
                }

                // feature 10: Term overlap score for <q, title>
                if (!listDisable.contains(10)) {
                        double score = TermOverlapFeature("title", docid, terms);
                        features.add(score);
                }

                // feature 11: BM25 score for <q, url>
                if (!listDisable.contains(11)) {
                        double score = BM25Feature("url", docid, terms);
                        features.add(score);
                }

                // feature 12: Indri score for <q, url>
                if (!listDisable.contains(12)) {
                        double score = IndriFeature("url", docid, terms);
                        features.add(score);
                }

                // feature 13: Term overlap score for <q, url>
                if (!listDisable.contains(13)) {
                        double score = TermOverlapFeature("url", docid, terms);
                        features.add(score);
                }

                // feature 14: BM25 score for <q, inlink>
                if (!listDisable.contains(14)) {
                        double score = BM25Feature("inlink", docid, terms);
                        features.add(score);
                }

                // feature 15: Indri score for <q, inlink>
                if (!listDisable.contains(15)) {
                        double score = IndriFeature("inlink", docid, terms);
                        features.add(score);
                }

                // feature 16: Term overlap score for <q, inlink>
                if (!listDisable.contains(16)) {
                        double score = TermOverlapFeature("inlink", docid, terms);
                        features.add(score);
                }

                // feature 17: Query Length
                if (!listDisable.contains(17)) {
                        double score = queryLength(terms);
                        features.add(score);
                }

                // feature 18: url Length
                if (!listDisable.contains(18)) {
                        double score = urlLength(Idx.getAttribute("rawUrl", docid));
                        features.add(score);
                }


                // feature 19: RankedBoolean score for <q, body>
                if (!listDisable.contains(19)) {
                        double score = RankedBooleanFeature("body", docid, terms);
                        features.add(score);
                }

                // feature 20: Number of inlinks score for <q, inlink>
                if (!listDisable.contains(20)) {
                        double score = numberInlinksFeature("inlink", docid, terms);
                        features.add(score);
                }

                return features;
        }

}
