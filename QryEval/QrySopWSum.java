import java.io.IOException;
import java.util.ArrayList;

/**
 *  The WSUM operator for Indri retrieval model.
 */
public class QrySopWSum extends  QrySop {

        public double sumWeight = 0;
        public ArrayList<Double> weights = new ArrayList<>();

        /**
         * Getter the weight of a specific index
         * @param index
         * @return weight of an index
         */
        public double getWeightAtIndex(int index) {
                return weights.get(index);
        }

        /**
         * add weight.
         * @param weight
         */
        public void addWeight(Double weight) {
                this.weights.add(weight);
                sumWeight = sumWeight + weight;
        }

        /**
         *  Indicates whether the query has a match.
         *  @param r The retrieval model that determines what is a match
         *  @return True if the query matches, otherwise false.
         */
        public boolean docIteratorHasMatch (RetrievalModel r) {

                return this.docIteratorHasMatchMin (r);
        }

        /**
         *  Get a score for the document that docIteratorHasMatch matched.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        public double getScore (RetrievalModel r) throws IOException {

                if (r instanceof RetrievalModelBM25) {
                        return this.getScoreBM25(r);
                } else if (r instanceof RetrievalModelIndri) {
                        return this.getScoreIndri(r);
                } else {
                        throw new IllegalArgumentException
                                (r.getClass().getName() + " doesn't support the AND operator.");
                }
        }

        /**
         *  getScore for the BM25 retrieval model.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        private double getScoreBM25 (RetrievalModel r) throws IOException {
                double sum = 0.0;
                sumWeight = this.sumWeight;
                if (this.docIteratorHasMatchCache()) {
                        int id = this.docIteratorGetMatch();
                        for (int i = 0; i < this.args.size(); i++) {
                                QrySop q_i = (QrySop) this.args.get(i);
                                if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == id) {
                                        double k_3 = ((RetrievalModelBM25)r).getK_3();
                                        double qtf = this.weights.get(i);
                                        double userWeight = (k_3 + 1) * qtf / (k_3 + qtf);
                                        double score = q_i.getScore(r) * userWeight;
                                        sum += score;
                                }
                        }
                }
                return sum / sumWeight;

        }

        /**
         *  getScore for the Indri retrieval model.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        private double getScoreIndri (RetrievalModel r) throws IOException {
                double sum = 0.0;
                if (this.docIteratorHasMatchCache()) {
                        double sumWeight = this.sumWeight;
                        int docid = this.docIteratorGetMatch ();
                        for (int i = 0; i < this.args.size(); i++) {

                                double qi_weight = getWeightAtIndex(i);

                                QrySop q_i = (QrySop) this.args.get(i);

                                //  If the i'th query argument matches this document, update the
                                //  score.
                                if (q_i.docIteratorHasMatch (r) &&
                                        (q_i.docIteratorGetMatch () == docid)) {
                                        sum += qi_weight * q_i.getScore(r);
                                } else {
                                        sum += qi_weight * q_i.getDefaultScore(r, docid);
                                }
                        }
                }
                return sum / sumWeight;

        }

        /**
         * If q_i has no match for document d, we will call getDefaultScore.
         * @param r The retrieval model that determines how scores are calculated.
         * @param docid document ID.
         * @return The document score.
         * @throws IOException Error accessing the Lucene index
         */
        @Override
        public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
                double sum = 0;
                for (int i = 0; i < this.args.size(); i++) {
                        QrySop q_i = (QrySop) this.args.get(i);
                        double qi_weight = getWeightAtIndex(i);
                        sum += qi_weight * q_i.getDefaultScore(r, docid);
                }
                return sum;
        }
}
