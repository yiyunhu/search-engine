import java.io.IOException;

/**
 *  The SUM operator for all retrieval models.
 */
public class QrySopSum extends QrySop {

        /**
         *  Indicates whether the query has a match.
         *  @param r The retrieval model that determines what is a match
         *  @return True if the query matches, otherwise false.
         */
        @Override
        public boolean docIteratorHasMatch(RetrievalModel r) {
                return this.docIteratorHasMatchMin(r);
        }

        /**
         *  Get a score for the document that docIteratorHasMatch matched.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        @Override
        public double getScore(RetrievalModel r) throws IOException {
                if (r instanceof RetrievalModelBM25) {
                        return this.getScoreBM25 (r);
                } else {
                        throw new IllegalArgumentException
                                (r.getClass().getName() + " doesn't support the SUM operator.");
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
                if (this.docIteratorHasMatchCache()) {
                        int id = this.docIteratorGetMatch();
                        for (Qry q: args) {
                                if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == id) {
                                        double score = ((QrySop)q).getScore(r);
                                        sum += score;
                                }
                        }
                }
                return sum;
        }

        /**
         * If q_i has no match for document d, we will call getDefaultScore.
         * @param r The retrieval model that determines how scores are calculated.
         * @param docid document ID.
         * @return The document score.
         * @throws IOException Error accessing the Lucene index
         */
        @Override
        public double getDefaultScore (RetrievalModel r, long docid) throws IOException {
                double mu = ((RetrievalModelIndri)r).getMu();
                double lambda = ((RetrievalModelIndri)r).getLambda();
                QryIop q = (this.getArg(0));
                double doclen = (double)Idx.getFieldLength(q.getField(), q.docIteratorGetMatch());

                // get MLE
                double ctf_qi = q.invertedList.ctf;
                double lengthC = (double)Idx.getSumOfFieldLengths(q.getField());
                double MLE = ctf_qi / lengthC;

                // use two-stage smoothing to compute term weights
                double score = (1.0 - lambda) * (mu * MLE) / (mu + doclen) + lambda * MLE;
                return score;

        }
}
