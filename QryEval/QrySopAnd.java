import java.io.IOException;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {
        /**
         *  Indicates whether the query has a match.
         *  @param r The retrieval model that determines what is a match
         *  @return True if the query matches, otherwise false.
         */
        public boolean docIteratorHasMatch (RetrievalModel r) {
                if (r instanceof RetrievalModelIndri) {
                        return this.docIteratorHasMatchMin(r);
                }
                return this.docIteratorHasMatchAll (r);
        }

        /**
         *  Get a score for the document that docIteratorHasMatch matched.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        public double getScore (RetrievalModel r) throws IOException {

                if (r instanceof RetrievalModelUnrankedBoolean) {
                        return this.getScoreUnrankedBoolean (r);
                } else if (r instanceof RetrievalModelRankedBoolean) {
                        return this.getScoreRankedBoolean(r);
                } else if (r instanceof RetrievalModelBM25) {
                        return this.getScoreBM25(r);
                } else if (r instanceof RetrievalModelIndri) {
                        return this.getScoreIndri(r);
                } else {
                        throw new IllegalArgumentException
                                (r.getClass().getName() + " doesn't support the AND operator.");
                }
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
                double score = 1.0;
                for (int i = 0; i < this.args.size(); i++) {
                        QrySop q_i = (QrySop) this.args.get(i);
                        score *= Math.pow(q_i.getDefaultScore(r, docid), 1.0 / this.args.size());
                }
                return score;
        }

        /**
         *  getScore for the UnrankedBoolean retrieval model.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
                //  Unranked Boolean systems only have two scores:
                //  1 (document matches) and 0 (document doesn't match).  QryEval
                //  only calls getScore for documents that match, so if we get
                //  here, the document matches, and its score should be 1.  The
                //  most efficient implementation returns 1 from here.
                //
                //  Other retrieval models must do more work.  To help students
                //  understand how to implement other retrieval models, this
                //  method uses a more general solution.  OR takes the maximum
                //  of the scores from its children query nodes.

                return 1.0;
        }

        /**
         *  getScore for the RankedBoolean retrieval model.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        private double getScoreRankedBoolean (RetrievalModel r) throws IOException {

                double score = Double.MAX_VALUE;
                int docid = this.docIteratorGetMatch ();

                for (int i = 0; i < this.args.size(); i++) {

                        //  Java knows that the i'th query argument is a Qry object, but
                        //  it does not know what type.  We know that OR operators can
                        //  only have QrySop objects as children.  Cast the i'th query
                        //  argument to QrySop so that we can call its getScore method.

                        QrySop q_i = (QrySop) this.args.get(i);

                        //  If the i'th query argument matches this document, update the
                        //  score.
                        System.out.println(q_i.getScore (r));

                        if (q_i.docIteratorHasMatch (r) &&
                                (q_i.docIteratorGetMatch () == docid)) {
                                score = Math.min (score, q_i.getScore (r));
                        }
                }
                return score;
        }

        /**
         *  getScore for the BM25 retrieval model.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        private double getScoreBM25 (RetrievalModel r) throws IOException {
                return 0.0;
        }

        /**
         *  getScore for the Indri retrieval model.
         *  Indri AND operator uses the geometric mean.
         *  @param r The retrieval model that determines how scores are calculated.
         *  @return The document score.
         *  @throws IOException Error accessing the Lucene index
         */
        private double getScoreIndri (RetrievalModel r) throws IOException {
                double score = 0.0;
                if (this.docIteratorHasMatchCache()) {
                        double geometricMean = 1.0;
                        int docid = this.docIteratorGetMatch ();
                        for (int i = 0; i < this.args.size(); i++) {

                                QrySop q_i = (QrySop) this.args.get(i);

                                //  If the i'th query argument matches this document, update the
                                //  score.
                                if (q_i.docIteratorHasMatch (r) &&
                                        (q_i.docIteratorGetMatch () == docid)) {
                                        geometricMean *= Math.pow(q_i.getScore(r), 1.0 / this.args.size());
                                } else {
                                        geometricMean *= Math.pow(q_i.getDefaultScore(r, docid), 1.0 / this.args.size());
                                }
                        }
                        score = geometricMean;
                }
                return score;
        }

}
