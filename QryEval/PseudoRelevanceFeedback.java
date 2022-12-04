import org.apache.lucene.util.ToStringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.nio.charset.Charset;


/**
 * Pseudo Relevance Feedback Class.
 * Indri Relevance Feedback
 */
public class PseudoRelevanceFeedback {

        /**
         * Indri Relevance Feedback parameters.
         * The number of judged documents.
         */
        private int fbdocs;
        /**
         * The number of terms to add to the query.
         */
        private int fbterms;
        /**
         * The smoothing weight to use for new terms.
         */
        private double fbmu;
        /**
         * The amount of weight to place on the original query.
         */
        private double fbweight;

        /**
         * Constructor.
         * @param fbdocs
         * @param fbterms
         * @param fbmu
         * @param fbweight
         */
        public PseudoRelevanceFeedback(int fbdocs, int fbterms, double fbmu, double fbweight) {
                this.fbdocs = fbdocs;
                this.fbterms = fbterms;
                this.fbmu = fbmu;
                this.fbweight = fbweight;
        }

        public class Term implements Comparable<Term> {
                private String term;
                private double score;

                public Term(String term, double score) {
                        this.term = term;
                        this.score = score;
                }

                public String getTerm() {
                        return term;
                }

                public double getScore() {
                        return score;
                }

                public int compareTo(Term t) {
                        if (score < t.getScore()) {
                                return -1;
                        } else if (score > t.getScore()) {
                                return 1;
                        } else {
                                return 0;
                        }
                }
        }

        /**
         * Scoring p(t|d)
         * @param tv
         * @param docid
         * @param termCtf
         * @param termLen
         * @param term
         * @return score for each potential expansion term
         * @throws IOException
         */
        private double getTermScore(TermVector tv, int docid, double termCtf, double termLen, String term) throws IOException {
                double score = 0.0;
                double pMLE = 0.0;
                double tf = 0.0;
                int termIndex = tv.indexOfStem(term);
                if (termIndex >= 0) {
                        tf = tv.stemFreq(termIndex);
                }
                double docLen = Idx.getFieldLength("body", docid);

                if (fbmu != 0.0) {
                        pMLE = termCtf / termLen;
                }
                if (tf == 0.0 && fbmu == 0.0) {
                        return 0.0;
                } else {
                        score = (tf + fbmu * pMLE) / (fbmu + docLen);
                }
                return score;
        }


        /**
         * Valid whether each character in a string is an ASCII character or not.
         * @param v string needed to be inspected
         * @return true if every character is an ASCII character
         */
        public static boolean isPureAscii(String v) {
                return Charset.forName("US-ASCII").newEncoder().canEncode(v);
        }


        /**
         * Use the top terms to create an expansion query Q(learned).
         * @param r original scoreList
         * @return an expansion query
         * @throws IOException
         */
        public String createLearnedQuery(ScoreList r) throws IOException {
                int docSize = Math.min(fbdocs, r.size());
                // store docid and corresponding index
                Map<Integer, Integer> docidIdxMap = new HashMap<>(docSize);
                for (int i = 0; i < docSize; i++) {
                        docidIdxMap.put(r.getDocid(i), i);
                }

                // store document structure by using forward index: get structure by docid
                List<TermVector> forwardList = new ArrayList<>();
                // a set contains all document id
                Set<Integer> docidSet = new HashSet<>();
                docidSet = docidIdxMap.keySet();
                for (Integer i: docidSet) {
                        TermVector tv = new TermVector(i, "body");
                        forwardList.add(tv);
                }

                // a set to collect all terms
                Set<String> allTerms = new HashSet<>();
                // map to store ctf with term
                Map<String, Double> ctfMap = new HashMap<>();
                // put terms and ctf into map
                for (TermVector tv: forwardList) {
                        for (int i = 1; i < tv.stemsLength(); i++) {
                                String currentTerm = tv.stemString(i);
                                // ignore any candidate expansion term that contains a period, a comma, or a non-ASCII term
                                if (!currentTerm.contains(".") && !currentTerm.contains(",") && isPureAscii(currentTerm)) {
                                        allTerms.add(currentTerm);
                                        if (!ctfMap.containsKey(currentTerm)) {
                                                ctfMap.put(currentTerm, (double) tv.totalStemFreq(i));
                                        }
                                }
                        }
                }

                PriorityQueue<Term> pqTerms = calculateScore(allTerms, ctfMap, forwardList, docidIdxMap, r);

                // create printout query
                DecimalFormat df = new DecimalFormat("#.####");
                StringBuilder sb = new StringBuilder();
                for (Term t: pqTerms) {
                        String currentTerm = df.format(t.getScore()) + " " + t.getTerm();
                        if (sb.toString() != "") {
                                sb.append(" ").append(currentTerm);
                        } else {
                                sb.append(currentTerm);
                        }
                }
                String output = "#wand (" + sb.toString() + ")";
                return output;

        }


        /**
         * Calculate potential expansion terms.
         * @param allTerms
         * @param ctfMap
         * @param forwardList
         * @param docidIdxMap
         * @param r Scorelist
         * @return score
         * @throws IOException
         */
        private PriorityQueue<Term>  calculateScore(Set<String> allTerms, Map<String, Double> ctfMap, List<TermVector> forwardList, Map<Integer, Integer> docidIdxMap, ScoreList r) throws IOException {
                int maxSize = fbterms;
                PriorityQueue<Term> pqTerms = new PriorityQueue<>(maxSize);
//                PriorityQueue<Term> pqTerms = new PriorityQueue<>();
                for (String term: allTerms) {
                        // weight = term length / ctf
                        double weight = Math.log(Idx.getSumOfFieldLengths("body") / ctfMap.get(term));
                        double ctf = ctfMap.get(term);
                        double termLen = Idx.getSumOfFieldLengths("body");

                        double score = 0.0;

                        for (TermVector tv: forwardList) {
                                double termScore = getTermScore(tv, tv.docId, ctf, termLen, term);
                                int docIdx = docidIdxMap.get(tv.docId);
                                double originalScore = r.getDocidScore(docIdx);
                                score += (termScore * originalScore * weight);
                        }
                        Term t = new Term(term, score);
                        pqTerms.add(t);
                        if (pqTerms.size() > maxSize) {
                                pqTerms.poll();
                        }
                }
                return pqTerms;
        }


        /**
         * The expanded query is
         * Q(expanded) = #wand (w Q(original) (1-w) Q(learned) )
         * @param originalQuery
         * @param learnedQuery
         * @return final expanded query
         */
        public String createExpandedQuery(String originalQuery, String learnedQuery) {
                return "#wand (" + fbweight + " " + originalQuery + " " + (1.0 - fbweight) + " " + learnedQuery + ")";

        }


        /**
         * Print the query results.
         * @param expansionQueryFile
         * @param expandQuery
         * @param qid
         * @throws IOException
         */
        public void printResults(String name, String expansionQueryFile, String expandQuery, int qid) throws IOException {

                if (name != null) {
                        String[] files = expansionQueryFile.split("\\.");
                        expansionQueryFile = files[0]  + "." + files[1];
                }

                PrintWriter pw = new PrintWriter(new FileOutputStream(new File(expansionQueryFile), true));

                pw.format("%s: %s\n", qid, expandQuery);

                // pad 0 if id is a two-digit integer
//                if (qid < 100) {
//                        String padded = "";
//                        padded = String.format("%03d", qid);
//                        pw.format("%s: %s\n", padded, expandQuery);
//                } else {
//                        pw.format("%s: %s\n", qid, expandQuery);
//                }
                pw.close();
        }
}
