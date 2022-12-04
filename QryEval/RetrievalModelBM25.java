/**
 *  An object that stores parameters for the BM25
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */

public class RetrievalModelBM25  extends RetrievalModel {

        // parameters in Model BM25
        private double k_1;
        private double b;
        private double k_3;

        /**
         * Constructor.
         * @param k_1 free parameter
         * @param b free parameter
         * @param k_3 free parameter
         */
        public RetrievalModelBM25(double k_1, double b, double k_3) {
                this.k_1 = k_1;
                this.b = b;
                this.k_3 = k_3;
        }

        /**
         * Getter for k_1.
         * @return k_1
         */
        public double getK_1() {
                return k_1;
        }

        /**
         * Getter for b.
         * @return b
         */
        public double getB() {
                return b;
        }

        /**
         * Getter for k_3.
         * @return k_3
         */
        public double getK_3() {
                return k_3;
        }

        /**
         * The name of the default query operator for the retrieval model.
         * @return SUM query operator is the default query operator
         */
        @Override
        public String defaultQrySopName() {
                return new String ("#sum");
        }
}
