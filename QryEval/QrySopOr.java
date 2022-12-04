/**
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

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

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return this.getScoreRankedBoolean(r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
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
    double score = 0.0;
    double geometricMean = 1.0;
    for (int i = 0; i < this.args.size(); i++) {
      QrySop q_i = (QrySop) this.args.get(i);
      geometricMean *= 1.0 - q_i.getDefaultScore(r, docid);
    }
    score = 1.0 - geometricMean;
    return score;
  }

  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (this.docIteratorHasMatchCache()) {
      return 1.0;
    } else {
      return 0.0;
    }
  }

  /**
   *  getScore for the RankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {

    double score = 0.0;
    int docid = this.docIteratorGetMatch ();

    for (int i=0; i<this.args.size(); i++) {

      //  Java knows that the i'th query argument is a Qry object, but
      //  it does not know what type.  We know that OR operators can
      //  only have QrySop objects as children.  Cast the i'th query
      //  argument to QrySop so that we can call its getScore method.

      QrySop q_i = (QrySop) this.args.get(i);

      //  If the i'th query argument matches this document, update the
      //  score.

      if (q_i.docIteratorHasMatch (r) &&
              (q_i.docIteratorGetMatch () == docid)) {
        score = Math.max (score, q_i.getScore (r));
      }
    }

    return score;
  }

}
