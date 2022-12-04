import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Vector;

/**
 *  The NEAR operator for all retrieval models.
 */
public class QryIopNear extends QryIop {

        public int distance;

        public QryIopNear(int d) {
                distance = d;
        }

        /**
         *  Evaluate the query operator; the result is an internal inverted
         *  list that may be accessed via the internal iterators.
         *  @throws IOException Error accessing the Lucene index.
         */
        protected void evaluate () throws IOException {

                //  Create an empty inverted list.  If there are no query arguments,
                //  this is the final result.

                this.invertedList = new InvList (this.getField());

                if (args.size () == 0) {
                        return;
                }

                //  Each pass of the loop adds 1 document to result inverted list
                //  until all of the argument inverted lists are depleted.

                // iterate document
                while(this.docIteratorHasMatchAll(null)) {
                        List<Integer> positions = new ArrayList<Integer>();

                        int docid_0 = this.args.get(0).docIteratorGetMatch();
                        // the first query document ID
                        QryIop qi_0 = this.getArg(0);

                        // iterate location
                        while(qi_0.locIteratorHasMatch()) {
                                // variable to record the previous pointer
                                int prevPos = qi_0.locIteratorGetMatch();
                                boolean found = true;

                                found = ifFoundMatchPair(prevPos, found);

                                if (found) {
                                        positions.add(prevPos);
                                }
                                // advance q_0
                                qi_0.locIteratorAdvance();
                        }
                        if (positions.size() > 0) {
                                this.invertedList.appendPosting(docid_0, positions);
                        }
                        // advance the pointer to the next
                        qi_0.docIteratorAdvancePast(docid_0);
                }
        }

        /**
         * helper function to compare current position and previous position
         * @param currPos current position
         * @param prevPos previous position
         * @return true if not match, false if match
         */
        private boolean biggerThanDistance(int currPos, int prevPos) {
                return (currPos > this.distance + prevPos);
        }


        /**
         * helper function to check if each pair match
         * @param prevPos previous position
         * @param found boolean value indicating match or not
         * @return false if not match
         */
        private boolean ifFoundMatchPair(int prevPos, boolean found) {
                for (int i = 1; i < this.args.size(); i++) {
                        QryIop qi_i = this.getArg(i);
                        qi_i.locIteratorAdvancePast(prevPos);
                        // check whether all pairs match
                        if (!qi_i.locIteratorHasMatch()) {
                                found = false;
                                break;
                        } else if (qi_i.locIteratorHasMatch()) {
                                int currPos = qi_i.locIteratorGetMatch();
                                if (biggerThanDistance(currPos, prevPos)) {
                                        found = false;
                                        break;
                                }
                        }
                        prevPos = qi_i.locIteratorGetMatch();
                        qi_i.locIteratorAdvance();
                }
                return found;
        }
}

