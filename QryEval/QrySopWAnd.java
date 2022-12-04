import java.io.IOException;
import java.util.ArrayList;

/**
 *  The WAND operator for Indri retrieval model.
 */
public class QrySopWAnd extends QrySop {

        public double sumWeight = 0.0;
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
         *  Indicates whether the query has a match.
         *  @param r The retrieval model that determines what is a match
         *  @return True if the query matches, otherwise false.
         */
        @Override
        public boolean docIteratorHasMatch(RetrievalModel r) {

                if (r instanceof RetrievalModelIndri) {
                        return this.docIteratorHasMatchMin (r);
                }
                return this.docIteratorHasMatchAll(r);

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
         *  Get a score for the document that docIteratorHasMatch matched.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        @Override
        public double getScore(RetrievalModel r) throws IOException {
                if (r instanceof RetrievalModelIndri) {
                        return getScoreIndri(r);
                } else {
                        return 0.0;
                }
        }

        /**
         *  getScore for the Indri retrieval model.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        private double getScoreIndri (RetrievalModel r) throws IOException {
                double score = 0.0;
                if (this.docIteratorHasMatchCache()) {
                        double geometricMean = 1.0;
                        double sumWeight = this.sumWeight;
                        int docid = this.docIteratorGetMatch ();
                        for (int i = 0; i < this.args.size(); i++) {

                                double qi_weight = getWeightAtIndex(i);

                                QrySop q_i = (QrySop) this.args.get(i);

                                //  If the i'th query argument matches this document, update the
                                //  score.
                                if (q_i.docIteratorHasMatch (r) &&
                                        (q_i.docIteratorGetMatch () == docid)) {
                                        geometricMean *= Math.pow(q_i.getScore(r), qi_weight / sumWeight);

                                } else {
                                        geometricMean *= Math.pow(q_i.getDefaultScore(r, docid), qi_weight / sumWeight);
                                }
                        }
                        score = geometricMean;
                }
                return score;
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
                double geometricMean = 1.0;
                double sumWeight = this.sumWeight;
                for (int i = 0; i < this.args.size(); i++) {
                        double qi_weight = getWeightAtIndex(i);
                        QrySop q_i = (QrySop) this.args.get(i);
                        geometricMean *= Math.pow(q_i.getDefaultScore(r, docid), qi_weight / sumWeight);
                }
                return geometricMean;
        }
}
