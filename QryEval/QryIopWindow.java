import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *  The WINDOW operator for all retrieval models.
 */
public class QryIopWindow extends QryIop {

        public int distance;

        public QryIopWindow(int d) {
                distance = d;
        }


        /**
         *  Evaluate the query operator; the result is an internal inverted
         *  list that may be accessed via the internal iterators.
         *  @throws IOException Error accessing the Lucene index.
         */
        @Override
        protected void evaluate() throws IOException {
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
                        boolean endLoop = false;
                        while (!endLoop) {
                                int index = 0;
                                int min = Integer.MAX_VALUE;
                                int max = -1;

                                for (int i = 0; i < this.args.size(); i++) {
                                        QryIop qi_i = (QryIop) this.args.get(i);
                                        if (!qi_i.locIteratorHasMatch()) {
                                                endLoop = true;
                                                break;
                                        }
                                        int temp = this.getArg(i).locIteratorGetMatch();
                                        // get minimum location
                                        if (min > temp) {
                                                min = temp;
                                                // record the index of minimum location for next round min location searching
                                                index = i;
                                        }
                                        // get maximum location
                                        if (max < temp) {
                                                max = temp;
                                        }
                                }
                                if (endLoop) {
                                        break;
                                }

                                if (inDistance(min, max)) {
                                        // If in distance, add max location to position list
                                        positions.add(max);
                                        // Increment all loc iterators
                                        for (int i = 0; i < this.args.size(); i++) {
                                                this.getArg(i).locIteratorAdvance();
                                        }
                                } else {
                                        // No match, increment the iterator for the min location
                                        ((QryIop)this.args.get(index)).locIteratorAdvance();
                                }

                        }

                        if (positions.size() > 0) {
                                this.invertedList.appendPosting(docid_0, positions);
                        }
                        // advance document pointer to the next one
//                        qi_0.docIteratorAdvancePast(docid_0);
                        for (Qry qi_i: this.args) {
                                (qi_i).docIteratorAdvancePast(docid_0);
                        }
                }

        }

        /**
         * Helper function to compare minimum position and maximum position.
         * If less than distance, means match our requirement
         * @param minPos current position
         * @param maxPos previous position
         * @return true if not match, false if match
         */
        private boolean inDistance(int minPos, int maxPos) {
                return (Math.abs(minPos - maxPos) < this.distance);
        }

}
