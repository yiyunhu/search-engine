import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Diversification {

        public class Algorithm {
                private int docid;
                private double score;

                // constructor for class Algorithm
                public Algorithm(int docid, double score) {
                        this.docid = docid;
                        this.score = score;
                }
        }

        /**
         * PM-2 or xQuAD algorithm.
         */
        private String algorithm;
        /**
         * the maximum number of documents in the relevance ranking.
         * and the intent rankings that your software should use for diversification.
         */
        private int maxInputRankingsLength;
        /**
         * the number of documents in the diversified ranking that your software will produce.
         */
        private int maxResultRankingLength;
        /**
         * the number of intent.
         */
        private static int numOfIntent;
        /**
         * a list of query intents.
         */
        private static List<String> queryIntent;
        /**
         * Balance between relevance and diversity.
         */
        private double lambda;

        // constructor
        public Diversification(String algorithm, int maxInputRankingsLength, int maxResultRankingLength, double lambda) {
                this.algorithm = algorithm;
                this.maxInputRankingsLength = maxInputRankingsLength;
                this.maxResultRankingLength = maxResultRankingLength;
                this.lambda = lambda;
        }


        /**
         * Produce Diversified Ranking.
         * @param documentRanking
         * @return a scorelist of diversified ranking result
         */
        public ScoreList produceDiversifiedRanking(List<Map<Integer, Double>> documentRanking) {
                ScoreList s = new ScoreList();
                Set<Integer> initialDocSet = new HashSet<>();
                initialDocSet = documentRanking.get(0).keySet();
                int size = documentRanking.size();
                for (int i = 1; i < size; i++) {
                        Map<Integer, Double> curr = new LinkedHashMap<>();
                        curr = documentRanking.get(i);
                        Set<Integer> removeDoc = new HashSet<>();
                        for (Integer id: documentRanking.get(i).keySet()) {
                                // remove documents that intents are not in initial rankings
                                if (initialDocSet.contains(id)) {
                                        continue;
                                } else {
                                        removeDoc.add(id);
                                }
                        }
                        for (Integer id: removeDoc) {
                                curr.remove(id);
                        }
                }

                // Scaling Document Scores
                // calculating the sum of document scores in the ranking,
                // and then dividing scores by that sum of scores value.
                // However, when diversifying the results for query q
                // with intents qi, all of the rankings should be normalized
                // with the same value, so that document scores remain
                // comparable after scaling. Thus, for query q, calculate
                // the sum of scores value for each ranking independently,
                // and then use the maximum value to normalize all of the rankings.
                boolean scale = false;
                double maxValue = -1.0;
                for (Map<Integer, Double> curr: documentRanking) {
                        double currSum = 0.0;
                        for (Integer id: curr.keySet()) {
                                if (curr.get(id) > 1.0) {
                                        scale = true;
                                }
                                currSum += curr.get(id);
                                if (currSum <= 1.0) {
                                        continue;
                                } else {
                                        scale = true;
                                }
                        }
                        maxValue = Math.max(maxValue, currSum);
                }
                if (scale) {
                        for (Map<Integer, Double> curr: documentRanking) {
                                for (Integer id: curr.keySet()) {
                                        curr.put(id, curr.get(id) / maxValue);
                                }
                        }
                }

                // get initial ranking
                Map<Integer, Double> initialRanking = documentRanking.get(0);
                // two choices of algorithms
                if (algorithm.equals("xQuAD")) {
                        s = xQuADMethod(documentRanking, initialRanking, maxResultRankingLength, lambda, numOfIntent);
                } else if (algorithm.equals("PM2")) {
                        s = PM2Method(documentRanking, initialRanking, maxResultRankingLength, lambda, numOfIntent);
                }
                return s;
        }


        /**
         * Explicit Query Aspect Diversification.
         * @param documentRanking a set of query intents
         * @param initialRanking initial ranking
         * @return a scorelist
         */
        private ScoreList xQuADMethod(List<Map<Integer, Double>> documentRanking, Map<Integer, Double> initialRanking, int maxResultRankingLength, double lambda, int numOfIntent) {
                ScoreList s = new ScoreList();
                // component: intent weight, score for d for intent qi, how well S already covers intent qi
                // while size < desired length of diversified ranking...
                while (s.size() < maxResultRankingLength) {
                        Algorithm ad = new Algorithm(-1, -1.0);
                        // intent weight
                        double intentWeight = 1.0 / (double) numOfIntent;

                        for (Integer id: initialRanking.keySet()) {
                                double relevance = (1.0 - lambda) * initialRanking.get(id);
                                double diversity = 0.0;
                                for (int i = 1; i <= numOfIntent; i++) {
                                        Map<Integer, Double> currMap = documentRanking.get(i);

                                        double newScore = 0.0;

                                        if (currMap.get(id) == null) {
                                                newScore = 0.0;
                                        } else {
                                                newScore = currMap.get(id);
                                        }

                                        for (int j = 0; j < s.size(); j++) {
                                                int did = s.getDocid(j);
                                                if (currMap.get(did) != null) {
                                                        newScore *= 1.0 - currMap.get(did);
                                                } else {
                                                        newScore *= 1.0;
                                                }
                                        }
                                        diversity += newScore;
                                }
                                // scale score
                                diversity = diversity * lambda * intentWeight;
                                if ((relevance + diversity) > ad.score) {
                                        ad.score = relevance + diversity;
                                        ad.docid = id;
                                }
                        }
                        initialRanking.remove(ad.docid);
                        s.add(ad.docid, ad.score);
                }
                return s;
        }


        /**
         * Proportionality Model 2.
         * @param documentRanking a set of query intents
         * @param initialRanking initial ranking
         * @return a scorelist
         */
        private ScoreList PM2Method(List<Map<Integer, Double>> documentRanking, Map<Integer, Double> initialRanking, int maxResultRankingLength, double lambda, int numOfIntent) {
                ScoreList PM2Scores = new ScoreList();
                // desired ranks for qi = diversified ranking size / number of intents
                double desiredRanks = (double) maxResultRankingLength  / numOfIntent;
                // quotient scores
                double qt[] = new double[numOfIntent];
                // slots assigned
                double s[] = new double[numOfIntent];
                while (PM2Scores.size() < maxResultRankingLength) {
                        // select the query intent qi that must be covered next to
                        // maintain proportional coverage of intents in the ranking
                        Algorithm ad = new Algorithm(-1, -1.0);
                        int cnt = 0;
                        while (cnt < numOfIntent) {
                                // priority of each intent now
                                qt[cnt] = desiredRanks / (2.0 * s[cnt] + 1.0);
                                cnt++;
                        }
                        // get next intent
                        double updateScore = 0.0;
                        int nextIntent = -1;
                        for (int i = 0; i < numOfIntent; i++) {
                                double currRank = qt[i];
                                if (currRank > updateScore) {
                                        updateScore = currRank;
                                        nextIntent = i;
                                }
                        }
                        // select a document d that covers intent qi
                        for (Integer id: initialRanking.keySet()) {
                                double score = 0.0;
                                double coversQi = 0.0;
                                double coversOther = 0.0;
                                // calculate covers qi
                                if (documentRanking.get(nextIntent + 1).get(id) != null) {
                                        coversQi = lambda * qt[nextIntent] * documentRanking.get(nextIntent + 1).get(id);
                                }
                                // calculate covers other intents
                                for (int i = 0; i < numOfIntent; i++) {
                                        if (i == nextIntent) {
                                                continue;
                                        } else {
                                                if (documentRanking.get(i + 1).get(id) != null) {
                                                        coversOther += qt[i] * documentRanking.get(i + 1).get(id);
                                                }
                                        }
                                }
                                score = coversQi + (1 - lambda) * coversOther;
                                if (score > ad.score) {
                                        ad.score = score;
                                        ad.docid = id;
                                }
                        }
                        // remove d from the initial ranking R
                        initialRanking.remove(ad.docid);
                        // assign d to the diversified ranking S
                        PM2Scores.add(ad.docid, ad.score);

                        // update coverage of each intent
                        int total = 0;
                        while (total < numOfIntent) {
                                double updateCoverage = 0.0;
                                double sum  = 0.0;
                                boolean noUpdate = false;
                                if (documentRanking.get(total + 1).get(ad.docid) == null) {
                                        noUpdate = true;
                                }
                                if (!noUpdate) {
                                        if (documentRanking.get(total + 1).get(ad.docid) != null) {
                                                for (int i = 0; i < numOfIntent; i++) {
                                                        if (documentRanking.get(i + 1).get(ad.docid) != null) {
                                                                sum += documentRanking.get(i + 1).get(ad.docid);
                                                        } else {
                                                                sum += 0.0;
                                                        }
                                                }
                                        }
                                        updateCoverage = documentRanking.get(total + 1).get(ad.docid) / sum;
                                        s[total] += updateCoverage;
                                        total++;
                                } else {
                                        total++;
                                }
                        }
                }
                return PM2Scores;
        }


        /**
         * Process query and get back scores.
         * @param allIntents intents
         * @param model
         * @return list of map with document score
         * @throws IOException
         */
        public static List<Map<Integer, Double>> processQuery(ScoreList s, List<String> allIntents, RetrievalModel model, int maxInputRankingsLength) throws IOException {
                // get scorelist
                int intentSize = allIntents.size();
                Map<Integer, Double> map = new ConcurrentHashMap<>();

                int idx = 0;
                int requiredSize = Math.min(s.size(), maxInputRankingsLength);
                List<Map<Integer, Double>> documentRanking = new ArrayList<>();
                while (idx < requiredSize) {
                        map.put(s.getDocid(idx), s.getDocidScore(idx));
                        idx += 1;
                }
                documentRanking.add(map);

                int cnt = 0;
                while (cnt < intentSize) {
                        String currQuery = allIntents.get(cnt);
                        ScoreList s1 = QryEval.processQuery(currQuery, model);
                        Map<Integer, Double> currIntentMap = new ConcurrentHashMap<>();
                        for (int i = 0; i < requiredSize; i++) {
                                currIntentMap.put(s1.getDocid(i), s1.getDocidScore(i));
                        }
                        documentRanking.add(currIntentMap);
                        cnt++;
                }
                return documentRanking;
        }


        /**
         * Read all intents from file.
         * @param filename Intents file name
         * @param id current query ID
         * @return all intents if query id match
         * @throws FileNotFoundException
         */
        public static List<String> readIntents(String filename, int id) throws FileNotFoundException {
                List<String> intents = new ArrayList<>();
                File intentsFile = new File (filename);

                if (! intentsFile.canRead ()) {
                        throw new IllegalArgumentException
                                ("Can't read " + filename);
                }

                Scanner scan = new Scanner(intentsFile);
                String line = null;
                do {
                        line = scan.nextLine();
                        String[] pair = line.split (":");
                        String[] multipleIntents = pair[0].split("\\.");
                        int getID = Integer.parseInt(multipleIntents[0]);
                        String query = pair[1];
                        if (id == getID) {
                                intents.add(query);
                        }
                } while (scan.hasNext());
                scan.close();

                // update number of intents and queryIntent
                numOfIntent = intents.size();
                queryIntent = intents;

                return intents;
        }

}
