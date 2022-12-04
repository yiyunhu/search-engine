/**
 *  Copyright (c) 2022, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
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
    }

    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
      return 1.0;
  }

  /**
   *  getScore for the Ranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {

    QryIop q_0 = (QryIop) this.args.get (0);
    return q_0.docIteratorGetMatchPosting().tf;
  }

  /**
   *  getScore for the BM25 model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreBM25 (RetrievalModel r) throws IOException {

    double k_1 = ((RetrievalModelBM25)r).getK_1();
    QryIop q = this.getArg(0);
    double tf = (double)q.docIteratorGetMatchPosting().tf;
    double b = ((RetrievalModelBM25)r).getB();
    double doclen = (double)Idx.getFieldLength(q.getField(), q.docIteratorGetMatch());
    double avg_doclen = (double)Idx.getSumOfFieldLengths(q.getField()) / (double)Idx.getDocCount(q.getField());
    // calculate tf weight
    double tfWeight = tf / (tf + k_1 * ((1 - b) + b * doclen / avg_doclen));

    // calculate RSJ weight (idf)
    long N = Idx.getNumDocs();
    int df = q.getDf();
    double idf = Math.max(0.0, Math.log(((double)N - (double)df + 0.5) / ((double)df + 0.5)));

    return idf * tfWeight;
  }

  /**
   *  getScore for the Indri model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreIndri(RetrievalModel r) throws IOException {
    double score = 0.0;
    if (this.docIteratorHasMatch(r)) {
      double mu = ((RetrievalModelIndri)r).getMu();
      double lambda = ((RetrievalModelIndri)r).getLambda();
      QryIop q = (this.getArg(0));
      double tf = (double)q.docIteratorGetMatchPosting().tf;
      double doclen = (double)Idx.getFieldLength(q.getField(), q.docIteratorGetMatch());

      // get MLE
      double ctf_qi = q.invertedList.ctf;
      double lengthC = (double)Idx.getSumOfFieldLengths(q.getField());
      double MLE = ctf_qi / lengthC;

      // use two-stage smoothing to compute term weights
      score = (1.0 - lambda) * (tf + mu * MLE) / (mu + doclen) + lambda * MLE;
    }
    return score;

  }


  /**
   * If q_i has no match for document d, we will call getDefaultScore.
   * Mostly same as usual calculation in Indri retreival model, but with tf=0.
   * @param r The retrieval model that determines how scores are calculated.
   * @param docid
   * @return The document score.
   * @throws IOException Error accessing the Lucene index
   */
  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {
    double mu = ((RetrievalModelIndri)r).getMu();
    double lambda = ((RetrievalModelIndri)r).getLambda();
    QryIop q = (this.getArg(0));
    double doclen = (double)Idx.getFieldLength(q.getField(), (int)docid);

    // get MLE
    double ctf_qi = q.invertedList.ctf;
    double lengthC = (double)Idx.getSumOfFieldLengths(q.getField());
    double MLE = ctf_qi / lengthC;

    // use two-stage smoothing to compute term weights
    double score = (1.0 - lambda) * (mu * MLE) / (mu + doclen) + lambda * MLE;
    return score;

  }


  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);

    /*
     *  STUDENTS:: In HW2 during query initialization you may find it
     *  useful to have this SCORE node precompute and cache some
     *  values that it will use repeatedly when calculating document
     *  scores.  It won't change your results, but it will improve the
     *  speed of your software.
     */
  }



}
